# -----------------------------------------------------------------------------
#  run-backend.ps1  -  Academic Voting Node.js Backend Launcher
#
#  Sets env vars and starts the Express backend.
#
#  Usage:
#    .\run-backend.ps1 -ContractAddress "0x5FbDB2315678afecb367f032d93F642f64180aa3"
# -----------------------------------------------------------------------------

param(
    [string]$ContractAddress = $env:CONTRACT_ADDRESS,
    [string]$MongoUri = "mongodb://localhost:27017/academic_voting"
)

Write-Host ""
Write-Host "=== Academic Voting Node.js Backend ===" -ForegroundColor Cyan
Write-Host ""

# -- Verify Node.js ------------------------------------------------------------
try {
    $nodeVer = & node -v 2>&1
    Write-Host "  Node.js: $nodeVer" -ForegroundColor Green
} catch {
    Write-Host "  Error: Node.js is not found on your PATH." -ForegroundColor Red
    Exit 1
}

# -- Configure Environment ----------------------------------------------------
if ([string]::IsNullOrWhiteSpace($ContractAddress)) {
    Write-Host "  Warning: No contract address provided." -ForegroundColor Yellow
    Write-Host "  Note: On-chain transactions will fail until CONTRACT_ADDRESS is set." -ForegroundColor Yellow
    $ContractAddress = Read-Host "  Paste contract address (or press Enter to skip)"
}

$env:CONTRACT_ADDRESS = $ContractAddress
$env:MONGODB_URI = $MongoUri
$env:PORT = "8080"
$env:JWT_SECRET = "YWNhZGVtaWN2b3RpbmctandrLXNlY3JldC1rZXktMzI="

if (![string]::IsNullOrWhiteSpace($ContractAddress)) {
    Write-Host "  Contract: $ContractAddress" -ForegroundColor Cyan
}
Write-Host "  Database: $MongoUri" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Starting Node.js server on http://localhost:8080 ..." -ForegroundColor Green
Write-Host "  Press Ctrl+C to stop." -ForegroundColor DarkGray
Write-Host ""

# -- Start Node.js ------------------------------------------------------------
npm start
