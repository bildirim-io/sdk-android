#!/usr/bin/env bash
# Central Portal deployment durumu. Jeton ortamdan okunur, EKRANA BASILMAZ.
#   CENTRAL_USER=… CENTRAL_PASS=… scripts/release-status.sh <DEPLOYMENT_ID>
set -euo pipefail
ID="${1:?kullanım: scripts/release-status.sh <DEPLOYMENT_ID>}"
: "${CENTRAL_USER:?CENTRAL_USER gerekli}"; : "${CENTRAL_PASS:?CENTRAL_PASS gerekli}"
TOKEN=$(printf '%s:%s' "$CENTRAL_USER" "$CENTRAL_PASS" | base64)
curl -s -H "Authorization: Bearer $TOKEN" -X POST \
  "https://central.sonatype.com/api/v1/publisher/status?id=$ID" | python3 -m json.tool
