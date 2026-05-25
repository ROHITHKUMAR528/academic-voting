const mongoose = require('mongoose');

const CommitmentSchema = new mongoose.Schema({
  userId: {
    type: String,
    required: true,
    trim: true,
    lowercase: true,
  },
  pollId: {
    type: Number,
    required: true,
  },
  commitment: {
    type: String,
    required: true,
  },
}, {
  timestamps: true,
});

// Enforce single credential per user per poll
CommitmentSchema.index({ userId: 1, pollId: 1 }, { unique: true });

module.exports = mongoose.model('Commitment', CommitmentSchema);
