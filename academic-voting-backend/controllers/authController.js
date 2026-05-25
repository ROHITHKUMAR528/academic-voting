const crypto = require('crypto');
const jwt = require('jsonwebtoken');
const { ethers } = require('ethers');
const User = require('../models/User');
const Commitment = require('../models/Commitment');
const { JWT_SECRET } = require('../middleware/auth');
const blockchain = require('../services/blockchain');

/**
 * Register a new user
 * POST /api/auth/signup
 */
async function signup(req, res) {
  try {
    const { userId, name, email, password, role } = req.body;

    if (!userId || !name || !email || !password) {
      return res.status(400).json({ status: 400, message: 'All fields are required' });
    }

    const normalizedUserId = userId.trim().toLowerCase();

    // Check if userId is taken
    const existing = await User.findOne({ userId: normalizedUserId });
    if (existing) {
      return res.status(400).json({ status: 400, message: `User ID '${userId}' is already registered` });
    }

    // Assign role (only STUDENT or ADMIN allowed, default STUDENT)
    const finalRole = (role === 'ADMIN') ? 'ADMIN' : 'STUDENT';

    // Create user. Mongoose will hash password on save() hook
    const user = new User({
      userId: normalizedUserId,
      name: name.trim(),
      email: email.trim().toLowerCase(),
      role: finalRole,
      passwordHash: password
    });

    await user.save();

    console.log(`Successfully signed up user: ${user.userId} [${user.role}]`);

    return res.status(201).json({
      status: 201,
      message: 'Signup successful. You can now log in.',
      data: {
        userId: user.userId,
        name: user.name,
        email: user.email,
        role: user.role
      }
    });

  } catch (err) {
    console.error('Signup error:', err);
    return res.status(500).json({ status: 500, message: err.message });
  }
}

/**
 * Authenticate user and issue JWT
 * POST /api/auth/login
 */
async function login(req, res) {
  try {
    const { userId, password } = req.body;

    if (!userId || !password) {
      return res.status(400).json({ status: 400, message: 'Invalid credentials' });
    }

    const normalizedUserId = userId.trim().toLowerCase();

    // Find user
    const user = await User.findOne({ userId: normalizedUserId });
    if (!user) {
      return res.status(400).json({ status: 400, message: 'Invalid credentials' });
    }

    // Check password
    const isMatch = await user.comparePassword(password);
    if (!isMatch) {
      return res.status(400).json({ status: 400, message: 'Invalid credentials' });
    }

    // Generate JWT
    const token = jwt.sign({
      sub: user.userId,
      name: user.name,
      email: user.email,
      role: user.role
    }, JWT_SECRET, { expiresIn: '24h' });

    const expiresAt = new Date(Date.now() + 86400000).toISOString(); // 24 hours

    console.log(`User logged in successfully: ${user.userId} [${user.role}]`);

    return res.status(200).json({
      status: 200,
      message: 'Success',
      data: {
        token,
        tokenType: 'Bearer',
        userId: user.userId,
        name: user.name,
        email: user.email,
        role: user.role,
        expiresAt
      }
    });

  } catch (err) {
    console.error('Login error:', err);
    return res.status(500).json({ status: 500, message: 'Internal server error' });
  }
}

/**
 * Return profile of currently logged-in user
 * GET /api/auth/me
 */
async function me(req, res) {
  try {
    const count = await Commitment.countDocuments({ userId: req.user.userId });
    return res.json({
      status: 200,
      message: 'Success',
      data: {
        userId: req.user.userId,
        name: req.user.name,
        email: req.user.email,
        role: req.user.role,
        credentialsIssued: count
      }
    });
  } catch (err) {
    console.error('Me endpoint error:', err);
    return res.status(500).json({ status: 500, message: 'Internal server error' });
  }
}

/**
 * Issue a one-time voting credential for the authenticated STUDENT
 * POST /api/auth/voting-credential
 */
async function getVotingCredential(req, res) {
  try {
    const { pollId } = req.body;
    
    if (pollId === undefined || pollId === null) {
      return res.status(400).json({ status: 400, message: 'pollId is required' });
    }

    // 1. Guard: only STUDENTs can obtain credentials
    if (req.user.role !== 'STUDENT') {
      return res.status(400).json({ status: 400, message: 'Only students are permitted to obtain voting credentials.' });
    }

    // 2. Guard: check double issuance
    const existing = await Commitment.findOne({ userId: req.user.userId, pollId: Number(pollId) });
    if (existing) {
      return res.status(409).json({
        status: 409,
        message: `Voting credential already issued for poll #${pollId}. Each voter may only receive one credential per poll.`
      });
    }

    // 3. Generate Ephemeral EC Keypair
    const ephemeralWallet = ethers.Wallet.createRandom();
    const ephemeralAddress = ephemeralWallet.address;
    const ephemeralPrivateKey = ephemeralWallet.privateKey;

    // 4. Generate One-way Commitment: SHA-256(userId || ":" || pollId || ":" || base64(salt))
    const saltBytes = crypto.randomBytes(32);
    const saltB64 = saltBytes.toString('base64');
    const preimage = `${req.user.userId}:${pollId}:${saltB64}`;
    const commitment = crypto.createHash('sha256').update(preimage).digest('hex');

    console.log(`Issuing credential for poll #${pollId} - whitelisting ephemeral address: ${ephemeralAddress}`);

    // 5. Whitelist address on-chain
    const whitelistTxHash = await blockchain.whitelistVoter(Number(pollId), ephemeralAddress);

    // 6. Fund Gas Stipend (0.01 ETH)
    // Stipend is configurable via VOTER_GAS_STIPEND_ETH
    const stipendAmount = process.env.VOTER_GAS_STIPEND_ETH || '0.01';
    await blockchain.fundGasStipend(ephemeralAddress, stipendAmount);

    // 7. Save Commitment to DB
    const newCommitment = new Commitment({
      userId: req.user.userId,
      pollId: Number(pollId),
      commitment
    });
    await newCommitment.save();

    console.log(`Credential issued & commitment saved for userId: ${req.user.userId}, pollId: ${pollId}`);

    return res.status(201).json({
      status: 201,
      message: 'Voting credential issued. Save your ephemeralPrivateKey — it will NOT be shown again.',
      data: {
        pollId: Number(pollId),
        ephemeralAddress,
        ephemeralPrivateKey,
        commitment,
        whitelistTxHash
      }
    });

  } catch (err) {
    console.error('Voting credential issuance error:', err);
    return res.status(400).json({ status: 400, message: err.message });
  }
}

module.exports = {
  signup,
  login,
  me,
  getVotingCredential
};
