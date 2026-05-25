const blockchain = require('../services/blockchain');

/**
 * Create a new poll
 * POST /api/polls
 */
async function createPoll(req, res) {
  try {
    const { question, options, durationSeconds, startTime } = req.body;

    if (!question || !options || !durationSeconds) {
      return res.status(400).json({ status: 400, message: 'question, options, and durationSeconds are required' });
    }

    const { pollId, txHash } = await blockchain.createPoll(
      question,
      options,
      Number(durationSeconds),
      startTime ? Number(startTime) : 0
    );

    return res.status(201).json({
      status: 201,
      message: 'Poll created successfully',
      data: { pollId },
      txHash
    });

  } catch (err) {
    console.error('Create poll error:', err);
    return res.status(400).json({ status: 400, message: err.message });
  }
}

/**
 * Cast a vote on a poll
 * POST /api/polls/:pollId/vote
 */
async function castVote(req, res) {
  try {
    const { pollId } = req.params;
    const { voterPrivateKey, optionIndex } = req.body;

    if (!voterPrivateKey || optionIndex === undefined) {
      return res.status(400).json({ status: 400, message: 'voterPrivateKey and optionIndex are required' });
    }

    const txHash = await blockchain.castVote(Number(pollId), voterPrivateKey, Number(optionIndex));

    return res.status(200).json({
      status: 200,
      message: 'Vote cast successfully',
      txHash
    });

  } catch (err) {
    console.error('Cast vote error:', err);
    // Custom formatted blockchain error
    return res.status(400).json({
      status: 400,
      message: err.message
    });
  }
}

/**
 * Get info for a specific poll
 * GET /api/polls/:pollId/info
 */
async function getPollInfo(req, res) {
  try {
    const { pollId } = req.params;
    const info = await blockchain.getPollInfo(Number(pollId));
    return res.json({
      status: 200,
      message: 'Success',
      data: info
    });
  } catch (err) {
    console.error('Get poll info error:', err);
    return res.status(404).json({ status: 404, message: err.message });
  }
}

/**
 * Get poll results
 * GET /api/polls/:pollId/results
 */
async function getResults(req, res) {
  try {
    const { pollId } = req.params;
    const results = await blockchain.getPollResults(Number(pollId));
    return res.json({
      status: 200,
      message: 'Success',
      data: results
    });
  } catch (err) {
    console.error('Get results error:', err);
    return res.status(404).json({ status: 404, message: err.message });
  }
}

/**
 * Get total number of polls
 * GET /api/polls/count
 */
async function getTotalPolls(req, res) {
  try {
    const total = await blockchain.getTotalPolls();
    return res.json({
      status: 200,
      message: 'Success',
      data: { totalPolls: total }
    });
  } catch (err) {
    console.error('Get total polls error:', err);
    return res.status(500).json({ status: 500, message: err.message });
  }
}

/**
 * Whitelist a voter for a specific poll
 * POST /api/polls/:pollId/whitelist
 */
async function whitelistVoter(req, res) {
  try {
    const { pollId } = req.params;
    const { address } = req.body;

    if (!address) {
      return res.status(400).json({ status: 400, message: 'address is required' });
    }

    const txHash = await blockchain.whitelistVoter(Number(pollId), address);

    return res.status(200).json({
      status: 200,
      message: 'Voter whitelisted successfully',
      txHash
    });

  } catch (err) {
    console.error('Whitelist voter error:', err);
    return res.status(400).json({ status: 400, message: err.message });
  }
}

module.exports = {
  createPoll,
  castVote,
  getPollInfo,
  getResults,
  getTotalPolls,
  whitelistVoter
};
