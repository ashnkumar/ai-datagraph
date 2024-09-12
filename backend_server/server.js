const express = require('express');
const bodyParser = require('body-parser');
const cors = require('cors');
const axios = require('axios');
const { Pool } = require('pg');
const { dag4 } = require('@stardust-collective/dag4');
const crypto = require('crypto');

// DAG4 setup for interacting with the blockchain
dag4.account.connect({
  networkVersion: '2.0',
  testnet: true,
});
const fromPrivateKey = "MAIN_METAGRAPH_PRIVATE_KEY"; 
const fromAddress = "MAIN_METAGRAPH_ADDRESS";     

// Setup PostgreSQL connection
const pool = new Pool({
  user: 'metagraph_user',
  host: 'localhost',
  database: 'metagraph_private_data',
  password: 'your_password',
  port: 5432,
});

const server = express();
server.use(bodyParser.json());
server.use(cors());

// Helper to hash data
const hashData = (data) => {
  return crypto.createHash('sha256').update(JSON.stringify(data)).digest('hex');
};

/**
 * Helper: Submit transaction on the blockchain
 */
async function submitTransaction(fromPrivateKey, toAddress, amount) {
  dag4.account.loginPrivateKey(fromPrivateKey);
  const lastRef = await dag4.network.getAddressLastAcceptedTransactionRef(dag4.account.address);
  const signedTxn = await dag4.account.generateSignedTransaction(toAddress, amount, 0, lastRef);
  return await dag4.network.postTransaction(signedTxn);
}

/**
 * Helper: Poll transaction status on the blockchain
 */
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

/**
 * POST /create-data-provider
 * Creates a new data provider and stores it in the Postgres database
 */
server.post('/create-data-provider', async (req, res) => {
  const { age, gender, ethnicity, height, weight, sharingPreferences } = req.body;

  try {
    const walletAddress = dag4.account.generateNewAddress(); // Generates a new address
    const privateKey = dag4.keyStore.generatePrivateKey();

    const queryText = `INSERT INTO data_providers 
      (wallet_address, private_key, age, gender, ethnicity, height, weight, sharing_preferences) 
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8) RETURNING wallet_address`;
    const values = [walletAddress, privateKey, age, gender, ethnicity, height, weight, sharingPreferences];

    const result = await pool.query(queryText, values);
    return res.status(201).json({ message: 'Data provider created', wallet_address: result.rows[0].wallet_address });
  } catch (err) {
    console.error('Error creating provider:', err);
    return res.status(500).json({ error: 'Failed to create provider' });
  }
});

/**
 * POST /create-ai-engineer
 * Creates an AI engineer with a wallet and stores it in the Postgres database
 */
server.post('/create-ai-engineer', async (req, res) => {
  const { name } = req.body;

  try {
    const walletAddress = dag4.account.generateNewAddress(); // Generate new address
    const privateKey = dag4.keyStore.generatePrivateKey();

    const queryText = `INSERT INTO ai_engineers (wallet_address, private_key, name) 
      VALUES ($1, $2, $3) RETURNING wallet_address`;
    const values = [walletAddress, privateKey, name];

    const result = await pool.query(queryText, values);
    return res.status(201).json({ message: 'AI Engineer created', wallet_address: result.rows[0].wallet_address });
  } catch (err) {
    console.error('Error creating AI engineer:', err);
    return res.status(500).json({ error: 'Failed to create AI engineer' });
  }
});

/**
 * POST /data
 * Sends private health data to blockchain and stores hash in Postgres
 */
server.post('/data', async (req, res) => {
  const { wallet_address, private_data } = req.body;
  const hash = hashData(private_data);

  try {
    // Store the data on the blockchain
    await axios.post('http://localhost:9400/data', {
      wallet_address,
      hash,
    });

    // Store the private data in Postgres
    const queryText = `INSERT INTO data_updates (wallet_address, private_data, hash) 
      VALUES ($1, $2, $3) RETURNING id`;
    const values = [wallet_address, private_data, hash];
    await pool.query(queryText, values);
    
    return res.status(201).json({ message: 'Data update stored' });
  } catch (err) {
    console.error('Error storing data:', err);
    return res.status(500).json({ error: 'Failed to store data' });
  }
});

/**
 * GET /data
 * Query blockchain for matching data hashes based on requested fields
 */
server.get('/data', async (req, res) => {
  const { fields } = req.query;
  const fieldList = fields.split(',');

  try {
    // Query the blockchain for data hashes that match the filter
    const blockchainResponse = await axios.get('http://localhost:9400/data', { params: { fields } });
    const hashes = blockchainResponse.data;

    // Query Postgres for matching data based on hashes
    const queryText = `SELECT * FROM data_updates WHERE hash = ANY($1::text[])`;
    const result = await pool.query(queryText, [hashes]);

    return res.status(200).json(result.rows);
  } catch (err) {
    console.error('Error querying data:', err);
    return res.status(500).json({ error: 'Failed to query data' });
  }
});

/**
 * POST /query-data
 * Handles the query process for AI researchers and returns matching data
 */
