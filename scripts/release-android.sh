#!/usr/bin/env bash
# Faz E — Maven Central yayını (Sonatype Central Portal, io.bildirim ad alanı).
#
# Ön koşullar (bir kez):
#   1. central.sonatype.com hesabı; ad alanı io.bildirim → DNS TXT doğrulaması (bildirim.io).
#   2. GPG anahtarı: gpg --full-generate-key (RSA 4096) → açık anahtarı keyserver'a gönder:
#        gpg --keyserver keyserver.ubuntu.com --send-keys <KEYID>
#   3. Ortam değişkenleri:
#        SIGNING_KEY="$(gpg --armor --export-secret-keys <KEYID>)"  SIGNING_PASSWORD=...
#        CENTRAL_USER / CENTRAL_PASS  (Portal → View Account → Generate User Token)
#
# Çalıştır: scripts/release-android.sh            → bundle üretir + imzalar + Portal'a yükler
#           DRY_RUN=1 scripts/release-android.sh  → yalnız bundle üretir (yükleme yok)
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION=$(grep -m1 '^VERSION_NAME=' gradle.properties | cut -d= -f2)
echo "Sürüm: $VERSION"
grep -q "SDK_VERSION: String = \"$VERSION\"" bildirim/src/main/kotlin/io/bildirim/sdk/Version.kt \
  || { echo "Version.kt ile gradle.properties farklı — scripts/bump-version.sh $VERSION"; exit 1; }
scripts/check-contract.sh

# Yayınlanan sürüm, ÇALIŞMA AĞACINDAN değil commit'lenmiş koddan derlenmeli ve o commit
# uzakta olmalı — 1.0.0, itilmemiş bir kimlik-doğrulama commit'i varken yayınlandı ve eksik çıktı.
# Maven Central kalıcı olduğu için bu kapı DRY_RUN'da bile uyarır, yayında durdurur.
if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "Çalışma ağacı kirli — önce commit edin (yayınlanan paket commit'lenmiş koddan derlenir)." >&2
  [ -z "${DRY_RUN:-}" ] && exit 1
fi
if git rev-parse --abbrev-ref --symbolic-full-name '@{u}' >/dev/null 2>&1; then
  AHEAD=$(git rev-list --count '@{u}'..HEAD)
  if [ "$AHEAD" != "0" ]; then
    echo "İtilmemiş $AHEAD commit var — önce 'git push origin main':" >&2
    git log --oneline '@{u}'..HEAD >&2
    [ -z "${DRY_RUN:-}" ] && exit 1
  fi
else
  echo "UYARI: uzak dal ayarlı değil (git push -u origin main)." >&2
fi

if [ -z "${SIGNING_KEY:-}" ] && [ -z "${DRY_RUN:-}" ]; then
  echo "SIGNING_KEY yok — Central imzasız paketi reddeder. DRY_RUN=1 ile yalnız paket üretebilirsiniz." >&2; exit 1
fi

rm -rf bildirim/build/maven-repo
./gradlew :bildirim:testDebugUnitTest :bildirim:publishReleasePublicationToBundleRepository --console=plain

BUNDLE="build/bildirim-android-$VERSION-bundle.zip"
mkdir -p build
( cd bildirim/build/maven-repo && rm -f "../../../$BUNDLE" && zip -qr "../../../$BUNDLE" io )
echo "Paket: $BUNDLE"; unzip -l "$BUNDLE" | tail -n +4 | head -20

if [ -n "${DRY_RUN:-}" ]; then echo "DRY_RUN — yükleme atlandı"; exit 0; fi

: "${CENTRAL_USER:?CENTRAL_USER gerekli}"; : "${CENTRAL_PASS:?CENTRAL_PASS gerekli}"
TOKEN=$(printf '%s:%s' "$CENTRAL_USER" "$CENTRAL_PASS" | base64)
# publishingType=AUTOMATIC: doğrulama geçerse kendiliğinden yayınlar (USER_MANAGED → Portal'da onay)
DEPLOY_ID=$(curl -fsS -X POST "https://central.sonatype.com/api/v1/publisher/upload?name=bildirim-android-$VERSION&publishingType=AUTOMATIC" \
  -H "Authorization: Bearer $TOKEN" -F "bundle=@$BUNDLE")
echo "Deployment: $DEPLOY_ID"
echo "Durum: curl -H 'Authorization: Bearer $TOKEN' -X POST 'https://central.sonatype.com/api/v1/publisher/status?id=$DEPLOY_ID'"
echo "Yayınlanınca (10–30 dk): https://repo1.maven.org/maven2/io/bildirim/bildirim-android/$VERSION/"
echo "Sonra ana depoda: sdk.android.released = true (RUNBOOK §9f5)"
