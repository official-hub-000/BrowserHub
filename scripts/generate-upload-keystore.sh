#!/usr/bin/env bash
set -euo pipefail

# Generates an official 4096-bit RSA Google Play Upload Keystore + android/keystore.properties
KEYSTORE_PATH="android/browserhub-upload-key.jks"
ALIAS="browserhub-upload"

if [ -f "$KEYSTORE_PATH" ]; then
  echo "Upload keystore already exists at $KEYSTORE_PATH"
  exit 0
fi

read -r -s -p "Enter a strong password for your Google Play Upload Keystore: " KS_PASS
echo ""

keytool -genkeypair -v \
  -keystore "$KEYSTORE_PATH" \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storepass "$KS_PASS" \
  -keypass "$KS_PASS" \
  -dname "CN=BrowserHub Mobile, OU=Engineering, O=BrowserHub, L=Chattogram, ST=Chittagong, C=BD"

cat > android/keystore.properties <<EOF
storeFile=browserhub-upload-key.jks
storePassword=$KS_PASS
keyAlias=$ALIAS
keyPassword=$KS_PASS
EOF

echo "✅ Created $KEYSTORE_PATH and android/keystore.properties (excluded from Git via .gitignore)!"
