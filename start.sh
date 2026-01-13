#!/bin/bash
echo "[HireOps] Checking Docker availability..."
if ! command -v docker &> /dev/null; then
    echo "[ERROR] Docker is not installed or not in PATH."
    exit 1
fi

echo "[HireOps] Checking for NVIDIA GPU support inside Docker..."
if docker run --rm --gpus all alpine echo "Checking..." &> /dev/null; then
    echo "[+] NVIDIA GPU detected and supported by Docker!"
    echo "[+] Starting with GPU acceleration enabled..."
    docker-compose -f docker-compose.yml -f docker-compose.gpu.yml up -d --build
else
    echo "[-] No compatible GPU detected or missing Toolkit fallback."
    echo "[-] Defaulting to CPU-only execution (may process AI scoring slower)."
    docker-compose up -d --build
fi

echo ""
echo "[+] Setup triggered. Access dashboard at: http://localhost:8081"
