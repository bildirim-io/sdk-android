#!/usr/bin/env bash
# Vendored contracts/mobile-sdk.json ile canlı GET /v1/mobile/contract aynı sürümde mi?
# CI'da çalıştırın; ayrışırsa sunucu sözleşmeyi değiştirmiştir → dosyayı güncelleyip testleri koşun.
set -euo pipefail
cd "$(dirname "$0")/.."
API="${API:-https://api.bildirim.io}"
local_v=$(python3 -c "import json;print(json.load(open('contracts/mobile-sdk.json'))['version'])")
live_v=$(curl -fsS "$API/v1/mobile/contract" | python3 -c "import json,sys;print(json.load(sys.stdin)['version'])")
if [ "$local_v" != "$live_v" ]; then
  echo "SÖZLEŞME AYRIŞTI: yerel v$local_v, canlı v$live_v — güncelle: curl -s $API/v1/mobile/contract | python3 -m json.tool --no-ensure-ascii > contracts/mobile-sdk.json" >&2
  exit 1
fi
echo "sözleşme uyumlu (v$local_v)"
