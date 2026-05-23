// test/AcademicVoting.test.js
// ─────────────────────────────────────────────────────────────────────────────
// Comprehensive unit tests for AcademicVoting.sol
// Run:  npx hardhat test
// ─────────────────────────────────────────────────────────────────────────────

const { expect }           = require("chai");
const { ethers }           = require("hardhat");
const { time, loadFixture }= require("@nomicfoundation/hardhat-toolbox/network-helpers");

// ─────────────────────────────────────────────────────────────────────────────
// Shared fixture: deploys contract + creates a standard 7-day poll
// startTime is set 1 hour ahead so it stays in the future even after
// other suites advance block time by small amounts.
// ─────────────────────────────────────────────────────────────────────────────
async function deployAndCreatePollFixture() {
  const [admin, voter1, voter2, voter3, stranger] = await ethers.getSigners();

  const AcademicVoting = await ethers.getContractFactory("AcademicVoting");
  const voting = await AcademicVoting.deploy();

  const options       = ["Alice", "Bob", "Carol"];
  const duration      = 7 * 24 * 3600; // 7 days
  const now           = await time.latest();
  const startTime     = now + 3600;    // starts 1 hour from snapshot — avoids
                                       // timestamp regression across suites

  const tx      = await voting.createPoll("Best Candidate?", options, startTime, duration);
  const receipt = await tx.wait();

  const pollCreatedEvent = receipt.logs
    .map((log) => { try { return voting.interface.parseLog(log); } catch { return null; } })
    .find((e) => e && e.name === "PollCreated");

  const pollId = pollCreatedEvent.args.pollId;

  return { voting, admin, voter1, voter2, voter3, stranger, pollId, startTime, duration, options };
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper: whitelist a voter and fast-forward past poll start.
// Uses time.increaseTo but only if current time < startTime + 1;
// otherwise the poll has already started (fine — just proceed).
// ─────────────────────────────────────────────────────────────────────────────
async function whitelistAndActivate(voting, pollId, voter, startTime) {
  await voting.whitelistVoter(pollId, voter.address);
  const current = await time.latest();
  if (current < startTime + 1) {
    await time.increaseTo(startTime + 1);
  }
}

// ═════════════════════════════════════════════════════════════════════════════
describe("AcademicVoting", function () {

  // ─────────────────────────────────────────────────────────────────────────
  // SUITE 1 — Deployment
  // ─────────────────────────────────────────────────────────────────────────
  describe("1. Deployment", function () {
    it("should set the deployer as admin", async function () {
      const { voting, admin } = await loadFixture(deployAndCreatePollFixture);
      expect(await voting.admin()).to.equal(admin.address);
    });

    it("should initialise totalPolls to 1 after fixture", async function () {
      const { voting } = await loadFixture(deployAndCreatePollFixture);
      expect(await voting.totalPolls()).to.equal(1n);
    });

    it("should emit AdminTransferred on deploy", async function () {
      const AcademicVoting = await ethers.getContractFactory("AcademicVoting");
      const [deployer] = await ethers.getSigners();
      const v = await AcademicVoting.deploy();
      const receipt = await v.deploymentTransaction().wait();

      const iface = AcademicVoting.interface;
      const adminEvent = receipt.logs
        .map((l) => { try { return iface.parseLog(l); } catch { return null; } })
        .find((e) => e && e.name === "AdminTransferred");

      expect(adminEvent).to.not.be.null;
      expect(adminEvent.args.newAdmin).to.equal(deployer.address);
    });
  });

  // ─────────────────────────────────────────────────────────────────────────
  // SUITE 2 — Poll Creation
  // ─────────────────────────────────────────────────────────────────────────
  describe("2. Poll Creation", function () {
    it("should revert if question is empty", async function () {
      const { voting } = await loadFixture(deployAndCreatePollFixture);
      await expect(
        voting.createPoll("", ["A", "B"], 0, 3600)
      ).to.be.revertedWithCustomError(voting, "EmptyQuestion");
    });

    it("should revert if fewer than 2 options are provided", async function () {
      const { voting } = await loadFixture(deployAndCreatePollFixture);
      await expect(
        voting.createPoll("Q?", ["OnlyOne"], 0, 3600)
      ).to.be.revertedWithCustomError(voting, "TooFewOptions");
    });

    it("should revert if more than MAX_OPTIONS (50) are provided", async function () {
      const { voting } = await loadFixture(deployAndCreatePollFixture);
      const tooMany = Array.from({ length: 51 }, (_, i) => `Option${i}`);
      await expect(
        voting.createPoll("Q?", tooMany, 0, 3600)
      ).to.be.revertedWithCustomError(voting, "TooManyOptions");
    });

    it("should revert if duration is zero", async function () {
      const { voting } = await loadFixture(deployAndCreatePollFixture);
      await expect(
        voting.createPoll("Q?", ["A", "B"], 0, 0)
      ).to.be.revertedWithCustomError(voting, "ZeroDuration");
    });

    it("should revert if an option label is empty", async function () {
      const { voting } = await loadFixture(deployAndCreatePollFixture);
      await expect(
        voting.createPoll("Q?", ["A", ""], 0, 3600)
      ).to.be.revertedWithCustomError(voting, "EmptyOptionText");
    });

    it("should revert if duplicate option labels exist", async function () {
      const { voting } = await loadFixture(deployAndCreatePollFixture);
      await expect(
        voting.createPoll("Q?", ["Same", "Same"], 0, 3600)
      ).to.be.revertedWithCustomError(voting, "DuplicateOptionText");
    });

    it("should revert createPoll if called by non-admin", async function () {
      const { voting, voter1 } = await loadFixture(deployAndCreatePollFixture);
      await expect(
        voting.connect(voter1).createPoll("Q?", ["A", "B"], 0, 3600)
      ).to.be.revertedWithCustomError(voting, "Unauthorized");
    });

    it("should correctly store poll data and emit PollCreated", async function () {
      const { voting, pollId, options, startTime, duration } =
        await loadFixture(deployAndCreatePollFixture);

      await expect(voting.createPoll("New Q?", ["X", "Y"], 0, 3600))
        .to.emit(voting, "PollCreated");

      const [q, opts, counts, total] = await voting.getResults(pollId);
      expect(q).to.equal("Best Candidate?");
      expect(opts).to.deep.equal(options);
      expect(total).to.equal(0n);
    });

    it("should use block.timestamp as start when startTime = 0", async function () {
      const { voting } = await loadFixture(deployAndCreatePollFixture);
      const before = await time.latest();
      await voting.createPoll("Q?", ["A", "B"], 0, 3600);
      // The second poll (pollId=1) should have startTime >= the current block
      const info = await voting.getPollInfo(1n);
      expect(info.startTime).to.be.gte(before);
    });
  });

  // ─────────────────────────────────────────────────────────────────────────
  // SUITE 3 — Voter Whitelisting
  // ─────────────────────────────────────────────────────────────────────────
  describe("3. Voter Whitelisting", function () {
    it("should whitelist a voter successfully", async function () {
      const { voting, pollId, voter1 } = await loadFixture(deployAndCreatePollFixture);
      await voting.whitelistVoter(pollId, voter1.address);
      expect(await voting.isWhitelisted(pollId, voter1.address)).to.be.true;
    });

    it("should emit VoterWhitelisted event", async function () {
      const { voting, pollId, voter1 } = await loadFixture(deployAndCreatePollFixture);
      await expect(voting.whitelistVoter(pollId, voter1.address))
        .to.emit(voting, "VoterWhitelisted")
        .withArgs(pollId, voter1.address);
    });

    it("should revert whitelisting if called by non-admin", async function () {
      const { voting, pollId, voter1, voter2 } = await loadFixture(deployAndCreatePollFixture);
      await expect(
        voting.connect(voter1).whitelistVoter(pollId, voter2.address)
      ).to.be.revertedWithCustomError(voting, "Unauthorized");
    });

    it("should revert on zero address", async function () {
      const { voting, pollId } = await loadFixture(deployAndCreatePollFixture);
      await expect(
        voting.whitelistVoter(pollId, ethers.ZeroAddress)
      ).to.be.revertedWithCustomError(voting, "ZeroAddress");
    });

    it("should revert on duplicate whitelist entry", async function () {
      const { voting, pollId, voter1 } = await loadFixture(deployAndCreatePollFixture);
      await voting.whitelistVoter(pollId, voter1.address);
      await expect(
        voting.whitelistVoter(pollId, voter1.address)
      ).to.be.revertedWithCustomError(voting, "AlreadyWhitelisted");
    });

    it("should batch-whitelist multiple voters", async function () {
      const { voting, pollId, voter1, voter2, voter3 } =
        await loadFixture(deployAndCreatePollFixture);
      await voting.whitelistVotersBatch(pollId, [voter1.address, voter2.address, voter3.address]);
      expect(await voting.isWhitelisted(pollId, voter1.address)).to.be.true;
      expect(await voting.isWhitelisted(pollId, voter2.address)).to.be.true;
      expect(await voting.isWhitelisted(pollId, voter3.address)).to.be.true;
    });

    it("should revert batch-whitelist exceeding MAX_BATCH_WHITELIST", async function () {
      const { voting, pollId } = await loadFixture(deployAndCreatePollFixture);
      const wallets = Array.from({ length: 201 }, () =>
        ethers.Wallet.createRandom().address
      );
      await expect(
        voting.whitelistVotersBatch(pollId, wallets)
      ).to.be.revertedWithCustomError(voting, "BatchLimitExceeded");
    });
  });

  // ─────────────────────────────────────────────────────────────────────────
  // SUITE 4 — Casting Votes (Happy Path)
  // ─────────────────────────────────────────────────────────────────────────
  describe("4. Casting Votes — Happy Path", function () {
    it("should allow a whitelisted voter to cast a vote", async function () {
      const { voting, pollId, voter1, startTime } =
        await loadFixture(deployAndCreatePollFixture);
      await whitelistAndActivate(voting, pollId, voter1, startTime);

      await expect(voting.connect(voter1).castVote(pollId, 0))
        .to.emit(voting, "VoteCast")
        .withArgs(pollId, voter1.address, 0n);
    });

    it("should increment the correct option's vote count", async function () {
      const { voting, pollId, voter1, startTime } =
        await loadFixture(deployAndCreatePollFixture);
      await whitelistAndActivate(voting, pollId, voter1, startTime);

      await voting.connect(voter1).castVote(pollId, 1); // vote for option[1] = Bob

      const [, , counts] = await voting.getResults(pollId);
      expect(counts[0]).to.equal(0);
      expect(counts[1]).to.equal(1);
      expect(counts[2]).to.equal(0);
    });

    it("should increment totalVotes on each vote", async function () {
      const { voting, pollId, voter1, voter2, voter3, startTime } =
        await loadFixture(deployAndCreatePollFixture);

      await voting.whitelistVotersBatch(pollId, [voter1.address, voter2.address, voter3.address]);
      await time.increaseTo(startTime + 1);

      await voting.connect(voter1).castVote(pollId, 0);
      await voting.connect(voter2).castVote(pollId, 0);
      await voting.connect(voter3).castVote(pollId, 2);

      const [, , , total] = await voting.getResults(pollId);
      expect(total).to.equal(3n);
    });

    it("should mark voter as hasVoted after casting", async function () {
      const { voting, pollId, voter1, startTime } =
        await loadFixture(deployAndCreatePollFixture);
      await whitelistAndActivate(voting, pollId, voter1, startTime);

      await voting.connect(voter1).castVote(pollId, 0);
      expect(await voting.hasVoted(pollId, voter1.address)).to.be.true;
    });
  });

  // ─────────────────────────────────────────────────────────────────────────
  // SUITE 5 — Double-Voting Guard (CRITICAL SECURITY)
  // ─────────────────────────────────────────────────────────────────────────
  describe("5. Double-Voting Guard", function () {
    it("should revert on a second vote by the same address", async function () {
      const { voting, pollId, voter1, startTime } =
        await loadFixture(deployAndCreatePollFixture);
      await whitelistAndActivate(voting, pollId, voter1, startTime);

      await voting.connect(voter1).castVote(pollId, 0);

      await expect(
        voting.connect(voter1).castVote(pollId, 1)
      ).to.be.revertedWithCustomError(voting, "AlreadyVoted");
    });

    it("should not increment count on a rejected double-vote", async function () {
      const { voting, pollId, voter1, startTime } =
        await loadFixture(deployAndCreatePollFixture);
      await whitelistAndActivate(voting, pollId, voter1, startTime);

      await voting.connect(voter1).castVote(pollId, 0);
      try { await voting.connect(voter1).castVote(pollId, 0); } catch (_) {}

      const [, , counts] = await voting.getResults(pollId);
      expect(counts[0]).to.equal(1);
    });
  });

  // ─────────────────────────────────────────────────────────────────────────
  // SUITE 6 — Non-Whitelisted Voting Attempts
  // ─────────────────────────────────────────────────────────────────────────
  describe("6. Non-Whitelisted Voting Attempts", function () {
    it("should revert if a non-whitelisted address tries to vote", async function () {
      const { voting, pollId, stranger, startTime } =
        await loadFixture(deployAndCreatePollFixture);
      await time.increaseTo(startTime + 1);

      await expect(
        voting.connect(stranger).castVote(pollId, 0)
      ).to.be.revertedWithCustomError(voting, "NotWhitelisted");
    });

    it("should revert if admin (not whitelisted) tries to vote", async function () {
      const { voting, pollId, admin, startTime } =
        await loadFixture(deployAndCreatePollFixture);
      await time.increaseTo(startTime + 1);

      await expect(
        voting.connect(admin).castVote(pollId, 0)
      ).to.be.revertedWithCustomError(voting, "NotWhitelisted");
    });
  });

  // ─────────────────────────────────────────────────────────────────────────
  // SUITE 7 — Deadline Enforcement (CRITICAL SECURITY)
  // ─────────────────────────────────────────────────────────────────────────
  describe("7. Deadline Enforcement", function () {
    it("should revert if voting after poll expiry", async function () {
      const { voting, pollId, voter1, startTime, duration } =
        await loadFixture(deployAndCreatePollFixture);
      await voting.whitelistVoter(pollId, voter1.address);

      // Jump past end of poll
      await time.increaseTo(startTime + duration + 1);

      await expect(
        voting.connect(voter1).castVote(pollId, 0)
      ).to.be.revertedWithCustomError(voting, "PollExpired");
    });

    it("should revert if voting before poll starts", async function () {      
      // Deploy a FRESH contract + poll with startTime 1 hour in the future
      // to guarantee block.timestamp < startTime regardless of test ordering.
      const [admin2, voter1b] = await ethers.getSigners();
      const AcademicVoting2 = await ethers.getContractFactory("AcademicVoting");
      const voting2 = await AcademicVoting2.deploy();

      const futureStart = (await time.latest()) + 7200; // 2 hours ahead
      await voting2.createPoll("Future Q?", ["X", "Y"], futureStart, 3600);
      await voting2.whitelistVoter(0n, voter1b.address);

      // Do NOT advance time — poll has not started
      await expect(
        voting2.connect(voter1b).castVote(0n, 0)
      ).to.be.revertedWithCustomError(voting2, "PollNotStarted");
    });

    it("should succeed exactly at startTime boundary", async function () {
      const { voting, pollId, voter1, startTime } =
        await loadFixture(deployAndCreatePollFixture);
      await voting.whitelistVoter(pollId, voter1.address);

      // Jump to exactly startTime (>= current, so no regression)
      const current = await time.latest();
      if (current < startTime) {
        await time.increaseTo(startTime);
      }

      await expect(voting.connect(voter1).castVote(pollId, 0)).to.not.be.reverted;
    });
  });

  // ─────────────────────────────────────────────────────────────────────────
  // SUITE 8 — Invalid Option Index
  // ─────────────────────────────────────────────────────────────────────────
  describe("8. Invalid Option Index", function () {
    it("should revert if option index is out of range", async function () {
      const { voting, pollId, voter1, startTime } =
        await loadFixture(deployAndCreatePollFixture);
      await whitelistAndActivate(voting, pollId, voter1, startTime);

      await expect(
        voting.connect(voter1).castVote(pollId, 99n)
      ).to.be.revertedWithCustomError(voting, "InvalidOption");
    });

    it("should revert getVoteCount with invalid index", async function () {
      const { voting, pollId } = await loadFixture(deployAndCreatePollFixture);
      await expect(
        voting.getVoteCount(pollId, 99n)
      ).to.be.revertedWithCustomError(voting, "InvalidOption");
    });
  });

  // ─────────────────────────────────────────────────────────────────────────
  // SUITE 9 — Results & Getter Accuracy
  // ─────────────────────────────────────────────────────────────────────────
  describe("9. Results & Getters", function () {
    it("getResults returns correct tallies after multiple votes", async function () {
      const { voting, pollId, voter1, voter2, voter3, startTime } =
        await loadFixture(deployAndCreatePollFixture);

      await voting.whitelistVotersBatch(pollId, [voter1.address, voter2.address, voter3.address]);
      await time.increaseTo(startTime + 1);

      await voting.connect(voter1).castVote(pollId, 0); // Alice: 1
      await voting.connect(voter2).castVote(pollId, 0); // Alice: 2
      await voting.connect(voter3).castVote(pollId, 2); // Carol: 1

      const [q, opts, counts, total] = await voting.getResults(pollId);
      expect(q).to.equal("Best Candidate?");
      expect(opts[0]).to.equal("Alice");
      expect(counts[0]).to.equal(2);
      expect(counts[1]).to.equal(0);
      expect(counts[2]).to.equal(1);
      expect(total).to.equal(3n);
    });

    it("getPollInfo returns correct active flag during active window", async function () {
      const { voting, pollId, startTime } = await loadFixture(deployAndCreatePollFixture);
      await time.increaseTo(startTime + 1);

      const info = await voting.getPollInfo(pollId);
      expect(info.isActive).to.be.true;
    });

    it("getPollInfo returns inactive flag after expiry", async function () {
      const { voting, pollId, startTime, duration } =
        await loadFixture(deployAndCreatePollFixture);
      await time.increaseTo(startTime + duration + 1);

      const info = await voting.getPollInfo(pollId);
      expect(info.isActive).to.be.false;
    });

    it("getVoteCount returns 0 before any votes", async function () {
      const { voting, pollId } = await loadFixture(deployAndCreatePollFixture);
      expect(await voting.getVoteCount(pollId, 0n)).to.equal(0);
    });

    it("should revert all getters on non-existent pollId", async function () {
      const { voting } = await loadFixture(deployAndCreatePollFixture);
      const nonExistent = 999n;

      await expect(voting.getResults(nonExistent))
        .to.be.revertedWithCustomError(voting, "PollNotFound");
      await expect(voting.getPollInfo(nonExistent))
        .to.be.revertedWithCustomError(voting, "PollNotFound");
      await expect(voting.getVoteCount(nonExistent, 0n))
        .to.be.revertedWithCustomError(voting, "PollNotFound");
    });
  });

  // ─────────────────────────────────────────────────────────────────────────
  // SUITE 10 — Admin Transfer
  // ─────────────────────────────────────────────────────────────────────────
  describe("10. Admin Transfer", function () {
    it("should transfer admin role successfully", async function () {
      const { voting, voter1 } = await loadFixture(deployAndCreatePollFixture);
      await voting.transferAdmin(voter1.address);
      expect(await voting.admin()).to.equal(voter1.address);
    });

    it("should emit AdminTransferred event on transfer", async function () {
      const { voting, admin, voter1 } = await loadFixture(deployAndCreatePollFixture);
      await expect(voting.transferAdmin(voter1.address))
        .to.emit(voting, "AdminTransferred")
        .withArgs(admin.address, voter1.address);
    });

    it("should revert transferAdmin to zero address", async function () {
      const { voting } = await loadFixture(deployAndCreatePollFixture);
      await expect(
        voting.transferAdmin(ethers.ZeroAddress)
      ).to.be.revertedWithCustomError(voting, "ZeroAddress");
    });

    it("should revert transferAdmin if called by non-admin", async function () {
      const { voting, voter1, voter2 } = await loadFixture(deployAndCreatePollFixture);
      await expect(
        voting.connect(voter1).transferAdmin(voter2.address)
      ).to.be.revertedWithCustomError(voting, "Unauthorized");
    });

    it("old admin cannot create polls after transfer", async function () {
      const { voting, admin, voter1 } = await loadFixture(deployAndCreatePollFixture);
      await voting.transferAdmin(voter1.address);
      await expect(
        voting.connect(admin).createPoll("Q?", ["A", "B"], 0, 3600)
      ).to.be.revertedWithCustomError(voting, "Unauthorized");
    });
  });

  // ─────────────────────────────────────────────────────────────────────────
  // SUITE 11 — Multiple Polls Isolation
  // ─────────────────────────────────────────────────────────────────────────
  describe("11. Multiple Polls Isolation", function () {
    it("votes on poll 0 should not affect poll 1 counts", async function () {
      const { voting, voter1, voter2, startTime, duration } =
        await loadFixture(deployAndCreatePollFixture);

      // Create a second poll
      const tx2 = await voting.createPoll("Second Q?", ["X", "Y"], 0, 3600);
      const receipt2 = await tx2.wait();
      const e2 = receipt2.logs
        .map((l) => { try { return voting.interface.parseLog(l); } catch { return null; } })
        .find((e) => e && e.name === "PollCreated");
      const pollId2 = e2.args.pollId;

      // Whitelist voter1 for poll 0 only
      await voting.whitelistVoter(0n, voter1.address);
      await time.increaseTo(startTime + 1);
      await voting.connect(voter1).castVote(0n, 0);

      // poll 1 should have zero votes
      const [, , counts2, total2] = await voting.getResults(pollId2);
      expect(total2).to.equal(0n);
      expect(counts2[0]).to.equal(0);
    });

    it("whitelist on poll 0 does not grant access to poll 1", async function () {
      const { voting, voter1, startTime } =
        await loadFixture(deployAndCreatePollFixture);

      await voting.createPoll("Second Q?", ["X", "Y"], 0, 3600);
      await voting.whitelistVoter(0n, voter1.address);
      await time.increaseTo(startTime + 1);

      // voter1 is NOT whitelisted for poll 1
      await expect(
        voting.connect(voter1).castVote(1n, 0)
      ).to.be.revertedWithCustomError(voting, "NotWhitelisted");
    });
  });
});
