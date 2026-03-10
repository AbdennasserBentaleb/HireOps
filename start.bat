@echo off
setlocal EnableDelayedExpansion

echo.
echo  =============================================
echo   HireOps  ^|  AI-Powered Hiring Engine
echo  =============================================
echo.

:: ── Step 1: Check Docker is installed ────────────────────────────────────────
echo [1/4] Checking Docker availability...
where docker >nul 2>nul
if !errorlevel! neq 0 (
    echo.
    echo  [ERROR] Docker is not installed or not in PATH.
    echo          Install Docker Desktop: https://www.docker.com/products/docker-desktop/
    echo          Then start Docker Desktop and re-run this script.
    echo.
    pause
    exit /b 1
)
echo       Docker found.

:: ── Step 2: Detect NVIDIA GPU on host ────────────────────────────────────────
echo [2/4] Checking host for NVIDIA GPU...
set "HAS_NVIDIA_GPU=0"
where nvidia-smi >nul 2>nul
if !errorlevel! equ 0 (
    nvidia-smi >nul 2>nul
    if !errorlevel! equ 0 (
        set "HAS_NVIDIA_GPU=1"
        echo       NVIDIA GPU detected on host.
    ) else (
        echo       nvidia-smi found but reported an error. Treating as no GPU.
    )
) else (
    echo       nvidia-smi not found - no NVIDIA GPU detected on this machine.
)

:: ── Step 3: Verify Docker can access the GPU ──────────────────────────────────
set "USE_GPU=0"
if "!HAS_NVIDIA_GPU!"=="1" (
    echo [3/4] Verifying Docker NVIDIA GPU access...
    docker run --rm --gpus all nvidia/cuda:12.1.1-base-ubuntu22.04 nvidia-smi >nul 2>nul
    set "DOCKER_GPU_RC=!errorlevel!"
    if !DOCKER_GPU_RC! equ 0 (
        set "USE_GPU=1"
        echo       Docker can see the GPU. GPU mode enabled.
    ) else (
        echo.
        echo  [!] Docker cannot access your NVIDIA GPU.
        echo      The NVIDIA Container Toolkit is likely missing or not configured.
        echo.
        echo      Fix steps ^(run inside WSL^):
        echo        curl -fsSL https://nvidia.github.io/libnvidia-container/gpgkey ^
        echo          ^| sudo gpg --dearmor -o /usr/share/keyrings/nvidia-container-toolkit-keyring.gpg
        echo        curl -s -L https://nvidia.github.io/libnvidia-container/stable/deb/nvidia-container-toolkit.list ^
        echo          ^| sed 's#deb https://#deb [signed-by=/usr/share/keyrings/nvidia-container-toolkit-keyring.gpg] https://#g' ^
        echo          ^| sudo tee /etc/apt/sources.list.d/nvidia-container-toolkit.list
        echo        sudo apt-get update ^&^& sudo apt-get install -y nvidia-container-toolkit
        echo        sudo nvidia-ctk runtime configure --runtime=docker
        echo        Restart Docker Desktop, then re-run this script.
        echo.
        echo      Falling back to CPU mode ^(AI scoring will be slower^).
        echo.
    )
) else (
    echo [3/4] No NVIDIA GPU on host - skipping Docker GPU check.
)

:: ── Step 4: Launch the stack ──────────────────────────────────────────────────
echo [4/4] Launching HireOps stack...
if "!USE_GPU!"=="1" (
    docker compose -f docker-compose.yml -f docker-compose.gpu.yml up -d --build
) else (
    docker compose -f docker-compose.yml up -d --build
)
if !errorlevel! neq 0 (
    echo.
    echo  [ERROR] docker compose failed. Review the output above.
    pause
    exit /b 1
)

:: ── Wait for services to become healthy ───────────────────────────────────────
echo.
echo  Waiting for services to become ready...
echo  (This may take up to 60 seconds on first run while images download)
echo.

set "MAX_WAIT=60"
set "ELAPSED=0"
set "ALL_HEALTHY=0"

:POLL_LOOP
if !ELAPSED! geq !MAX_WAIT! goto :TIMEOUT_REACHED

:: Check all three containers are in a running/healthy state
set "PG_OK=0"
set "OLLAMA_OK=0"
set "APP_OK=0"

for /f "usebackq tokens=*" %%S in (`docker inspect --format "{{.State.Health.Status}}" hireops-postgres-vector 2^>nul`) do (
    if "%%S"=="healthy" set "PG_OK=1"
)
for /f "usebackq tokens=*" %%S in (`docker inspect --format "{{.State.Health.Status}}" hireops-ollama 2^>nul`) do (
    if "%%S"=="healthy" set "OLLAMA_OK=1"
)
for /f "usebackq tokens=*" %%S in (`docker inspect --format "{{.State.Health.Status}}" hireops-engine-app 2^>nul`) do (
    if "%%S"=="healthy" set "APP_OK=1"
)

:: Display live status line
set "PG_ICON= [ ]"
set "OLLAMA_ICON= [ ]"
set "APP_ICON= [ ]"
if "!PG_OK!"=="1"     set "PG_ICON= [OK]"
if "!OLLAMA_OK!"=="1" set "OLLAMA_ICON= [OK]"
if "!APP_OK!"=="1"    set "APP_ICON= [OK]"

<nul set /p " =  PostgreSQL !PG_ICON!   Ollama !OLLAMA_ICON!   App !APP_ICON!   (!ELAPSED!s elapsed)   ^r"

if "!PG_OK!"=="1" if "!OLLAMA_OK!"=="1" if "!APP_OK!"=="1" (
    set "ALL_HEALTHY=1"
    goto :SERVICES_READY
)

timeout /t 3 >nul
set /a ELAPSED=!ELAPSED!+3
goto :POLL_LOOP

:TIMEOUT_REACHED
echo.
echo.
echo  [WARNING] Services did not report healthy within !MAX_WAIT! seconds.
echo            This can happen on first run while large images finish downloading.
echo            Check status with:  docker compose ps
echo            Check logs with:    docker compose logs -f
echo.
goto :PRINT_BANNER

:SERVICES_READY
echo.
echo.
echo  All services are healthy ^(!ELAPSED!s^).

:PRINT_BANNER
echo.
if "!USE_GPU!"=="1" (
    echo  ##############################################
    echo  #                                            #
    echo  #   INFERENCE MODE :  GPU  ^(NVIDIA CUDA^)     #
    echo  #   Ollama is using your graphics card.      #
    echo  #   AI scoring will be fast.                 #
    echo  #                                            #
    echo  ##############################################
) else (
    echo  ##############################################
    echo  #                                            #
    echo  #   INFERENCE MODE :  CPU  ^(no GPU found^)    #
    echo  #   Ollama is running on the processor.      #
    echo  #   AI scoring will work but be slower.      #
    echo  #                                            #
    echo  ##############################################
)
echo.
echo   Dashboard : http://localhost:8081
echo   Ollama API: http://localhost:11434
echo.
echo  To stop all services run:  docker compose down
echo.
pause
endlocal
