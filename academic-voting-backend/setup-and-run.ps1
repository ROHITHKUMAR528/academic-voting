#!/usr/bin/env pwsh
# ─────────────────────────────────────────────────────────────────────────────
# setup-and-run.ps1
# Full setup script for Sprint 2 — Academic Voting Backend
#
# Run from project root:
#   .\setup-and-run.ps1
# ─────────────────────────────────────────────────────────────────────────────

Write-Host "═══════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  Academic Voting Backend — Sprint 2 Setup Script  " -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════" -ForegroundColor Cyan

# ── 1. Check Java ─────────────────────────────────────────────────────────────
Write-Host "`n[1/5] Checking Java 17+..." -ForegroundColor Yellow
try {
    $javaVer = java -version 2>&1
    Write-Host "  ✅ $($javaVer[0])" -ForegroundColor Green
} catch {
    Write-Host "  ❌ Java not found. Install Java 17+ from:" -ForegroundColor Red
    Write-Host "     https://adoptium.net/ (Eclipse Temurin 17 LTS)" -ForegroundColor White
    Write-Host "     Or via winget:  winget install EclipseAdoptium.Temurin.17.JDK" -ForegroundColor White
    exit 1
}

# ── 2. Check Maven ────────────────────────────────────────────────────────────
Write-Host "`n[2/5] Checking Maven..." -ForegroundColor Yellow
try {
    $mvnVer = mvn --version 2>&1
    Write-Host "  ✅ $($mvnVer[0])" -ForegroundColor Green
} catch {
    Write-Host "  ❌ Maven not found. Install via:" -ForegroundColor Red
    Write-Host "     winget install Apache.Maven" -ForegroundColor White
    Write-Host "     Or download from: https://maven.apache.org/download.cgi" -ForegroundColor White
    exit 1
}

# ── 3. Check CONTRACT_ADDRESS ─────────────────────────────────────────────────
Write-Host "`n[3/5] Checking CONTRACT_ADDRESS env var..." -ForegroundColor Yellow
if (-not $env:CONTRACT_ADDRESS) {
    Write-Host "  ⚠️  CONTRACT_ADDRESS not set." -ForegroundColor Yellow
    Write-Host "     The app will auto-deploy a fresh contract on startup." -ForegroundColor White
    Write-Host "     To use an existing deployment, run:" -ForegroundColor White
    Write-Host "       `$env:CONTRACT_ADDRESS = '0x<your-address>'" -ForegroundColor White
} else {
    Write-Host "  ✅ CONTRACT_ADDRESS = $env:CONTRACT_ADDRESS" -ForegroundColor Green
}

# ── 4. Compile ────────────────────────────────────────────────────────────────
Write-Host "`n[4/5] Compiling with Maven..." -ForegroundColor Yellow
mvn compile -q
if ($LASTEXITCODE -ne 0) {
    Write-Host "  ❌ Compilation failed. Check the errors above." -ForegroundColor Red
    exit 1
}
Write-Host "  ✅ Compilation successful" -ForegroundColor Green

# ── 5. Run ────────────────────────────────────────────────────────────────────
Write-Host "`n[5/5] Starting Spring Boot application..." -ForegroundColor Yellow
Write-Host "  API will be available at: http://localhost:8080/api" -ForegroundColor Cyan
Write-Host "  Press Ctrl+C to stop.`n" -ForegroundColor White

mvn spring-boot:run
