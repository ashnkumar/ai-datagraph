const express = require('express');
const bodyParser = require('body-parser');
const cors = require('cors');
const axios = require('axios');
const { Pool } = require('pg');
const { dag4 } = require('@stardust-collective/dag4');
const crypto = require('crypto');

// DAG4 setup for blockchain interaction
dag4.account.connect({
  networkVersion: '2.0',
  testnet: true,
});

const pool = new Pool({
  user: 'ai_datagraph',
  host: 'localhost',
  database: 'ai_datagraph',
  password: 'ai_datagraph',
  port: 5432,
});

const server = express();
server.use(bodyParser.json());
server.use(cors());

const hashData = (data) => {
  return crypto.createHash('sha256').update(JSON.stringify(data)).digest('hex');
};

async function submitTransaction(fromPrivateKey, toAddress, amount) {
  dag4.account.loginPrivateKey(fromPrivateKey);
  const lastRef = await dag4.network.getAddressLastAcceptedTransactionRef(dag4.account.address);
  const signedTxn = await dag4.account.generateSignedTransaction(toAddress, amount, 0, lastRef);
  return await dag4.network.postTransaction(signedTxn);
}

async function pollTransactionStatus(txnHash) {
  return new Promise((resolve, reject) => {
    const intervalId = setInterval(async () => {
      try {
        const txn = await dag4.network.getTransaction(txnHash);
        if (txn) {
          clearInterval(intervalId);
          resolve(true);
        }
      } catch (error) {
        clearInterval(intervalId);
        reject(new Error('Transaction polling error'));
      }
    }, 5000);
  });
}

server.post('/create-data-provider', async (req, res) => {
  const { age, gender, ethnicity, height, weight, sharingPreferences } = req.body;

  try {
    const walletAddress = dag4.account.generateNewAddress();
    const privateKey = dag4.keyStore.generatePrivateKey();

    const queryText = `INSERT INTO data_providers 
      (wallet_address, private_key, age, gender, ethnicity, height, weight, sharing_preferences) 
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8) RETURNING wallet_address`;
    const values = [walletAddress, privateKey, age, gender, ethnicity, height, weight, sharingPreferences];

    const result = await pool.query(queryText, values);
    return res.status(201).json({ message: 'Data provider created', wallet_address: result.rows[0].wallet_address });
  } catch (err) {
    return res.status(500).json({ error: 'Failed to create provider' });
  }
});

server.post('/create-ai-engineer', async (req, res) => {
  const { name } = req.body;

  try {
    const walletAddress = dag4.account.generateNewAddress();
    const privateKey = dag4.keyStore.generatePrivateKey();

    const queryText = `INSERT INTO ai_engineers (wallet_address, private_key, name) 
      VALUES ($1, $2, $3) RETURNING wallet_address`;
    const values = [walletAddress, privateKey, name];

    const result = await pool.query(queryText, values);
    return res.status(201).json({ message: 'AI Engineer created', wallet_address: result.rows[0].wallet_address });
  } catch (err) {
    return res.status(500).json({ error: 'Failed to create AI engineer' });
  }
});

server.post('/data', async (req, res) => {
  const { wallet_address, private_data } = req.body;
  const hash = hashData(private_data);

  try {
    await axios.post('http://localhost:9400/data', { wallet_address, hash });
    const queryText = `INSERT INTO data_updates (wallet_address, private_data, hash) 
      VALUES ($1, $2, $3) RETURNING id`;
    const values = [wallet_address, private_data, hash];
    await pool.query(queryText, values);

    return res.status(201).json({ message: 'Data update stored' });
  } catch (err) {
    return res.status(500).json({ error: 'Failed to store data' });
  }
});

server.get('/data', async (req, res) => {
  const { fields } = req.query;
  const fieldList = fields.split(',');

  try {
    const blockchainResponse = await axios.get('http://localhost:9400/data', { params: { fields } });
    const hashes = blockchainResponse.data;

    const queryText = `SELECT * FROM data_updates WHERE hash = ANY($1::text[])`;
    const result = await pool.query(queryText, [hashes]);

    return res.status(200).json(result.rows);
  } catch (err) {
    return res.status(500).json({ error: 'Failed to query data' });
  }
});

