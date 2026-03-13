# ======================================================================================
# HireOps OS - Stage 1 Absolute Startup
# ======================================================================================
# This script handles the "1-Command Deployment" to spin up the entire Stage 1 architecture:
# 1. PostgreSQL + pgvector Database (Docker Compose)
# 2. Spring Boot AI Engine (Background)
# 3. Next.js Glassmorphism UI (Foreground)

Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "   🚀 INITIATING HIREOPS OS (STAGE 1 EXPERT DEPLOYMENT)  " -ForegroundColor Cyan
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Dependencies Required: Docker Desktop, Java 21+, Node.js (npm), Maven" -ForegroundColor Yellow
Write-Host ""

$hireopsRoot = Get-Location

# 1. Spin up the Database Server (pgvector)
Write-Host "[1/3] Spinning up PostgreSQL Vector Database via Docker Compose..." -ForegroundColor Green
try {
    docker-compose up -d
    if ($LASTEXITCODE -ne 0) {
        Write-Host "⚠️ Docker Compose failed to start. Ensure Docker Desktop is running!" -ForegroundColor Red
        Write-Host "Falling back to application-defined data source if possible (Requires manual intervention)." -ForegroundColor DarkYellow
    } else {
        Write-Host "✅ Vector DB Online." -ForegroundColor Green
    }
} catch {
    Write-Host "⚠️ Docker not found or error executing docker-compose. Is Docker Desktop running?" -ForegroundColor Red
}

Write-Host ""

# 2. Start the Spring Boot AI Engine
Write-Host "[2/3] Warming up the Java/Spring Boot AI Engine..." -ForegroundColor Green
Write-Host "This process runs in the background. Logs are directed to app_run.log" -ForegroundColor DarkGray
try {
    # Using Start-Process to run in the background without locking the terminal
    Start-Process -FilePath "mvn" -ArgumentList "spring-boot:run -Dspring-boot.run.profiles=dev" -NoNewWindow -RedirectStandardOutput "app_run.log" -RedirectStandardError "app_run_error.log"
    Write-Host "✅ AI Engine ignited (Background Process)." -ForegroundColor Green
} catch {
     Write-Host "⚠️ Failed to start Spring Boot via Maven. Ensure 'mvn' is in your PATH." -ForegroundColor Red
}

Write-Host ""

# 3. Launch the Next.js UI
Write-Host "[3/3] Launching the Premium Glassmorphism UI (Next.js)..." -ForegroundColor Green
Write-Host "Terminal will now lock to the UI process. Press Ctrl+C to terminate." -ForegroundColor DarkGray
Write-Host "Access HireOps OS at: http://localhost:3000" -ForegroundColor Cyan
Write-Host ""

Set-Location "$hireopsRoot\hireops-ui"
try {
    # Using cmd /c as requested previously to ensure npm resolves correctly on Windows
    cmd.exe /c "npm run dev"
} catch {
    Write-Host "⚠️ Failed to start Next.js UI. Did you run 'npm install' in the hireops-ui folder?" -ForegroundColor Red
}

# Cleanup on exit (Note: Since we locked to npm run dev, this runs after Ctrl+C)
Set-Location $hireopsRoot
Write-Host ""
Write-Host "Shutting down HireOps UI..." -ForegroundColor Yellow
Write-Host "Note: The Java Backend and PostgreSQL DB are still running in the background." -ForegroundColor Yellow
Write-Host "To stop them, run 'docker-compose down' and kill the Java process." -ForegroundColor Yellow
