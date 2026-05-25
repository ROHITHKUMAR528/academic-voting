const fs = require('fs');
const path = require('path');
const { ethers } = require('ethers');

// ── Load Contract Artifact ──────────────────────────────────────────────────
const artifactPath = path.resolve(__dirname, '../../academic-voting-dapp/artifacts/contracts/AcademicVoting.sol/AcademicVoting.json');
let contractAbi;
try {
  const artifact = JSON.parse(fs.readFileSync(artifactPath, 'utf8'));
  contractAbi = artifact.abi;
} catch (err) {
  console.error('Failed to load contract ABI from Hardhat build path:', err.message);
  process.exit(1);
}

// ── Environment Variables & Configuration ────────────────────────────────────
const providerUrl = process.env.WEB3J_NODE_URL || 'http://localhost:8545';
// Hardhat Account #0
const adminPrivateKey = process.env.ADMIN_PRIVATE_KEY || '0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80';
const contractAddress = process.env.CONTRACT_ADDRESS;

if (!contractAddress) {
  console.warn('⚠️ CONTRACT_ADDRESS is not configured. On-chain calls will fail until set.');
}

// ── Ethers Provider and Signers ──────────────────────────────────────────────
const provider = new ethers.JsonRpcProvider(providerUrl);
const baseWallet = new ethers.Wallet(adminPrivateKey, provider);
const adminWallet = new ethers.NonceManager(baseWallet);

console.log(`Connected to EVM node at: ${providerUrl}`);
console.log(`Admin wallet address: ${baseWallet.address}`);
if (contractAddress) {
  console.log(`AcademicVoting contract loaded at: ${contractAddress}`);
}

// Get contract instance connected as admin
function getAdminContract() {
  if (!contractAddress) {
    throw new Error('CONTRACT_ADDRESS is not set in environment variables');
  }
  return new ethers.Contract(contractAddress, contractAbi, adminWallet);
}

// Get contract instance connected as a voter (via custom private key)
function getVoterContract(voterPrivateKey) {
  if (!contractAddress) {
    throw new Error('CONTRACT_ADDRESS is not set in environment variables');
  }
  const voterWallet = new ethers.Wallet(voterPrivateKey, provider);
  return new ethers.Contract(contractAddress, contractAbi, voterWallet);
}

/**
 * Execute a blockchain operation with auto-reset for out-of-sync nonces.
 */
async function executeWithRetry(operationFn) {
  try {
    return await operationFn();
  } catch (err) {
    const msg = err.message || '';
    if (
      err.code === 'NONCE_EXPIRED' ||
      msg.includes('nonce') ||
      msg.includes('Nonce too low') ||
      msg.includes('already been used')
    ) {
      console.warn('⚠️ Nonce out-of-sync detected. Resetting NonceManager and retrying operation...');
      adminWallet.reset();
      // Brief delay for node to settle
      await new Promise(resolve => setTimeout(resolve, 300));
      return await operationFn();
    }
    throw err;
  }
}

// ── Blockchain Methods ───────────────────────────────────────────────────────

/**
 * Whitelists a voter address for a specific poll. Admin only.
 */
async function whitelistVoter(pollId, voterAddress) {
  const contract = getAdminContract();
  try {
    console.log(`Whitelisting ${voterAddress} for poll #${pollId}`);
    const tx = await executeWithRetry(() => contract.whitelistVoter(pollId, voterAddress));
    const receipt = await tx.wait();
    console.log(`Whitelist tx succeeded: ${receipt.hash}`);
    return receipt.hash;
  } catch (err) {
    console.error(`Whitelist failed for voter ${voterAddress}:`, err.message);
    throw wrapError('Whitelist failed', err);
  }
}

/**
 * Funds an ephemeral wallet with a gas stipend.
 */
async function fundGasStipend(toAddress, amountEth = '0.01') {
  try {
    console.log(`Funding ephemeral wallet ${toAddress} with ${amountEth} ETH gas stipend`);
    const tx = await executeWithRetry(() => adminWallet.sendTransaction({
      to: toAddress,
      value: ethers.parseEther(amountEth)
    }));
    const receipt = await tx.wait();
    console.log(`Gas stipend funded - tx: ${receipt.hash}`);
    return receipt.hash;
  } catch (err) {
    console.error(`Failed to fund gas stipend for ${toAddress}:`, err.message);
    throw wrapError('Failed to fund gas stipend', err);
  }
}

