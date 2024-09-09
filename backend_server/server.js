const express = require('express');
const bodyParser = require('body-parser');
const { initializeApp } = require('firebase/app');
const { dag4 } = require('@stardust-collective/dag4');
const { v4: uuidv4 } = require('uuid'); // For generating unique query IDs
const cors = require('cors');
const { getFirestore, doc, setDoc, collection, getDoc, getDocs, writeBatch, updateDoc } = require('firebase/firestore');

const fromPrivateKey = "MAIN_METAGRAPH_PRIVATE_KEY"; 
const fromAddress = "MAIN_METAGRAPH_ADDRESS";     

// Initialize Firebase
const firebaseConfig = {
  apiKey: "",
  authDomain: "",
  projectId: "",
  storageBucket: "",
  messagingSenderId: "",
  appId: ""
};
const app = initializeApp(firebaseConfig);
const firestore = getFirestore(app);

// Setup DAG4 to connect to the correct network
dag4.account.connect({
  networkVersion: '2.0',
  testnet: true
});

// Initialize Express app
const server = express();
server.use(bodyParser.json());
server.use(cors());

// Helper function to generate a wallet using dag4
const generateWallet = () => {
  const privateKey = dag4.keyStore.generatePrivateKey();
  const account = dag4.createAccount();
  account.loginPrivateKey(privateKey);
  return { address: account.address, privateKey };
};

/**
 * Route: Create Data Provider
 * This will create a new data provider in Firestore based on the form input.
 */
server.post('/create-data-provider', async (req, res) => {
  const {
    age,
    gender,
    ethnicity,
    height,
    weight,
    sharingPreferences,
  } = req.body;

  console.log('Request body:', req.body);

  try {
    // Generate a wallet for the provider
    const { address: walletAddress, privateKey } = generateWallet(); // Generate wallet address and private key

    // Reference to data providers collection
    const providerRef = doc(firestore, 'data_providers', walletAddress);

    // Save provider demographic information along with sharing preferences
    const providerData = {
      wallet_address: walletAddress,
      private_key: privateKey, // Save private key (make sure this is securely stored in production)
      age: age || null,
      gender: gender || null,
      ethnicity: ethnicity || null,
      height: height || null,
      weight: weight || null,
      sharing_preferences: sharingPreferences,
      created_at: new Date(),
    };

    // Save the provider information and initialize their wallet balance to 0
    const batch = writeBatch(firestore);
    batch.set(providerRef, providerData);

    // Commit the batch operation
    await batch.commit();

    return res.status(201).json({
      message: 'Data provider created successfully',
      wallet_address: walletAddress,
      private_key: privateKey
    });
  } catch (error) {
    console.error('Error creating data provider:', error);
    return res.status(500).json({ error: 'Failed to create data provider' });
  }
});

server.get('/view-providers', async (req, res) => {
  try {
    // Fetch all data providers
    const providersSnapshot = await getDocs(collection(firestore, 'data_providers'));

    const providers = [];

    // Iterate through each provider document
    providersSnapshot.forEach((doc) => {
      const provider = doc.data();

      providers.push({
        ...provider
      });
    });

    res.status(200).json(providers);
  } catch (error) {
    console.error('Error fetching providers:', error);
    res.status(500).send('Error fetching providers');
  }
});


/**
 * Function: Distribute rewards by generating DAG transactions on the Metachain.
 */
async function sendRewardTransaction(toAddress, amount) {
  try {
    dag4.account.loginPrivateKey(fromPrivateKey); // Login using the metagraph private key

    // Get the last accepted transaction reference for the Metachain address
    const lastRef = await dag4.network.getAddressLastAcceptedTransactionRef(fromAddress);

    // Generate a signed transaction
    const signedTxn = await dag4.account.generateSignedTransaction(toAddress, amount, 0, lastRef);

    // Post the transaction to the network
    const txnHash = await dag4.network.postTransaction(signedTxn);

    console.log(`Transaction sent to ${toAddress}: ${amount} DAG (txnHash: ${txnHash})`);
    return txnHash;
  } catch (error) {
    throw new Error(`Failed to send transaction to ${toAddress}: ${error.message}`);
  }
}

server.post('/distribute-rewards', async (req, res) => {
  try {
    console.log("Starting rewards distribution on Metachain...");

    // Get all unprocessed queries
    const queriesSnapshot = await getDocs(collection(firestore, 'queries'));
    const queries = queriesSnapshot.docs
      .map(doc => ({ id: doc.id, ...doc.data() }))
      .filter(query => !query.processed); // Only process unprocessed queries

    let totalRewards = 0;

    // Initialize Firestore batch to reset data_updates after rewards
    const resetBatch = writeBatch(firestore);
    const queryBatch = writeBatch(firestore); // Batch to mark queries as processed

    for (const query of queries) {
      if (!query.data_updates || query.data_updates.length === 0) {
        console.log(`Skipping query ${query.id}: Invalid or empty data_updates field`);
        continue;
      }

      const totalDataPoints = query.data_updates.length;
      const rewardPerDataPoint = query.payment_amount / totalDataPoints; // Use `payment_amount` for calculating reward
      totalRewards += query.payment_amount;

      // Distribute rewards for each data update
      for (const dataUpdateId of query.data_updates) {
        const dataUpdateDoc = await getDoc(doc(firestore, 'data_updates', dataUpdateId));

        if (!dataUpdateDoc.exists()) {
          console.log(`Skipping data update ${dataUpdateId}: Not found`);
          continue;
        }

        const dataUpdate = dataUpdateDoc.data();
        const providerWallet = dataUpdate.wallet_address;

        // Send the reward transaction using dag4.js
        await sendRewardTransaction(providerWallet, rewardPerDataPoint);

        // Reset `usage_since_last_reward` and `queries_used_in` for this data update
        const updateRef = doc(firestore, 'data_updates', dataUpdateId);
        resetBatch.update(updateRef, {
          usage_since_last_reward: 0,
          queries_used_in: []
        });
      }

      // Mark the query as processed
      const queryRef = doc(firestore, 'queries', query.id);
      queryBatch.update(queryRef, {
        processed: true
      });
    }

    // Commit the reset batch for all data updates
    await resetBatch.commit();

    // Commit the query batch to mark queries as processed
    await queryBatch.commit();

    console.log(`Total rewards distributed: ${totalRewards.toFixed(2)} DAG`);

    // Return success response
    return res.status(200).send("Rewards distributed successfully.");
  } catch (error) {
    console.error("Error distributing rewards on Metachain:", error);
    return res.status(500).send(`Error distributing rewards: ${error.message}`);
  }
});

