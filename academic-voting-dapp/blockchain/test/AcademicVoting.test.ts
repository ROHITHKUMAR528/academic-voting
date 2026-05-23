import { loadFixture, time } from "@nomicfoundation/hardhat-network-helpers";
import { expect } from "chai";
import { ethers } from "hardhat";
import { AcademicVoting } from "../typechain-types";
import { SignerWithAddress } from "@nomicfoundation/hardhat-ethers/signers";

// ─────────────────────────────────────────────────────────────────────────────
//  Test Helpers & Constants
// ─────────────────────────────────────────────────────────────────────────────

const DURATION_1H  = 3_600;   // seconds
const DURATION_24H = 86_400;

const DEFAULT_QUESTION = "Best programming language for backend?";
const DEFAULT_OPTIONS  = ["Java", "Go", "Rust", "Python"];

// ─────────────────────────────────────────────────────────────────────────────
//  Shared Fixture
// ─────────────────────────────────────────────────────────────────────────────

async function deployFixture() {
  const [admin, voter1, voter2, voter3, stranger] =
    await ethers.getSigners();

  const factory = await ethers.getContractFactory("AcademicVoting", admin);
  const contract = (await factory.deploy(admin.address)) as AcademicVoting;
  await contract.waitForDeployment();

  return { contract, admin, voter1, voter2, voter3, stranger };
}

/**
 * Helper: creates a poll and returns its pollId parsed from the event.
 */
async function createPoll(
  contract: AcademicVoting,
  question: string   = DEFAULT_QUESTION,
  options:  string[] = DEFAULT_OPTIONS,
  duration: number   = DURATION_24H
): Promise<bigint> {
  const tx      = await contract.createPoll(question, options, duration);
  const receipt = await tx.wait();

  const parsed = receipt?.logs
    .map((log) => {
      try { return contract.interface.parseLog(log); } catch { return null; }
    })
    .find((e) => e?.name === "PollCreated");

  if (!parsed) throw new Error("PollCreated event not found");
  return parsed.args.pollId as bigint;
}

// ─────────────────────────────────────────────────────────────────────────────
//  Test Suites
// ─────────────────────────────────────────────────────────────────────────────

