#!/usr/bin/env bash
# Disable exit-on-error here so that failed docker inspect calls during
# the poll loop don't abort the script. We handle errors explicitly.
set -uo pipefail

# ── Colours (gracefully disabled in non-TTY environments) ─────────────────────
if [ -t 1 ]; then
    C_RESET="\033[0m"
    C_BOLD="\033[1m"
    C_GREEN="\033[32m"
    C_YELLOW="\033[33m"
    C_CYAN="\033[36m"
    C_RED="\033[31m"
    C_DIM="\033[2m"
else
    C_RESET="" C_BOLD="" C_GREEN="" C_YELLOW="" C_CYAN="" C_RED="" C_DIM=""
fi

header() { echo -e "${C_BOLD}${C_CYAN} ==============================================${C_RESET}"; }
ok()     { echo -e "      ${C_GREEN}✔${C_RESET}  $*"; }
warn()   { echo -e "${C_YELLOW} [!]${C_RESET}  $*"; }
err()    { echo -e "${C_RED}[ERROR]${C_RESET}  $*"; }
step()   { echo -e "${C_BOLD}$*${C_RESET}"; }

echo ""
header
echo -e "${C_BOLD}${C_CYAN}  HireOps  |  AI-Powered Hiring Engine${C_RESET}"
header
echo ""

# ── Step 1: Check Docker is installed ─────────────────────────────────────────
step "[1/4] Checking Docker availability..."
if ! command -v docker &>/dev/null; then
    err "Docker is not installed or not in PATH."
    echo "     Install Docker Engine (Linux) or Docker Desktop (macOS) and retry."
    exit 1
fi
ok "Docker found."

# ── Step 2: Detect NVIDIA GPU on host ─────────────────────────────────────────
step "[2/4] Checking host for NVIDIA GPU..."
HAS_NVIDIA_GPU=0
if command -v nvidia-smi &>/dev/null && nvidia-smi &>/dev/null; then
    HAS_NVIDIA_GPU=1
    ok "NVIDIA GPU detected on host."
else
    echo "      No NVIDIA GPU detected (nvidia-smi absent or errored)."
fi

# ── Step 3: Verify Docker can access the GPU ──────────────────────────────────
USE_GPU=0
if [ "${HAS_NVIDIA_GPU}" -eq 1 ]; then
    step "[3/4] Verifying Docker NVIDIA GPU access..."
    if docker run --rm --gpus all nvidia/cuda:12.1.1-base-ubuntu22.04 nvidia-smi &>/dev/null; then
        USE_GPU=1
        ok "Docker can see the GPU. GPU mode enabled."
    else
        echo ""
        warn "Docker cannot access your NVIDIA GPU."
        echo "     The NVIDIA Container Toolkit may not be installed or configured."
        echo ""
        echo "     Fix steps (Linux):"
        echo "       curl -fsSL https://nvidia.github.io/libnvidia-container/gpgkey \\"
        echo "         | sudo gpg --dearmor -o /usr/share/keyrings/nvidia-container-toolkit-keyring.gpg"
        echo "       curl -s -L https://nvidia.github.io/libnvidia-container/stable/deb/nvidia-container-toolkit.list \\"
        echo "         | sed 's#deb https://#deb [signed-by=/usr/share/keyrings/nvidia-container-toolkit-keyring.gpg] https://#g' \\"
        echo "         | sudo tee /etc/apt/sources.list.d/nvidia-container-toolkit.list"
        echo "       sudo apt-get update && sudo apt-get install -y nvidia-container-toolkit"
        echo "       sudo nvidia-ctk runtime configure --runtime=docker"
        echo "       sudo systemctl restart docker"
        echo "     On macOS: GPU passthrough is not supported by Docker Desktop."
        echo ""
        warn "Falling back to CPU mode (AI scoring will work but be slower)."
        echo ""
    fi
else
    step "[3/4] No NVIDIA GPU on host - skipping Docker GPU check."
fi

# ── Step 4: Launch the stack ───────────────────────────────────────────────────
step "[4/4] Launching HireOps stack..."
if [ "${USE_GPU}" -eq 1 ]; then
    docker compose -f docker-compose.yml -f docker-compose.gpu.yml up -d --build
else
    docker compose -f docker-compose.yml up -d --build
fi

echo ""
echo -e "${C_DIM} Waiting for services to become ready..."
echo -e " (First run may take longer while images finish downloading)${C_RESET}"
echo ""

# ── Poll until all services are healthy (or timeout) ──────────────────────────
MAX_WAIT=60
ELAPSED=0
ALL_HEALTHY=0

while [ "${ELAPSED}" -lt "${MAX_WAIT}" ]; do
    PG_STATUS=$(docker inspect --format "{{.State.Health.Status}}" hireops-postgres-vector 2>/dev/null || echo "N/A")
    OLLAMA_STATUS=$(docker inspect --format "{{.State.Health.Status}}" hireops-ollama 2>/dev/null || echo "N/A")
    APP_STATUS=$(docker inspect --format "{{.State.Health.Status}}" hireops-engine-app 2>/dev/null || echo "N/A")

    # Icon helper
    icon() { [ "$1" = "healthy" ] && echo -e "${C_GREEN}✔${C_RESET}" || echo -e "${C_DIM}…${C_RESET}"; }

    printf "\r  PostgreSQL $(icon "$PG_STATUS")   Ollama $(icon "$OLLAMA_STATUS")   App $(icon "$APP_STATUS")   ${ELAPSED}s elapsed   "

    if [ "${PG_STATUS}" = "healthy" ] && \
       [ "${OLLAMA_STATUS}" = "healthy" ] && \
       [ "${APP_STATUS}" = "healthy" ]; then
        ALL_HEALTHY=1
        break
    fi

    sleep 3
    ELAPSED=$((ELAPSED + 3))
done

echo ""
echo ""

if [ "${ALL_HEALTHY}" -eq 0 ]; then
    warn "Services did not report healthy within ${MAX_WAIT}s."
    echo "     This is normal on first run while large images download."
    echo "     Check status : docker compose ps"
    echo "     Check logs   : docker compose logs -f"
    echo ""
else
    ok "All services healthy! (${ELAPSED}s)"
fi

# ── Final status banner ────────────────────────────────────────────────────────
echo ""
echo " ##############################################"
if [ "${USE_GPU}" -eq 1 ]; then
    echo -e " #                                            #"
    echo -e " #   INFERENCE MODE :  ${C_GREEN}${C_BOLD}GPU  (NVIDIA CUDA)${C_RESET}    #"
    echo -e " #   Ollama is using your graphics card.      #"
    echo -e " #   AI scoring will be fast.                 #"
    echo -e " #                                            #"
else
    echo -e " #                                            #"
    echo -e " #   INFERENCE MODE :  ${C_YELLOW}${C_BOLD}CPU  (no GPU found)${C_RESET}    #"
    echo -e " #   Ollama is running on the processor.      #"
    echo -e " #   AI scoring will work but be slower.      #"
    echo -e " #                                            #"
fi
echo " ##############################################"
echo ""
echo -e "  Dashboard : ${C_CYAN}${C_BOLD}http://localhost:8081${C_RESET}"
echo -e "  Ollama API: ${C_DIM}http://localhost:11434${C_RESET}"
echo ""
echo -e "${C_DIM}  To stop all services:  docker compose down${C_RESET}"
echo ""