/**
 * Function: Submit the transaction to the Metagraph.
 * Logs into the researcher's wallet and submits the currency transaction.
 */
async function submitTransaction(fromPrivateKey, toAddress, amount) {
  try {
    dag4.account.loginPrivateKey(fromPrivateKey);
    const lastRef = await dag4.network.getAddressLastAcceptedTransactionRef(dag4.account.address);
    const signedTxn = await dag4.account.generateSignedTransaction(toAddress, amount, 0, lastRef);
    const txnHash = await dag4.network.postTransaction(signedTxn);
    return txnHash;
  } catch (error) {
    throw new Error(`Failed to submit transaction: ${error.message}`);
  }
}

/**
 * Function: Retrieve data from Firestore based on the fields the researcher requested.
 * Increments `usage_since_last_reward` for each data update.
 */
async function getFilteredDataAndUpdateUsage(fields) {
  const requestedFields = fields.split(',');
  const updatesSnapshot = await getDocs(collection(firestore, 'data_updates'));
  const filteredUpdates = updatesSnapshot.docs
    .map(doc => ({ id: doc.id, ...doc.data() }))
    .filter(update => requestedFields.every(field => update.shared_fields.includes(field)))
    .sort((a, b) => b.timestamp.toMillis() - a.timestamp.toMillis());

  // Update the usage tracking for relevant data updates
  const batch = writeBatch(firestore);
  filteredUpdates.forEach(update => {
    const updateRef = doc(firestore, 'data_updates', update.id);
    batch.update(updateRef, {
      usage_since_last_reward: update.usage_since_last_reward + 1
    });
  });
  await batch.commit();

  return filteredUpdates;
}

/**
 * Function: Poll for transaction status to check if it has been confirmed.
 * Polls the status of the transaction hash every 5 seconds until confirmed.
 */
async function pollTransactionStatus(txnHash) {
  return new Promise((resolve, reject) => {
    const intervalId = setInterval(async () => {
      try {
        const pendingTxn = await dag4.network.getPendingTransaction(txnHash);
        if (pendingTxn === null) {
          const confirmedTxn = await dag4.network.getTransaction(txnHash);
          clearInterval(intervalId);
          if (confirmedTxn) {
            resolve(true); // Transaction confirmed
          } else {
            reject(new Error('Transaction dropped or not confirmed.'));
          }
        }
      } catch (error) {
        clearInterval(intervalId);
        reject(new Error(`Error polling transaction status: ${error.message}`));
      }
    }, 5000);
  });
}

/**
 * Function: Log the query in Firestore for rewards tracking.
 */
async function logQueryInFirestore(txnHash, paymentAmount, dataUpdates) {
  const queryRef = doc(collection(firestore, 'queries'));
  const queryData = {
    txn_hash: txnHash,
    payment_amount: paymentAmount,
    timestamp: new Date(),
    data_updates: dataUpdates.map(update => update.id) // Only log the IDs of the data updates
  };
  await setDoc(queryRef, queryData);
}

/**
 * Route: Handles the entire process - submit txn, poll, retrieve data, and update usage tracking.
 * Input:
 *  - fromPrivateKey: Researcher's private key
 *  - toAddress: Address to send payment to
 *  - amount: Amount of token to send
 *  - fields: Fields to retrieve data for
 */
server.post('/query-data', async (req, res) => {
  const { fromPrivateKey, toAddress, amount, fields } = req.body;

  if (!fromPrivateKey || !toAddress || !amount || !fields) {
    return res.status(400).json({ error: 'Missing required parameters.' });
  }

  try {
    // Step 1: Submit the transaction
    const txnHash = await submitTransaction(fromPrivateKey, toAddress, amount);
    console.log(`Transaction submitted: ${txnHash}`);

    // Step 2: Poll the transaction status
    const isConfirmed = await pollTransactionStatus(txnHash);
    if (!isConfirmed) {
      return res.status(500).json({ error: 'Transaction was not confirmed.' });
    }

    // Step 3: Retrieve data after payment is confirmed and update usage counts
    const filteredData = await getFilteredDataAndUpdateUsage(fields);
    if (filteredData.length === 0) {
      return res.status(404).json({ message: 'No matching data points found' });
    }

    // Step 4: Log the query for reward distribution
    await logQueryInFirestore(txnHash, amount, filteredData);

    // Step 5: Return the filtered data
    return res.status(200).json(filteredData);

  } catch (error) {
    console.error('Error processing query-data request:', error);
    return res.status(500).json({ error: error.message });
  }
});


// Start the server
const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
  console.log(`Server running on port ${PORT}`);
});