// scripts/deploy.js
// ─────────────────────────────────────────────────────────────────────────────
// Deployment script for AcademicVoting.sol
// Usage:
//   npx hardhat run scripts/deploy.js --network localhost
// ─────────────────────────────────────────────────────────────────────────────

const hre = require("hardhat");

async function main() {
  const [deployer, voter1, voter2, voter3] = await hre.ethers.getSigners();

  console.log("═══════════════════════════════════════════════════════════");
  console.log("  Academic Voting dApp — Deployment Script");
  console.log("═══════════════════════════════════════════════════════════");
  console.log(`  Deploying from: ${deployer.address}`);
  console.log(
    `  Balance:        ${hre.ethers.formatEther(
      await hre.ethers.provider.getBalance(deployer.address)
    )} ETH`
  );
  console.log("───────────────────────────────────────────────────────────");

  // ── 1. Deploy contract ────────────────────────────────────────────────────
  console.log("\n[1/4] Deploying AcademicVoting...");
  const AcademicVoting = await hre.ethers.getContractFactory("AcademicVoting");
  const voting = await AcademicVoting.deploy();
  await voting.waitForDeployment();

  const contractAddress = await voting.getAddress();
  console.log(`      ✅ AcademicVoting deployed at: ${contractAddress}`);

  // ── 2. Create a sample poll ───────────────────────────────────────────────
  console.log("\n[2/4] Creating sample poll: Dean Election 2025...");

  const options = [
    "Dr. Alice Morgan    (Faculty of Engineering)",
    "Prof. Robert Clarke (Faculty of Sciences)",
    "Dr. Sarah Bennett   (Faculty of Arts)",
  ];

  const now = Math.floor(Date.now() / 1000);
  const startTime = now;                  // starts immediately
  const durationSeconds = 7 * 24 * 3600; // 7 days

  const createTx = await voting.createPoll(
    "Who should be elected as Dean of the University for 2025–2026?",
    options,
    startTime,
    durationSeconds
  );
  const createReceipt = await createTx.wait();

  // Extract pollId from PollCreated event
  const pollCreatedEvent = createReceipt.logs
    .map((log) => {
      try {
        return voting.interface.parseLog(log);
      } catch {
        return null;
      }
    })
    .find((e) => e && e.name === "PollCreated");

  const pollId = pollCreatedEvent
    ? pollCreatedEvent.args.pollId
    : 0n;

  console.log(`      ✅ Poll #${pollId} created`);
  console.log(`         Opens:  ${new Date(startTime * 1000).toISOString()}`);
  console.log(`         Closes: ${new Date((startTime + durationSeconds) * 1000).toISOString()}`);

  // ── 3. Whitelist voters ───────────────────────────────────────────────────
  console.log("\n[3/4] Whitelisting test voters...");

  const testVoters = [voter1, voter2, voter3];
  const batchAddresses = testVoters.map((v) => v.address);

  const whitelistTx = await voting.whitelistVotersBatch(pollId, batchAddresses);
  await whitelistTx.wait();

  for (const v of testVoters) {
    console.log(`      ✅ Whitelisted: ${v.address}`);
  }

  // ── 4. Print summary ──────────────────────────────────────────────────────
  console.log("\n[4/4] Deployment Summary");
  console.log("───────────────────────────────────────────────────────────");
  console.log(`  Contract Address : ${contractAddress}`);
  console.log(`  Admin Address    : ${deployer.address}`);
  console.log(`  Poll ID          : ${pollId}`);
  console.log(`  Poll Question    : "Who should be elected as Dean..."`);
  console.log(`  Options          : ${options.length}`);
  console.log(`  Whitelisted      : ${testVoters.length} voters`);
  console.log("═══════════════════════════════════════════════════════════");
  console.log("  ✅ Sprint 1 deployment complete!");
  console.log("═══════════════════════════════════════════════════════════\n");

  // Return for programmatic use / testing
  return { voting, contractAddress, pollId };
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error("❌ Deployment failed:", error);
    process.exit(1);
  });
