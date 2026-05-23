require("@nomicfoundation/hardhat-toolbox");
require("dotenv").config();

/** @type import('hardhat/config').HardhatUserConfig */
module.exports = {
  solidity: {
    version: "0.8.20",
    settings: {
      optimizer: {
        enabled: true,
        runs: 200,
      },
      // Enforce London EVM (compatible with most chains)
      evmVersion: "london",
    },
  },

  networks: {
    // ── Local Hardhat in-process node (used by `npx hardhat test`)
    hardhat: {
      chainId: 31337,
      // Mine blocks automatically on every transaction
      mining: {
        auto: true,
        interval: 0,
      },
      // Pre-funded accounts for testing
      accounts: {
        count: 10,
        accountsBalance: "10000000000000000000000", // 10 000 ETH each
      },
    },

    // ── Long-running local node (`npx hardhat node` then `--network localhost`)
    localhost: {
      url: "http://127.0.0.1:8545",
      chainId: 31337,
    },
  },

  // Gas reporter — enable with REPORT_GAS=true npx hardhat test
  gasReporter: {
    enabled: process.env.REPORT_GAS === "true",
    currency: "USD",
    outputFile: "gas-report.txt",
    noColors: true,
    coinmarketcap: process.env.COINMARKETCAP_API_KEY,
  },

  // Etherscan verification (for testnets / mainnet in later sprints)
  etherscan: {
    apiKey: process.env.ETHERSCAN_API_KEY || "",
  },

  // Source paths
  paths: {
    sources:   "./contracts",
    tests:     "./test",
    cache:     "./cache",
    artifacts: "./artifacts",
  },

  mocha: {
    timeout: 60000, // 60 s per test
  },
};
