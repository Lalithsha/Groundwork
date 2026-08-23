#!/usr/bin/env sh
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

cd "$PROJECT_DIR"
./scripts/secret-scan.sh
python3 -m json.tool docs/openapi.json >/dev/null
./mvnw --batch-mode test

cd "$PROJECT_DIR/frontend"
npm ci
npm run build
