#!/usr/bin/env bash
# Creates the release signing key and keystore.properties (both git-ignored).
# KEEP the .jks file and passwords safe: without them you cannot install updates over the existing
# app, and uninstalling deletes Arthik's data (back up first).
set -euo pipefail
cd "$(dirname "$0")/.."
STORE=${1:-arthik-release.jks}
if [[ -f "$STORE" ]]; then echo "$STORE already exists, refusing to overwrite"; exit 1; fi
PASS=$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 24)
keytool -genkeypair -v -keystore "$STORE" -storetype PKCS12 -alias arthik -keyalg RSA -keysize 4096 \
  -validity 10000 -storepass "$PASS" -keypass "$PASS" -dname "CN=Arthik, O=Personal, C=IN"
cat > keystore.properties <<PROPS
storeFile=$STORE
storePassword=$PASS
keyAlias=arthik
keyPassword=$PASS
PROPS
echo "Created $STORE and keystore.properties. Store both somewhere safe (password manager)."
