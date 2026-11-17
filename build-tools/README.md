# Standalone APK toolchain

These are the exact tools used by `scripts/build-standalone-apk.sh` to produce
`release/BrowserHub-v1.0.0-release.apk` without Gradle:

| File | Purpose |
| --- | --- |
| `android.jar` | Android 34 platform stubs (compile-time only) |
| `ecj-3.45.0.jar` | Eclipse Java compiler (javac replacement) |
| `d8.jar` | Android dexer |
| `apksigner.jar` | APK Signature Scheme v2 + v3 signer/verifier |

`aapt2` and `zipalign` come from the Android SDK build-tools.

The binaries are **gitignored** (~60 MB of third-party files), so a clean checkout
restores them with one command:

```bash
./scripts/fetch-apk-tools.sh
```

That script downloads from public mirrors that need no credentials:

| File | Source |
| --- | --- |
| `android.jar` | `Sable/android-platforms` (API 34 platform stubs) |
| `ecj-3.45.0.jar` | `NorbertXYZ/eclipse-maven` Maven mirror |
| `d8.jar`, `apksigner.jar` | `@drxiaozhi/minapk` npm package (same toolchain this build targets) |
| `aapt2` (linux x86_64) | `aaptjs3` npm package |

`zipalign` is not invoked: `aapt2` emits an aligned layout and the script re-zips
before signing, which `apksigner` accepts directly.

A JDK with `javac` is **not** required — ECJ compiles the Java sources, and any JRE 17+
can run it (`jdk4py` from PyPI provides a wheel-based runtime if no JDK is installed).

Signing key: `android/browserhub-release.jks` (alias `browserhub`, RSA 4096).
It is **committed** (with the exception above in `.gitignore`) so every rebuild — on
any machine — carries the same signer identity. Without that, a fresh checkout would
generate a new key and Android would refuse to install an update over the old APK.

Current signer SHA-256: `9b3f6d8482c63984f4cd087ce2c4d2495d50747ab92565171dff4241fab349c8`
Keep that keystore backed up — Play Store updates must be signed with the same key.