server.post('/query-data', async (req, res) => {
  const { fromPrivateKey, toAddress, amount, fields } = req.body;

  try {
    const txnHash = await submitTransaction(fromPrivateKey, toAddress, amount);
    const confirmed = await pollTransactionStatus(txnHash);
    if (!confirmed) return res.status(500).json({ error: 'Transaction not confirmed' });

    const response = await axios.get('http://localhost:9400/data', { params: { fields } });
    const hashes = response.data;

    const queryText = `SELECT * FROM data_updates WHERE hash = ANY($1::text[])`;
    const dataUpdates = await pool.query(queryText, [hashes]);

    const logQueryText = `INSERT INTO queries (txn_hash, payment_amount, data_updates) 
      VALUES ($1, $2, $3) RETURNING id`;
    await pool.query(logQueryText, [txnHash, amount, dataUpdates.rows]);

    return res.status(200).json(dataUpdates.rows);
  } catch (err) {
    return res.status(500).json({ error: 'Failed to process query' });
  }
});

server.post('/distribute-rewards', async (req, res) => {
  try {
    const result = await pool.query(`
      SELECT q.id as query_id, q.payment_amount, q.ai_engineer_id, d.wallet_address as provider_wallet, 
             d.id as data_update_id, d.usage_since_last_reward, d.ai_engineer_id 
      FROM queries q 
      JOIN data_updates d ON q.data_updates @> json_build_array(d.id)
      WHERE q.processed = false
    `);

    const queries = result.rows;
    if (queries.length === 0) return res.status(200).json({ message: 'No queries to process.' });

    let totalRewards = 0;
    const transactions = {};

    for (const query of queries) {
      const { ai_engineer_id, provider_wallet, payment_amount, data_update_id } = query;
      const rewardPerDataPoint = payment_amount / queries.length;

      if (!transactions[ai_engineer_id]) transactions[ai_engineer_id] = {};
      if (!transactions[ai_engineer_id][provider_wallet]) transactions[ai_engineer_id][provider_wallet] = 0;

      transactions[ai_engineer_id][provider_wallet] += rewardPerDataPoint;
      totalRewards += rewardPerDataPoint;

      await pool.query(`UPDATE data_updates SET usage_since_last_reward = 0 WHERE id = $1`, [data_update_id]);
    }

    for (const [aiEngineerId, providerMap] of Object.entries(transactions)) {
      const engineerResult = await pool.query(`SELECT private_key FROM ai_engineers WHERE id = $1`, [aiEngineerId]);
      const aiPrivateKey = engineerResult.rows[0].private_key;

      for (const [providerWallet, amount] of Object.entries(providerMap)) {
        await submitTransaction(aiPrivateKey, providerWallet, amount);
      }
    }

    const queryIds = queries.map(query => query.query_id);
    await pool.query(`UPDATE queries SET processed = true WHERE id = ANY($1::int[])`, [queryIds]);

    return res.status(200).json({ message: `Total rewards distributed: ${totalRewards.toFixed(2)} DAG` });
  } catch (err) {
    return res.status(500).json({ error: 'Failed to distribute rewards' });
  }
});

server.get('/view-providers', async (req, res) => {
  try {
    const result = await pool.query(`SELECT * FROM data_providers`);
    return res.status(200).json(result.rows);
  } catch (err) {
    return res.status(500).json({ error: 'Failed to fetch providers' });
  }
});

server.get('/view-updates', async (req, res) => {
  try {
    const result = await pool.query(`SELECT * FROM data_updates`);
    return res.status(200).json(result.rows);
  } catch (err) {
    return res.status(500).json({ error: 'Failed to fetch updates' });
  }
});

server.get('/view-ai-engineers', async (req, res) => {
  try {
    const result = await pool.query(`SELECT * FROM ai_engineers`);
    return res.status(200).json(result.rows);
  } catch (err) {
    return res.status(500).json({ error: 'Failed to fetch AI engineers' });
  }
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
  console.log(`Server running on port ${PORT}`);
});
