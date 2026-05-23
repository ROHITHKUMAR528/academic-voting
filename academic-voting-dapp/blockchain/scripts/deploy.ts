import { ethers } from "hardhat";
import { AcademicVoting } from "../typechain-types";

/**
 * Deploy script for AcademicVoting.sol
 *
 * Usage:
 *   npx hardhat run scripts/deploy.ts --network localhost   (hardhat node)
 *   npx hardhat run scripts/deploy.ts --network anvil       (foundry anvil)
 *
 * After deployment the script:
 *   1. Deploys the contract with signers[0] as admin.
 *   2. Creates a sample poll with a 24-hour window.
 *   3. Whitelists signers[1] and signers[2] as test voters.
 *   4. Prints all relevant addresses and parameters for the Java backend config.
 */
async function main(): Promise<void> {
  const signers = await ethers.getSigners();
  const adminSigner = signers[0];

  console.log("═══════════════════════════════════════════════════════════");
  console.log("  Academic Voting — Deployment Script");
  console.log("═══════════════════════════════════════════════════════════");
  console.log(`  Network   : ${(await ethers.provider.getNetwork()).name}`);
  console.log(`  Admin     : ${adminSigner.address}`);
  console.log(`  Balance   : ${ethers.formatEther(await ethers.provider.getBalance(adminSigner.address))} ETH`);
  console.log("───────────────────────────────────────────────────────────");

  // ── 1. Deploy ────────────────────────────────────────────────────────────
  console.log("\n[1/4] Deploying AcademicVoting contract…");

  const factory = await ethers.getContractFactory("AcademicVoting", adminSigner);
  const contract = (await factory.deploy(adminSigner.address)) as AcademicVoting;
  await contract.waitForDeployment();

  const contractAddress = await contract.getAddress();
  console.log(`      ✔  Contract deployed at: ${contractAddress}`);

  // ── 2. Create a sample poll ──────────────────────────────────────────────
  console.log("\n[2/4] Creating sample poll…");

  const DURATION_24H = 24 * 60 * 60; // seconds

  const tx1 = await contract.createPoll(
    "Which framework should the CS department adopt for its 2024 curriculum?",
    ["React", "Angular", "Vue.js", "Svelte"],
    DURATION_24H
  );
  const receipt1 = await tx1.wait();

  // Parse PollCreated event to grab pollId
  const pollCreatedEvent = receipt1?.logs
    .map((log) => {
      try { return contract.interface.parseLog(log); } catch { return null; }
    })
    .find((e) => e?.name === "PollCreated");

  const pollId: bigint = pollCreatedEvent?.args.pollId ?? 1n;
  console.log(`      ✔  Poll #${pollId} created  (gas: ${receipt1?.gasUsed})`);

  // ── 3. Whitelist test voters ─────────────────────────────────────────────
  console.log("\n[3/4] Whitelisting test voters…");

  const testVoters = signers.slice(1, 4).map((s) => s.address);
  const tx2 = await contract.whitelistVoterBatch(pollId, testVoters);
  const receipt2 = await tx2.wait();
  console.log(`      ✔  ${testVoters.length} voters whitelisted  (gas: ${receipt2?.gasUsed})`);
  testVoters.forEach((v, i) => console.log(`         Voter ${i + 1}: ${v}`));

  // ── 4. Print backend config snippet ─────────────────────────────────────
  console.log("\n[4/4] Spring Boot application.properties snippet:");
  console.log("───────────────────────────────────────────────────────────");
  console.log(`web3j.client-address=http://127.0.0.1:8545`);
  console.log(`web3j.admin-private-key=0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80`);
  console.log(`app.contract.address=${contractAddress}`);
  console.log("───────────────────────────────────────────────────────────");
  console.log("  ⚠  The private key above is an Anvil/Hardhat test key.");
  console.log("     NEVER use it on a public network.");
  console.log("═══════════════════════════════════════════════════════════\n");
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
