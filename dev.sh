#!/bin/bash
# ⚡ Groundwork Hot Reloading & Development Startup Script

echo "⚡ Launching Groundwork stack with Live Reloading..."
docker rm -f groundwork-backend groundwork-frontend 2>/dev/null
docker compose up -d --build

echo "✅ Groundwork Live Stack is running!"
echo "🌐 Frontend (with Vite Hot Module Replacement): http://localhost:5173"
echo "⚙️ Backend API: http://localhost:8080"
