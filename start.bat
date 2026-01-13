@echo off
echo [HireOps] Checking Docker availability...
where docker >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] Docker is not installed or not in PATH.
    echo Please install Docker Desktop before running this script.
    pause
    exit /b 1
)

echo [HireOps] Checking for NVIDIA GPU support inside Docker...
docker run --rm --gpus all alpine echo "Checking..." >nul 2>&1

if %errorlevel% equ 0 (
    echo [+] NVIDIA GPU detected and supported by Docker!
    echo [+] Starting with GPU acceleration enabled...
    docker-compose -f docker-compose.yml -f docker-compose.gpu.yml up -d --build
) else (
    echo [-] No compatible GPU detected or missing Toolkit fallback.
    echo [-] Defaulting to CPU-only execution (may process AI scoring slower).
    docker-compose up -d --build
)

echo.
echo [+] Setup triggered. You can access the dashboard at:
echo     http://localhost:8081
pause
