#!/usr/bin/env bash
# Vendored contracts/mobile-sdk.json ile canlı GET /v1/mobile/contract aynı mı?
#
# SÜRÜM YETMEZ: sunucu bir kez `version` 3'te kalırken gövdeye alan ekledi
# (`identityHash`, `sdk.*.api.login`) ve yalnız sürüme bakan kontrol bunu KAÇIRDI.
# Bu yüzden kanonik (anahtarları sıralı) içerik karşılaştırılıyor; fark varsa döker.
#
# Ayrışma çıktığında: dosyayı yenile, Contract sabitlerini ve doküman-yüzeyi testlerini
# gözden geçir (yayındaki dokümanlar API'yi kilitler), sonra testleri koştur.
set -euo pipefail
cd "$(dirname "$0")/.."
API="${API:-https://api.bildirim.io}"

curl -fsS "$API/v1/mobile/contract" -o /tmp/bildirim-live-contract.json

python3 - "$API" <<'PY'
import json, sys, subprocess
api = sys.argv[1]
local = json.load(open('contracts/mobile-sdk.json'))
live = json.load(open('/tmp/bildirim-live-contract.json'))
if local == live:
    print(f"sözleşme uyumlu (v{live.get('version')})")
    sys.exit(0)

lv, wv = local.get('version'), live.get('version')
print(f"SÖZLEŞME AYRIŞTI: yerel v{lv}, canlı v{wv}"
      + ("  (SÜRÜM AYNI, İÇERİK FARKLI — sessiz değişiklik)" if lv == wv else ""), file=sys.stderr)

def flat(o, p=''):
    if isinstance(o, dict):
        for k, v in o.items(): yield from flat(v, f'{p}.{k}' if p else k)
    elif isinstance(o, list):
        for i, v in enumerate(o): yield from flat(v, f'{p}[{i}]')
    else:
        yield p, o

a, b = dict(flat(local)), dict(flat(live))
for k in sorted(set(a) | set(b)):
    if a.get(k) != b.get(k):
        print(f"  {k}:\n    yerel: {a.get(k)!r}\n    canlı: {b.get(k)!r}", file=sys.stderr)

print(f"\nGüncelle: curl -fsS {api}/v1/mobile/contract | python3 -m json.tool --no-ensure-ascii > contracts/mobile-sdk.json",
      file=sys.stderr)
print("Sonra: Contract sabitleri + doküman-yüzeyi testleri + testler.", file=sys.stderr)
sys.exit(1)
PY
