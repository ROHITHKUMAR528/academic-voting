const { ethers } = require('ethers');

const API_BASE = 'http://localhost:8080/api';

async function runTests() {
  console.log('=== Starting E2E Verification Tests ===\n');

  try {
    // 1. Signup a new user
    const testUserId = `teststudent_${Date.now()}`;
    const signupData = {
      userId: testUserId,
      name: 'Test Student',
      email: `${testUserId}@university.edu`,
      password: 'password123',
      role: 'STUDENT'
    };

    console.log(`1. Testing Signup for ${testUserId}...`);
    const signupRes = await fetch(`${API_BASE}/auth/signup`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(signupData)
    });
    const signupJson = await signupRes.json();
    console.log('   Signup Status:', signupRes.status);
    console.log('   Signup Response:', JSON.stringify(signupJson));
    if (signupRes.status !== 201) throw new Error('Signup failed');

    // 2. Login as the newly created user
    console.log(`\n2. Testing Login for ${testUserId}...`);
    const loginRes = await fetch(`${API_BASE}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        userId: testUserId,
        password: 'password123'
      })
    });
    const loginJson = await loginRes.json();
    console.log('   Login Status:', loginRes.status);
    if (loginRes.status !== 200) throw new Error('Login failed');
    const token = loginJson.data.token;
    console.log('   JWT Token obtained successfully.');

    // 3. Verify user profile using /auth/me
    console.log('\n3. Testing /auth/me profile retrieval...');
    const meRes = await fetch(`${API_BASE}/auth/me`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    const meJson = await meRes.json();
    console.log('   Profile Status:', meRes.status);
    console.log('   Profile Data:', JSON.stringify(meJson.data));
    if (meRes.status !== 200) throw new Error('Me profile failed');

    // 4. Log in as admin to create a poll
    console.log('\n4. Logging in as Admin (admin001)...');
    const adminLoginRes = await fetch(`${API_BASE}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        userId: 'admin001',
        password: 'admin123'
      })
    });
    const adminLoginJson = await adminLoginRes.json();
    if (adminLoginRes.status !== 200) throw new Error('Admin login failed');
    const adminToken = adminLoginJson.data.token;
    console.log('   Admin JWT Token obtained.');

    // 5. Create a poll as admin
    console.log('\n5. Creating a new poll as Admin...');
    const pollData = {
      question: `Who should be the next President? (Test ${Date.now()})`,
      options: ['Alice', 'Bob', 'Carol'],
      durationSeconds: 3600 // 1 hour
    };
    const createPollRes = await fetch(`${API_BASE}/polls`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${adminToken}`
      },
      body: JSON.stringify(pollData)
    });
    const createPollJson = await createPollRes.json();
    console.log('   Create Poll Status:', createPollRes.status);
    console.log('   Create Poll Response:', JSON.stringify(createPollJson));
    if (createPollRes.status !== 201) throw new Error('Create poll failed');
    const pollId = createPollJson.data.pollId;
    console.log(`   New Poll ID is: ${pollId}`);

    // 6. Obtain voting credential for the poll as the student
    console.log(`\n6. Obtaining voting credential for Poll #${pollId} as student ${testUserId}...`);
    const credRes = await fetch(`${API_BASE}/auth/voting-credential`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({ pollId })
    });
    const credJson = await credRes.json();
    console.log('   Get Credential Status:', credRes.status);
    console.log('   Get Credential Response:', JSON.stringify(credJson));
    if (credRes.status !== 201) throw new Error('Get voting credential failed');
    const { ephemeralAddress, ephemeralPrivateKey } = credJson.data;

    // 7. Cast vote using the ephemeral private key
    console.log(`\n7. Casting vote for Poll #${pollId} using ephemeral key...`);
    const voteData = {
      voterPrivateKey: ephemeralPrivateKey,
      optionIndex: 1 // Vote for Bob
    };
    const voteRes = await fetch(`${API_BASE}/polls/${pollId}/vote`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(voteData)
    });
    const voteJson = await voteRes.json();
    console.log('   Cast Vote Status:', voteRes.status);
    console.log('   Cast Vote Response:', JSON.stringify(voteJson));
    if (voteRes.status !== 200) throw new Error('Cast vote failed');

    // 8. Get Poll results and verify Bob has 1 vote
    console.log(`\n8. Retrieving Poll Results for Poll #${pollId}...`);
    const resultsRes = await fetch(`${API_BASE}/polls/${pollId}/results`);
    const resultsJson = await resultsRes.json();
    console.log('   Get Results Status:', resultsRes.status);
    console.log('   Results:', JSON.stringify(resultsJson.data));
    if (resultsRes.status !== 200) throw new Error('Get results failed');

    const bobOption = resultsJson.data.options.find(opt => opt.label === 'Bob');
    if (!bobOption || bobOption.voteCount !== 1) {
      throw new Error(`Expected Bob to have 1 vote, but got: ${JSON.stringify(bobOption)}`);
    }
    console.log('   ✅ Verification successful! Bob has exactly 1 vote on-chain.');

    console.log('\n=== E2E VERIFICATION PASSED SUCCESSFULLY ===');
  } catch (err) {
    console.error('\n❌ E2E VERIFICATION FAILED:', err.message);
    process.exit(1);
  }
}

runTests();
