const { ethers, NonceManager } = require('ethers');

const providerUrl = 'http://localhost:8545';
const adminPrivateKey = '0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80';

const provider = new ethers.JsonRpcProvider(providerUrl);
const baseWallet = new ethers.Wallet(adminPrivateKey, provider);
const adminWallet = new NonceManager(baseWallet);

console.log('adminWallet.reset:', adminWallet.reset);
if (typeof adminWallet.reset === 'function') {
  console.log('✅ reset() is a function on NonceManager!');
} else {
  console.log('❌ reset() is NOT a function on NonceManager.');
}
