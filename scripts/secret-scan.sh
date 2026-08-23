#!/usr/bin/env sh
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$PROJECT_DIR"

if rg -n --hidden --glob '!.git/**' --glob '!frontend/node_modules/**' --glob '!frontend/dist/**' --glob '!target/**' \
  '(-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----|AIza[0-9A-Za-z_-]{30,}|sk-[0-9A-Za-z]{32,}|gh[pousr]_[0-9A-Za-z]{30,})'; then
  echo "Potential credential found in tracked files" >&2
  exit 1
fi

echo "No high-confidence credential patterns found"