server.post('/query-data', async (req, res) => {
  const { fromPrivateKey, toAddress, amount, fields } = req.body;

  try {
    // Step 1: Submit transaction on the blockchain
    const txnHash = await submitTransaction(fromPrivateKey, toAddress, amount);
    
    // Step 2: Poll for transaction confirmation
    const confirmed = await pollTransactionStatus(txnHash);
    if (!confirmed) return res.status(500).json({ error: 'Transaction not confirmed' });

    // Step 3: Query blockchain for matching data
    const response = await axios.get('http://localhost:9400/data', { params: { fields } });
    const hashes = response.data;

    // Step 4: Retrieve matching data from Postgres
    const queryText = `SELECT * FROM data_updates WHERE hash = ANY($1::text[])`;
    const dataUpdates = await pool.query(queryText, [hashes]);

    // Step 5: Log the query for rewards distribution
    const logQueryText = `INSERT INTO queries (txn_hash, payment_amount, data_updates) 
      VALUES ($1, $2, $3) RETURNING id`;
    await pool.query(logQueryText, [txnHash, amount, dataUpdates.rows]);

    return res.status(200).json(dataUpdates.rows);
  } catch (err) {
    console.error('Error querying data:', err);
    return res.status(500).json({ error: 'Failed to process query' });
  }
});

/**
 * POST /distribute-rewards
 * Consolidates rewards and sends direct transactions from AI engineers to data providers
 */
server.post('/distribute-rewards', async (req, res) => {
  try {
    // Get all unprocessed queries and their associated data updates
    const result = await pool.query(`
      SELECT q.id as query_id, q.payment_amount, q.ai_engineer_id, d.wallet_address as provider_wallet, 
             d.id as data_update_id, d.usage_since_last_reward, d.ai_engineer_id 
      FROM queries q 
      JOIN data_updates d ON q.data_updates @> json_build_array(d.id)
      WHERE q.processed = false
    `);

    const queries = result.rows;

    if (queries.length === 0) {
      return res.status(200).json({ message: 'No queries to process.' });
    }

    let totalRewards = 0;
    const transactions = {};

    // Consolidate rewards owed to data providers
    for (const query of queries) {
      const { ai_engineer_id, provider_wallet, payment_amount, data_update_id } = query;

      // Calculate reward for each data point (even distribution across all updates)
      const rewardPerDataPoint = payment_amount / queries.length;

      // If this provider has already been assigned a transaction, add to it
      if (!transactions[ai_engineer_id]) {
        transactions[ai_engineer_id] = {};
      }

      if (!transactions[ai_engineer_id][provider_wallet]) {
        transactions[ai_engineer_id][provider_wallet] = 0;
      }

      transactions[ai_engineer_id][provider_wallet] += rewardPerDataPoint;
      totalRewards += rewardPerDataPoint;

      // Reset usage_since_last_reward for the data update
      await pool.query(`UPDATE data_updates SET usage_since_last_reward = 0 WHERE id = $1`, [data_update_id]);
    }

    // Send consolidated transactions from AI engineers to data providers
    for (const [aiEngineerId, providerMap] of Object.entries(transactions)) {
      // Get AI engineer's wallet info
      const engineerResult = await pool.query(`SELECT private_key FROM ai_engineers WHERE id = $1`, [aiEngineerId]);
      const aiPrivateKey = engineerResult.rows[0].private_key;

      for (const [providerWallet, amount] of Object.entries(providerMap)) {
        // Submit the transaction from the AI engineer's wallet to the provider's wallet
        await submitTransaction(aiPrivateKey, providerWallet, amount);
      }
    }

    // Mark all queries as processed
    const queryIds = queries.map(query => query.query_id);
    await pool.query(`UPDATE queries SET processed = true WHERE id = ANY($1::int[])`, [queryIds]);

    return res.status(200).json({ message: `Total rewards distributed: ${totalRewards.toFixed(2)} DAG` });
  } catch (err) {
    console.error('Error distributing rewards:', err);
    return res.status(500).json({ error: 'Failed to distribute rewards' });
  }
});

/**
 * GET /view-providers
 * Returns all providers for the frontend
 */
server.get('/view-providers', async (req, res) => {
  try {
    const result = await pool.query(`SELECT * FROM data_providers`);
    return res.status(200).json(result.rows);
  } catch (err) {
    console.error('Error fetching providers:', err);
    return res.status(500).json({ error: 'Failed to fetch providers' });
  }
});

/**
 * GET /view-updates
 * Returns all data updates for the frontend
 */
server.get('/view-updates', async (req, res) => {
  try {
    const result = await pool.query(`SELECT * FROM data_updates`);
    return res.status(200).json(result.rows);
  } catch (err) {
    console.error('Error fetching updates:', err);
    return res.status(500).json({ error: 'Failed to fetch updates' });
  }
});

/**
 * GET /view-ai-engineers
 * Returns all AI engineers for the frontend
 */
server.get('/view-ai-engineers', async (req, res) => {
  try {
    const result = await pool.query(`SELECT * FROM ai_engineers`);
    return res.status(200).json(result.rows);
  } catch (err) {
    console.error('Error fetching AI engineers:', err);
    return res.status(500).json({ error: 'Failed to fetch AI engineers' });
  }
});

// Start the server
const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
  console.log(`Server running on port ${PORT}`);
});
 