describe("AcademicVoting", () => {
  // ── Deployment ─────────────────────────────────────────────────────────────
  describe("Deployment", () => {
    it("Sets the correct admin address", async () => {
      const { contract, admin } = await loadFixture(deployFixture);
      expect(await contract.admin()).to.equal(admin.address);
    });

    it("Reverts when deployed with zero-address admin", async () => {
      const [deployer] = await ethers.getSigners();
      const factory    = await ethers.getContractFactory("AcademicVoting", deployer);
      await expect(factory.deploy(ethers.ZeroAddress))
        .to.be.revertedWithCustomError({ interface: factory.interface }, "ZeroAddress");
    });

    it("Initialises with zero polls", async () => {
      const { contract } = await loadFixture(deployFixture);
      expect(await contract.getPollCount()).to.equal(0n);
    });
  });

  // ── createPoll ─────────────────────────────────────────────────────────────
  describe("createPoll()", () => {
    it("Emits PollCreated with correct data", async () => {
      const { contract, admin } = await loadFixture(deployFixture);
      const latestBlock = await time.latest();

      await expect(
        contract.createPoll(DEFAULT_QUESTION, DEFAULT_OPTIONS, DURATION_24H)
      )
        .to.emit(contract, "PollCreated")
        .withArgs(
          1n,                              // pollId = 1
          DEFAULT_QUESTION,
          latestBlock + 1,                 // startTime ≈ next block
          latestBlock + 1 + DURATION_24H   // endTime
        );
    });

    it("Increments poll IDs sequentially", async () => {
      const { contract } = await loadFixture(deployFixture);
      const id1 = await createPoll(contract);
      const id2 = await createPoll(contract);
      expect(id1).to.equal(1n);
      expect(id2).to.equal(2n);
    });

    it("Stores correct question and options", async () => {
      const { contract } = await loadFixture(deployFixture);
      const pollId = await createPoll(contract);
      const poll   = await contract.getPoll(pollId);
      expect(poll.question).to.equal(DEFAULT_QUESTION);
      expect(poll.options).to.deep.equal(DEFAULT_OPTIONS);
    });

    it("Initialises all vote counts to zero", async () => {
      const { contract } = await loadFixture(deployFixture);
      const pollId = await createPoll(contract);
      const poll   = await contract.getPoll(pollId);
      for (const count of poll.voteCounts) {
        expect(count).to.equal(0n);
      }
    });

    // ── Negative: access control ──
    it("Reverts when called by non-admin  →  NotAdmin", async () => {
      const { contract, voter1 } = await loadFixture(deployFixture);
      await expect(
        contract.connect(voter1).createPoll(DEFAULT_QUESTION, DEFAULT_OPTIONS, DURATION_24H)
      ).to.be.revertedWithCustomError(contract, "NotAdmin");
    });

    // ── Negative: input validation ──
    it("Reverts on empty question  →  EmptyQuestion", async () => {
      const { contract } = await loadFixture(deployFixture);
      await expect(
        contract.createPoll("", DEFAULT_OPTIONS, DURATION_24H)
      ).to.be.revertedWithCustomError(contract, "EmptyQuestion");
    });

    it("Reverts with only 1 option  →  TooFewOptions", async () => {
      const { contract } = await loadFixture(deployFixture);
      await expect(
        contract.createPoll(DEFAULT_QUESTION, ["Solo"], DURATION_24H)
      ).to.be.revertedWithCustomError(contract, "TooFewOptions");
    });

    it("Reverts with 11 options  →  TooManyOptions", async () => {
      const { contract } = await loadFixture(deployFixture);
      const tooMany = Array.from({ length: 11 }, (_, i) => `Option ${i}`);
      await expect(
        contract.createPoll(DEFAULT_QUESTION, tooMany, DURATION_24H)
      ).to.be.revertedWithCustomError(contract, "TooManyOptions");
    });

    it("Reverts on an empty option string  →  EmptyOption", async () => {
      const { contract } = await loadFixture(deployFixture);
      await expect(
        contract.createPoll(DEFAULT_QUESTION, ["Valid", ""], DURATION_24H)
      ).to.be.revertedWithCustomError(contract, "EmptyOption");
    });

    it("Reverts on duration below MIN_DURATION  →  InvalidDuration", async () => {
      const { contract } = await loadFixture(deployFixture);
      await expect(
        contract.createPoll(DEFAULT_QUESTION, DEFAULT_OPTIONS, 59)
      ).to.be.revertedWithCustomError(contract, "InvalidDuration");
    });

    it("Reverts on duration above MAX_DURATION  →  InvalidDuration", async () => {
      const { contract } = await loadFixture(deployFixture);
      const tooLong = 366 * 24 * 60 * 60;
      await expect(
        contract.createPoll(DEFAULT_QUESTION, DEFAULT_OPTIONS, tooLong)
      ).to.be.revertedWithCustomError(contract, "InvalidDuration");
    });
  });

  // ── whitelistVoter ─────────────────────────────────────────────────────────
  describe("whitelistVoter()", () => {
    it("Emits VoterWhitelisted and sets flag", async () => {
      const { contract, voter1 } = await loadFixture(deployFixture);
      const pollId = await createPoll(contract);

      await expect(contract.whitelistVoter(pollId, voter1.address))
        .to.emit(contract, "VoterWhitelisted")
        .withArgs(pollId, voter1.address);

      expect(await contract.isWhitelisted(pollId, voter1.address)).to.be.true;
    });

    it("Reverts when non-admin calls  →  NotAdmin", async () => {
      const { contract, voter1, voter2 } = await loadFixture(deployFixture);
      const pollId = await createPoll(contract);
      await expect(
        contract.connect(voter1).whitelistVoter(pollId, voter2.address)
      ).to.be.revertedWithCustomError(contract, "NotAdmin");
    });

    it("Reverts on zero address  →  ZeroAddress", async () => {
      const { contract } = await loadFixture(deployFixture);
      const pollId = await createPoll(contract);
      await expect(
        contract.whitelistVoter(pollId, ethers.ZeroAddress)
      ).to.be.revertedWithCustomError(contract, "ZeroAddress");
    });

    it("Reverts on duplicate whitelist  →  AlreadyWhitelisted", async () => {
      const { contract, voter1 } = await loadFixture(deployFixture);
      const pollId = await createPoll(contract);
      await contract.whitelistVoter(pollId, voter1.address);
      await expect(
        contract.whitelistVoter(pollId, voter1.address)
      ).to.be.revertedWithCustomError(contract, "AlreadyWhitelisted");
    });

    it("Reverts on non-existent poll  →  PollNotFound", async () => {
      const { contract, voter1 } = await loadFixture(deployFixture);
      await expect(
        contract.whitelistVoter(999n, voter1.address)
      ).to.be.revertedWithCustomError(contract, "PollNotFound");
    });
  });

  // ── whitelistVoterBatch ────────────────────────────────────────────────────
  describe("whitelistVoterBatch()", () => {
    it("Whitelists multiple voters and emits correct events", async () => {
      const { contract, voter1, voter2, voter3 } = await loadFixture(deployFixture);
      const pollId = await createPoll(contract);
      const voters = [voter1.address, voter2.address, voter3.address];

      const tx = await contract.whitelistVoterBatch(pollId, voters);
      await expect(tx).to.emit(contract, "VoterBatchWhitelisted").withArgs(pollId, 3n);

      for (const v of voters) {
        expect(await contract.isWhitelisted(pollId, v)).to.be.true;
      }
    });

    it("Silently skips already-whitelisted addresses (idempotent)", async () => {
      // Use all three voters from ONE fixture — do NOT call loadFixture again inside a test.
      const { contract, voter1, voter2, voter3 } = await loadFixture(deployFixture);
      const pollId = await createPoll(contract);

      // First batch: voter1 + voter2 are new → count = 2
      await contract.whitelistVoterBatch(pollId, [voter1.address, voter2.address]);

      // Second batch: voter1 is already whitelisted (skipped), voter3 is new → count = 1
      const tx = await contract.whitelistVoterBatch(pollId, [voter1.address, voter3.address]);
      await expect(tx).to.emit(contract, "VoterBatchWhitelisted").withArgs(pollId, 1n);
    });
  });

  // ── castVote ───────────────────────────────────────────────────────────────
  describe("castVote()", () => {
    /** Fixture that creates a poll AND whitelists voter1 & voter2. */
    async function pollWithVotersFixture() {
      const base    = await loadFixture(deployFixture);
      const { contract, voter1, voter2 } = base;
      const pollId  = await createPoll(contract);
      await contract.whitelistVoterBatch(pollId, [voter1.address, voter2.address]);
      return { ...base, pollId };
    }

    it("Emits VoteCast and increments tally correctly", async () => {
      const { contract, voter1, pollId } = await pollWithVotersFixture();

      await expect(contract.connect(voter1).castVote(pollId, 0n))
        .to.emit(contract, "VoteCast")
        .withArgs(pollId, 0n, 1n); // newOptionTotal = 1

      const poll = await contract.getPoll(pollId);
      expect(poll.voteCounts[0]).to.equal(1n);
      expect(poll.totalVotesCast).to.equal(1n);
    });

    it("Two different voters each vote for different options", async () => {
      const { contract, voter1, voter2, pollId } = await pollWithVotersFixture();

      await contract.connect(voter1).castVote(pollId, 0n);
      await contract.connect(voter2).castVote(pollId, 2n);

      const poll = await contract.getPoll(pollId);
      expect(poll.voteCounts[0]).to.equal(1n);
      expect(poll.voteCounts[2]).to.equal(1n);
      expect(poll.totalVotesCast).to.equal(2n);
    });

    it("Records hasVoted flag after voting", async () => {
      const { contract, voter1, pollId } = await pollWithVotersFixture();
      expect(await contract.hasVoted(pollId, voter1.address)).to.be.false;
      await contract.connect(voter1).castVote(pollId, 1n);
      expect(await contract.hasVoted(pollId, voter1.address)).to.be.true;
    });

    // ── CRITICAL: double-vote prevention ──
    it("Prevents double voting  →  AlreadyVoted", async () => {
      const { contract, voter1, pollId } = await pollWithVotersFixture();

      await contract.connect(voter1).castVote(pollId, 0n);
      await expect(
        contract.connect(voter1).castVote(pollId, 0n)
      ).to.be.revertedWithCustomError(contract, "AlreadyVoted");
    });

    it("Double vote attempt does NOT alter tally", async () => {
      const { contract, voter1, pollId } = await pollWithVotersFixture();

      await contract.connect(voter1).castVote(pollId, 0n);
      await expect(
        contract.connect(voter1).castVote(pollId, 1n)
      ).to.be.revertedWithCustomError(contract, "AlreadyVoted");

      const poll = await contract.getPoll(pollId);
      expect(poll.totalVotesCast).to.equal(1n); // unchanged
    });

    // ── CRITICAL: non-whitelisted voter ──
    it("Rejects vote from non-whitelisted address  →  NotWhitelisted", async () => {
      const { contract, stranger, pollId } = await pollWithVotersFixture();
      await expect(
        contract.connect(stranger).castVote(pollId, 0n)
      ).to.be.revertedWithCustomError(contract, "NotWhitelisted");
    });

    // ── CRITICAL: expired poll ──
    it("Rejects vote after deadline  →  VotingWindowClosed", async () => {
      const { contract, voter1, pollId } = await pollWithVotersFixture();

      // Fast-forward time past the 24-hour window
      await time.increase(DURATION_24H + 1);

      await expect(
        contract.connect(voter1).castVote(pollId, 0n)
      ).to.be.revertedWithCustomError(contract, "VotingWindowClosed");
    });

    // ── CRITICAL: manually closed poll ──
    it("Rejects vote on manually closed poll  →  PollAlreadyClosed", async () => {
      const { contract, admin, voter1, pollId } = await pollWithVotersFixture();

      await contract.connect(admin).closePoll(pollId);

      await expect(
        contract.connect(voter1).castVote(pollId, 0n)
      ).to.be.revertedWithCustomError(contract, "PollAlreadyClosed");
    });

    // ── Bounds check ──
    it("Rejects out-of-bounds option index  →  InvalidOptionIndex", async () => {
      const { contract, voter1, pollId } = await pollWithVotersFixture();
      // DEFAULT_OPTIONS has 4 entries; index 4 is out of bounds
      await expect(
        contract.connect(voter1).castVote(pollId, 4n)
      ).to.be.revertedWithCustomError(contract, "InvalidOptionIndex");
    });

    // ── Non-existent poll ──
    it("Reverts on non-existent pollId  →  PollNotFound", async () => {
      const { contract, voter1 } = await loadFixture(deployFixture);
      await expect(
        contract.connect(voter1).castVote(999n, 0n)
      ).to.be.revertedWithCustomError(contract, "PollNotFound");
    });
  });

  // ── closePoll ──────────────────────────────────────────────────────────────
  describe("closePoll()", () => {
    it("Admin can close an active poll, emits PollClosed", async () => {
      const { contract } = await loadFixture(deployFixture);
      const pollId = await createPoll(contract);

      await expect(contract.closePoll(pollId))
        .to.emit(contract, "PollClosed")
        .withArgs(pollId, 0n);

      const poll = await contract.getPoll(pollId);
      expect(poll.status).to.equal(1n); // PollStatus.CLOSED
    });

    it("Reverts if called by non-admin  →  NotAdmin", async () => {
      const { contract, voter1 } = await loadFixture(deployFixture);
      const pollId = await createPoll(contract);
      await expect(
        contract.connect(voter1).closePoll(pollId)
      ).to.be.revertedWithCustomError(contract, "NotAdmin");
    });

    it("Reverts if poll already closed  →  PollAlreadyClosed", async () => {
      const { contract } = await loadFixture(deployFixture);
      const pollId = await createPoll(contract);
      await contract.closePoll(pollId);
      await expect(
        contract.closePoll(pollId)
      ).to.be.revertedWithCustomError(contract, "PollAlreadyClosed");
    });

    it("Reverts on non-existent pollId  →  PollNotFound", async () => {
      const { contract } = await loadFixture(deployFixture);
      await expect(
        contract.closePoll(999n)
      ).to.be.revertedWithCustomError(contract, "PollNotFound");
    });
  });

  // ── Getter Functions ───────────────────────────────────────────────────────
  describe("Getter Functions", () => {
    it("getAllPollIds() returns all created poll IDs", async () => {
      const { contract } = await loadFixture(deployFixture);
      const id1 = await createPoll(contract);
      const id2 = await createPoll(contract);
      const ids  = await contract.getAllPollIds();
      expect(ids).to.deep.equal([id1, id2]);
    });

    it("isPollAcceptingVotes() returns true during active window", async () => {
      const { contract } = await loadFixture(deployFixture);
      const pollId = await createPoll(contract);
      expect(await contract.isPollAcceptingVotes(pollId)).to.be.true;
    });

    it("isPollAcceptingVotes() returns false after deadline", async () => {
      const { contract } = await loadFixture(deployFixture);
      const pollId = await createPoll(contract, DEFAULT_QUESTION, DEFAULT_OPTIONS, DURATION_1H);
      await time.increase(DURATION_1H + 1);
      expect(await contract.isPollAcceptingVotes(pollId)).to.be.false;
    });

    it("isPollAcceptingVotes() returns false when manually closed", async () => {
      const { contract } = await loadFixture(deployFixture);
      const pollId = await createPoll(contract);
      await contract.closePoll(pollId);
      expect(await contract.isPollAcceptingVotes(pollId)).to.be.false;
    });

    it("getOptionVoteCount() returns correct count per option", async () => {
      const { contract, voter1 } = await loadFixture(deployFixture);
      const pollId = await createPoll(contract);
      await contract.whitelistVoter(pollId, voter1.address);
      await contract.connect(voter1).castVote(pollId, 2n);
      expect(await contract.getOptionVoteCount(pollId, 2n)).to.equal(1n);
    });

    it("getOptionVoteCount() reverts on invalid index  →  InvalidOptionIndex", async () => {
      const { contract } = await loadFixture(deployFixture);
      const pollId = await createPoll(contract);
      await expect(
        contract.getOptionVoteCount(pollId, 99n)
      ).to.be.revertedWithCustomError(contract, "InvalidOptionIndex");
    });

    it("getPoll() reverts on non-existent pollId  →  PollNotFound", async () => {
      const { contract } = await loadFixture(deployFixture);
      await expect(
        contract.getPoll(404n)
      ).to.be.revertedWithCustomError(contract, "PollNotFound");
    });
  });

  // ── End-to-End Scenario ───────────────────────────────────────────────────
  describe("End-to-End: Full voting lifecycle", () => {
    it("Complete lifecycle: create → whitelist → vote → results", async () => {
      const { contract, admin, voter1, voter2, voter3, stranger } =
        await loadFixture(deployFixture);

      // 1. Admin creates poll
      const pollId = await createPoll(
        contract,
        "Best student project platform?",
        ["GitHub", "GitLab", "Bitbucket"],
        DURATION_24H
      );
      expect(await contract.getPollCount()).to.equal(1n);

      // 2. Whitelist voter1 & voter2; stranger is NOT whitelisted
      await contract.whitelistVoterBatch(pollId, [voter1.address, voter2.address, voter3.address]);

      // 3. Voters cast ballots
      await contract.connect(voter1).castVote(pollId, 0n); // GitHub
      await contract.connect(voter2).castVote(pollId, 0n); // GitHub
      await contract.connect(voter3).castVote(pollId, 1n); // GitLab

      // 4. Non-whitelisted address is rejected
      await expect(
        contract.connect(stranger).castVote(pollId, 0n)
      ).to.be.revertedWithCustomError(contract, "NotWhitelisted");

      // 5. Verify results
      const poll = await contract.getPoll(pollId);
      expect(poll.voteCounts[0]).to.equal(2n); // GitHub: 2
      expect(poll.voteCounts[1]).to.equal(1n); // GitLab:  1
      expect(poll.voteCounts[2]).to.equal(0n); // Bitbucket: 0
      expect(poll.totalVotesCast).to.equal(3n);

      // 6. Admin closes poll
      await contract.connect(admin).closePoll(pollId);
      expect((await contract.getPoll(pollId)).status).to.equal(1n);

      // 7. No further votes accepted
      await expect(
        contract.connect(voter1).castVote(pollId, 1n)
      ).to.be.revertedWithCustomError(contract, "PollAlreadyClosed");
    });
  });
});
