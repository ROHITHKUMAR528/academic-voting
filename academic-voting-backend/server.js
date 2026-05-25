require('dotenv').config();
const express = require('express');
const mongoose = require('mongoose');
const cors = require('cors');

const authMiddleware = require('./middleware/auth');
const authController = require('./controllers/authController');
const pollController = require('./controllers/pollController');
const User = require('./models/User');

const app = express();
const PORT = process.env.PORT || 8080;
const MONGODB_URI = process.env.MONGODB_URI || 'mongodb://localhost:27017/academic_voting';

// ── Middleware ───────────────────────────────────────────────────────────────
app.use(cors());
app.use(express.json());

// Request logger
app.use((req, res, next) => {
  console.log(`${new Date().toLocaleTimeString()} [http] ${req.method} ${req.originalUrl}`);
  next();
});

// ── Routes ───────────────────────────────────────────────────────────────────

// Auth endpoints
app.post('/api/auth/signup', authController.signup);
app.post('/api/auth/login', authController.login);
app.get('/api/auth/me', authMiddleware.protect, authController.me);
app.post('/api/auth/voting-credential', authMiddleware.protect, authController.getVotingCredential);

// Poll endpoints
app.post('/api/polls', authMiddleware.protect, authMiddleware.authorize('ADMIN'), pollController.createPoll);
app.post('/api/polls/:pollId/vote', pollController.castVote);
app.get('/api/polls/:pollId/info', pollController.getPollInfo);
app.get('/api/polls/:pollId/results', pollController.getResults);
app.get('/api/polls/count', pollController.getTotalPolls);
app.post('/api/polls/:pollId/whitelist', authMiddleware.protect, authMiddleware.authorize('ADMIN'), pollController.whitelistVoter);

// Health check
app.get('/health', (req, res) => res.json({ status: 'UP' }));

// Global Error Handler
app.use((err, req, res, next) => {
  console.error('Unhandled error:', err);
  res.status(err.status || 500).json({
    status: err.status || 500,
    message: err.message || 'Internal server error'
  });
});

// ── MongoDB Connection & Mock User Seeding ───────────────────────────────────
mongoose.connect(MONGODB_URI)
  .then(async () => {
    console.log(`Connected to MongoDB database: ${mongoose.connection.name}`);
    await seedMockUsers();
    
    // Start Server
    app.listen(PORT, () => {
      console.log(`🚀 Academic Voting Backend running on http://localhost:${PORT}`);
    });
  })
  .catch(err => {
    console.error('Database connection failed:', err.message);
    process.exit(1);
  });

/**
 * Pre-populates default university accounts if they do not exist in the database.
 */
async function seedMockUsers() {
  try {
    const mockUsers = [
      {
        userId: 'student001',
        name: 'Alice Johnson',
        email: 'alice@university.edu',
        role: 'STUDENT',
        password: 'password123'
      },
      {
        userId: 'student002',
        name: 'Bob Smith',
        email: 'bob@university.edu',
        role: 'STUDENT',
        password: 'password456'
      },
      {
        userId: 'student003',
        name: 'Carol Williams',
        email: 'carol@university.edu',
        role: 'STUDENT',
        password: 'password789'
      },
      {
        userId: 'admin001',
        name: 'Admin User',
        email: 'admin@university.edu',
        role: 'ADMIN',
        password: 'admin123'
      }
    ];

    for (const mock of mockUsers) {
      const exists = await User.findOne({ userId: mock.userId });
      if (!exists) {
        const user = new User({
          userId: mock.userId,
          name: mock.name,
          email: mock.email,
          role: mock.role,
          passwordHash: mock.password // will be hashed by Mongoose pre-save
        });
        await user.save();
        console.log(`Seeded mock user account: ${user.userId} (${user.role})`);
      }
    }
  } catch (err) {
    console.error('Failed to seed mock users:', err.message);
  }
}
