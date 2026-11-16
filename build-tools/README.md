# Standalone APK toolchain

These are the exact tools used by `scripts/build-standalone-apk.sh` to produce
`release/BrowserHub-v1.0.0-release.apk` without Gradle:

| File | Purpose |
| --- | --- |
| `android.jar` | Android 34 platform stubs (compile-time only) |
| `ecj-3.45.0.jar` | Eclipse Java compiler (javac replacement) |
| `d8.jar` | Android dexer |
| `apksigner.jar` | APK Signature Scheme v2 + v3 signer/verifier |

`aapt2` and `zipalign` come from the Android SDK build-tools. This directory is
committed so a clean checkout can rebuild the standalone APK with only a JDK.

Signing key: `android/browserhub-release.jks` (alias `browserhub`, RSA 4096).
Keep that keystore backed up — Play Store updates must be signed with the same key.
