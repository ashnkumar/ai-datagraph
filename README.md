AI DataGraph lets data providers—whether they’re governments, enterprises, or individuals—share their data securely for AI model training and get paid for it. It leverages the Constellation Metagraph to allow providers to create profiles and send privacy-gated updates, to enable AI researchers to query this data by sending a token payment, and for the data providers to be rewarded for each time their data was used as part of an AI model's query.
![alt](https://i.imgur.com/GxstAIb.png)

## Prerequisites

Ensure you have the following installed:

- [Node.js](https://nodejs.org/) (v14+)
- [Docker](https://www.docker.com/)
- [Euclid Development Environment](https://docs.constellationnetwork.io/sdk/guides/quick-start)

---

## Project Structure

This repository contains the following components:

- **ai_datagraph**: The Metagraph portion of the project.
- **backend_server**: The backend server (runs on port 3000).
- **frontend**: The React frontend (runs on port 3001).
- **Dockerfile**: Custom Docker configuration for the Metagraph.

---

## Installation Instructions

### Step 1: Clone the repository

First, clone this repository to your local machine:

```bash
git clone https://github.com/ashnkumar/ai-datagraph
cd ai-datagraph
```

### Step 2: Install backend dependencies

Navigate to the backend server folder and install the necessary dependencies:

```bash
cd backend_server
npm install
```

Start the backend server:

```bash
npm start
```

The backend server will be available on `http://localhost:3000`.

### Step 3: Install frontend dependencies

Navigate to the frontend folder and install the necessary dependencies:

```bash
cd ../frontend
npm install
```

Start the frontend:

```bash
npm start
```

The frontend will be available on `http://localhost:3001`.

---

## Metagraph Setup

Follow these steps to set up the Metagraph component.

### Step 1: Set up the Euclid Development Environment

Go through the Euclid Development Environment [quickstart guide](https://docs.constellationnetwork.io/sdk/guides/quick-start) to get it on your local machine.

### Step 2: Copy the Metagraph Project

Once the Euclid development environment is set up, copy the `ai_datagraph` folder from this repository into the `/euclid-development-environment/source/project` folder:

```bash
cp -r path_to_cloned_repo/ai_datagraph /path_to_euclid_development_environment/source/project
```

### Step 3: Update the Euclid Configuration

Update the `euclid.json` file in the root of the Euclid development environment to replace the `project_name` field with `"ai_datagraph"`.

### Step 4: Copy Dockerfile

Copy the `Dockerfile` from the root of this repository into the `/euclid-development-environment/infra/docker/custom/metagraph-base-image` directory:

```bash
cp path_to_cloned_repo/Dockerfile /path_to_euclid_development_environment/infra/docker/custom/metagraph-base-image
```

### Step 5: Build and Start the Metagraph

In the Euclid development environment, build the Metagraph:

```bash
cd /path_to_euclid_development_environment
scripts/hydra build
```

Start the Metagraph:

```bash
scripts/hydra start-genesis
```

---

### Summary

- **Backend** runs on `http://localhost:3000`
- **Frontend** runs on `http://localhost:3001`
- **Metagraph** is handled via the Euclid development environment.
---
### Problem(s) we're solving:
As a machine learning engineer, I know how powerful AI can be in helping us solve our most complex problems, but its effectiveness depends **entirely** on getting access to high-quality data. For really important use cases like healthcare and disease prevention, these models need to be trained on nuanced data such as diet, age, gender, daily activity, and other health metrics. 

**Billions of these useful data points are generated each day** from our phones and smart watches, but they’re spread across millions of people and are highly sensitive, so it’s no surprise that most people aren’t willing to share this data.

From talking to potential users in preparation for this hackathon, it’s clear they would be willing to share this data if it's (a) super simple to set up, (b) if they have fine-grained control over what they share, and (c) if they get compensated for it.

### Overview of our solution: AI DataGraph
AI DataGraph works through the use of 3 distinct "phases" as described below. More information is in the "How it works" section:
* **Phase 1 (Data Ingestion):** Data providers (in our example, these are consumers) are able to sign up through the web app and select different types of information they are willing to share. This is a combination of demographic information (not updated often), daily updates like sleep and average heart rate, and hourly updates like steps and calories consumed in the past hour. Once they've set this up, their relevant updates are sent each hour (or each day) from their device automatically into the metagraph with minimal involvement on their part. 
* **Phase 2 (AI models request data access for training):** Whenever an AI engineer wants to request access to this data for training their models, they send a payment (token) to the metagraph, and once this transaction is confirmed, they receive the data. During this process, our logic records which data points were returned with which query (or multiple queries) for reward distribution in Phase 3.
* **Phase 3 (Rewards distribution):** Periodically, our app runs a process to distribute rewards. It uses the tracking from Phase 2 to calculate how much to pay out each data provider based on the number of times their data points were used in the queries since the last rewards cycle. This logic handles cases where each data point can be returned as part of more than one query.

### Why Metagraphs?
The Constellation Metagraph is an ideal framework for AI DataGraph because of its unique ability to handle **privacy**, **scalability**, and **decentralization**—all essential to our solution.

1. **Privacy-first infrastructure:** Metagraphs allow us to define custom privacy rules, enabling data providers to securely share sensitive information without worrying about their data being misused. By using **custom consensus mechanisms**, we ensure that private data is validated in a way that preserves confidentiality before it's processed on-chain.

2. **Scalability and throughput:** AI model training requires enormous amounts of data, and traditional blockchains are too slow or too expensive to handle this efficiently. The **Hypergraph's extreme scalability** allows us to ingest, validate, and share millions of data points across many users with high throughput and low fees. This is critical to making our solution practical and attractive to both data providers and AI researchers.

3. **Decentralization and trust:** Unlike centralized solutions, Metagraphs provide a **decentralized network** that eliminates the need for a trusted intermediary, reducing the risks of centralized control over sensitive data. This not only ensures data security but also encourages more people to participate as data providers because they can trust that their data remains in their control.

Ultimately, Constellation's Metagraph framework allows us to build an infrastructure where **data sovereignty**, **privacy**, and **efficiency** are at the forefront. It’s the backbone that makes AI DataGraph a scalable and secure solution for data sharing.

### How it works (more detail)

![alt](https://i.imgur.com/daB4o85.png)
_Data providers (including consumers) have full control over what data they are willing to share and how often they're willing to share this data._

| Component                | How it works  |
|------------------------|---------------|
| **Creating a data provider profile**       | Data providers (in our example, these are consumers) are able to sign up through the web app and select different types of information they are willing to share. This is a combination of demographic information (not updated often), daily updates like sleep and average heart rate, and hourly updates like steps and calories consumed in the past hour. Once they've set this up, their relevant updates are sent each hour (or each day) from their device automatically into the metagraph with minimal involvement on their part. This information, along with their wallet address, is saved in an external database (we're using Firestore in our project).           |
| **Data updates are sent to metagraph**       | Data is automatically sent to the metagraph as an update based on its frequency (e.g., if it's a daily update like `sleep_hours`, one update is sent each day per device that is sharing that. For hourly updates like `calories_consumed`, that is sent once per hour per device that is sharing that). These updates go through the lifecycle methods on the metagraph (notably the L1 `validateUpdate`, which checks for data standardization, and L0 `validateData` that checks for stateful information like ensuring each update is after its most previous timestamp for that same update type and data provider).         |
| **Metagraph hashes and stores information on and off-chain**       | During the `combine` lifecycle function, the metagraph logic hashes the private data and sends this hash and the data itself to an external database (we're using Firestore in our example). On-chain, it only stores the hash itself instead of the private data. It also stores an array of the fields being shared in that update to make it easier to query for the relevant data updates downstream.       |
| **AI engineers request access to data for model training**       | An AI engineer sends a JSON request for data types they want returned (e.g., "sleep_hours", "gender"). They also send a token amount to pay for this dataset. Our server sends the token transaction to the metagraph, gets back a txn hash, polls the latest snapshots until it sees that the txn has been confirmed. Then it queries the metagraph endpoint for all the **hashes** requested, which it then turns around and queries our external database to get the underlying private data before providing it back to the requester. During this process, it also records that each of these data points have been used in a specific query, which it uses downstream to calculate rewards.       |
| **Rewards are distributed periodically**       | Every 10 minutes, in our application, we run the rewards distribution cycle. The server handles most of this now (though we plan on moving more of this on-chain in the future). It queries the external database to find all data points that are due for a payout, then uses the payment information for each individual query to determine what share of that bounty the data provider is due a payout for. Finally, it pays all this out by sending bulk transactions to the metagraph. |