/**
 * Casts a vote on-chain signed by the voter's ephemeral private key.
 */
async function castVote(pollId, voterPrivateKey, optionIndex) {
  const contract = getVoterContract(voterPrivateKey);
  try {
    const voterAddress = await contract.runner.getAddress();
    console.log(`castVote - poll #${pollId}, voter: ${voterAddress}, option: ${optionIndex}`);
    
    // Explicitly restrict gas limit to 200,000 so upfront cost is within stipend
    const tx = await contract.castVote(pollId, optionIndex, {
      gasLimit: 200000
    });
    
    const receipt = await tx.wait();
    console.log(`Vote cast - tx: ${receipt.hash}, voter: ${voterAddress}`);
    return receipt.hash;
  } catch (err) {
    console.error(`castVote failed for poll #${pollId}:`, err.message);
    throw wrapError('castVote failed', err);
  }
}

/**
 * Gets high-level metadata about a poll.
 */
async function getPollInfo(pollId) {
  const contract = getAdminContract();
  try {
    const [question, startTime, endTime, isActive, totalVotes, optionCount] = 
      await contract.getPollInfo(pollId);
    return {
      question,
      startTime: Number(startTime),
      endTime: Number(endTime),
      active: isActive,
      totalVotes: Number(totalVotes),
      optionCount: Number(optionCount)
    };
  } catch (err) {
    console.error(`getPollInfo failed for poll #${pollId}:`, err.message);
    throw wrapError(`Failed to fetch info for poll #${pollId}`, err);
  }
}

/**
 * Gets results for a poll.
 */
async function getPollResults(pollId) {
  const contract = getAdminContract();
  try {
    const [question, options, counts, total] = await contract.getResults(pollId);
    
    const totalVotesNum = Number(total);

    // Format options with counts and percentages matching the React DTO
    const formattedOptions = options.map((label, index) => {
      const voteCount = Number(counts[index]);
      const percentage = totalVotesNum > 0 ? (voteCount / totalVotesNum) * 100 : 0;
      return {
        index,
        label,
        voteCount,
        percentage
      };
    });

    return {
      pollId: Number(pollId),
      question,
      options: formattedOptions,
      totalVotes: totalVotesNum
    };
  } catch (err) {
    console.error(`getResults failed for poll #${pollId}:`, err.message);
    throw wrapError(`Failed to fetch results for poll #${pollId}`, err);
  }
}

/**
 * Returns the total number of polls created.
 */
async function getTotalPolls() {
  const contract = getAdminContract();
  try {
    const total = await contract.totalPolls();
    return Number(total);
  } catch (err) {
    console.error('Failed to fetch totalPolls:', err.message);
    throw wrapError('Failed to fetch total polls', err);
  }
}

/**
 * Deploys a new poll. Admin only.
 */
async function createPoll(question, options, durationSeconds, startTime = 0) {
  const contract = getAdminContract();
  try {
    console.log(`Creating poll: "${question}" with options [${options.join(', ')}]`);
    const tx = await executeWithRetry(() => contract.createPoll(question, options, startTime, durationSeconds));
    const receipt = await tx.wait();
    
    // Find the PollCreated event log
    let pollId = 0;
    const log = receipt.logs.find(
      l => contract.interface.parseLog(l)?.name === 'PollCreated'
    );
    if (log) {
      const parsedLog = contract.interface.parseLog(log);
      pollId = Number(parsedLog.args.pollId);
    }
    
    console.log(`Poll created successfully. Assigned ID: ${pollId}`);
    return { pollId, txHash: receipt.hash };
  } catch (err) {
    console.error('Failed to create poll:', err.message);
    throw wrapError('Failed to create poll', err);
  }
}

// Helper to extract custom reverts or format errors nicely
function wrapError(context, err) {
  let message = err.message;
  // Look for contract revert signature or nested error
  if (err.data && err.data.message) {
    message = err.data.message;
  } else if (err.error && err.error.message) {
    message = err.error.message;
  }
  
  const wrapped = new Error(`${context}: ${message}`);
  wrapped.originalError = err;
  return wrapped;
}

module.exports = {
  whitelistVoter,
  fundGasStipend,
  castVote,
  getPollInfo,
  getPollResults,
  getTotalPolls,
  createPoll,
  provider,
  adminWallet
};
