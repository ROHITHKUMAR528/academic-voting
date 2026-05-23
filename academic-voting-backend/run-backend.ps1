# -----------------------------------------------------------------------------
#  run-backend.ps1  -  Academic Voting Backend launcher
#
#  Sets JAVA_HOME + MAVEN_HOME (adjust paths if yours differ), then runs
#  the Spring Boot backend with the given CONTRACT_ADDRESS.
#
#  Usage:
#    .\run-backend.ps1 -ContractAddress "0x5FbDB2315678afecb367f032d93F642f64180aa3"
#
#  Or just:
#    .\run-backend.ps1          (reuses LAST_CONTRACT env var or prompts)
# -----------------------------------------------------------------------------

param(
    [string]$ContractAddress = $env:CONTRACT_ADDRESS
)

# -- Tool Paths (edit these if your install locations differ) ------------------
$JAVA_HOME   = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
$MAVEN_HOME  = "C:\Users\rohit\Downloads\apache-maven-3.9.16-bin\apache-maven-3.9.16"

# -- Put both on PATH for this session -----------------------------------------
$env:JAVA_HOME  = $JAVA_HOME
$env:MAVEN_HOME = $MAVEN_HOME
$env:Path       = "$JAVA_HOME\bin;$MAVEN_HOME\bin;$env:Path"

# -- Verify tools --------------------------------------------------------------
Write-Host ""
Write-Host "=== Academic Voting Backend ===" -ForegroundColor Cyan
Write-Host ""

$javaVer = & java -version 2>&1 | Select-Object -First 1
Write-Host "  Java  : $javaVer" -ForegroundColor Green

$mvnVer  = & mvn -version 2>&1 | Select-Object -First 1
Write-Host "  Maven : $mvnVer" -ForegroundColor Green

# -- Contract address ----------------------------------------------------------
if ([string]::IsNullOrWhiteSpace($ContractAddress)) {
    Write-Host ""
    Write-Host "  No contract address provided." -ForegroundColor Yellow
    Write-Host "  Tip: deploy first with: npx hardhat run scripts/deploy.js --network localhost" -ForegroundColor Yellow
    Write-Host ""
    $ContractAddress = Read-Host "  Paste contract address (or press Enter to auto-deploy)"
}

$env:CONTRACT_ADDRESS = $ContractAddress

if ([string]::IsNullOrWhiteSpace($ContractAddress)) {
    Write-Host ""
    Write-Host "  No address given - Spring Boot will deploy a fresh contract." -ForegroundColor Magenta
} else {
    Write-Host ""
    Write-Host "  Contract : $ContractAddress" -ForegroundColor Cyan
}

Write-Host ""
Write-Host "  Starting Spring Boot on http://localhost:8080 ..." -ForegroundColor Green
Write-Host "  Press Ctrl+C to stop." -ForegroundColor DarkGray
Write-Host ""

# -- Launch --------------------------------------------------------------------
mvn spring-boot:run
