# Repository recheck — `main` @ db0ad72

Rechecked after your changes. This is the state of `main`, not the
`titanium/` branch work.

## What is there now

A real Kotlin/Compose Android app, substantially complete:

| | |
|---|---|
| Kotlin files | 45 in `app/src/main/java`, 4 test files |
| Kotlin LOC | 25,122 |
| Package / version | `com.browserhub.app`, 1.0.0 (versionCode 1) |
| SDK | minSdk 24, compile/target 36 |
| Stack | Compose + Material 3, Room, KSP, Firebase AI + App Check |
| Size in git | 18.8 MB tracked |

**It has been run.** `public/assets/aistudio/` holds 16 emulator screenshots
named `Screenshot_…_com.browserhub.app_….jpg`, showing the real home screen,
search engine picker, quick sites, AdBlock & Shields, and the Ask AI tab. So the
app is not a sketch — it compiles and runs somewhere.

## Checked and found sound

- **Package declarations** — all 45 files match their directory paths. 0 mismatches.
- **Brace balance** — all 45 main files + 4 test files balance, using a
  Kotlin-aware counter that understands raw strings, `${}` interpolation, char
  literals and comments. (An earlier naive count flagged 3 files; that was the
  counter's bug, not the code's — verified and discarded.)
- **Internal imports** — every `com.browserhub.*` import resolves to a declared
  package. 0 unresolved.
- **String resources** — all `R.string.*` references (95 defined) resolve. 0 missing.
- **Version catalog** — 27 `libs.*` references all exist; every `version.ref`
  points at a defined version. 0 dangling.
- **Manifest** — VPN service correctly declared: `BIND_VPN_SERVICE` permission,
  `android.net.VpnService` intent filter, `specialUse` FGS type with a
  `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`. Permissions the code references are all
  declared. No TODO/FIXME/stub markers in 25k lines.
- **README claims** — spot-checked `WebExtensionEngine`, `BrowserHubVpnService`,
  `BrowserDatabase`, `DnsPacketCodec`, `PageToolsEngine`; all exist and are
  substantial.

## Build blockers (4)

These stop `./gradlew assembleRelease` from succeeding. In order of what will
bite first:

1. **No `google-services.json`.** `app/build.gradle.kts` applies
   `com.google.gms.google-services`, which fails hard when the file is absent.
   The app genuinely uses Firebase AI, App Check reCAPTCHA and App Check debug,
   so this is not a stray plugin. Needs a Firebase project matching
   `com.browserhub.app`, or the plugin removed and the AI engine stubbed.
2. **No `debug.keystore`.** Both `debug` **and `release`** signingConfigs point
   at `${rootDir}/debug.keystore`. Release is signed with the debug key, which
   Play Store rejects, and the file is not in the tree.
3. **No Gradle wrapper.** `gradlew`, `gradle/wrapper/gradle-wrapper.jar` and
   `gradle-wrapper.properties` are all absent, so `./gradlew` cannot run; a
   system Gradle ≥9.3.1 must be installed instead.
4. **`.gradle/` build cache is committed** — 11 files, 7.1 MB, including a
   6.2 MB `executionHistory.bin`. There is also **no `.gitignore`** anywhere,
   which is why. This is machine-specific state that should never be in git.

## Smaller issues

- `gradle.properties` sets `org.gradle.jvmargs=-Xmx2048m`. Compose + KSP +
  Room on a 45-file codebase will usually need 3–4 GB; 2 GB risks
  `OutOfMemoryError` during Kotlin compilation.
- `ic_browserhub_logo.png` (0.8 MB) and `ic_browserhub_logo_v2.png` (1.0 MB) are
  drawables at photo resolution. Fine functionally, ~1.8 MB of APK for no reason.
- `metadata.json` declares `MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API`, matching
  the Firebase AI dependency.

## Not verified, and cannot be here

**I have not compiled it.** No JDK compiler (the JRE from PyPI has no
`jdk.compiler`), no Android SDK, no Gradle, no network route to `google.com`/
`maven.google.com` for AGP and AndroidX. So "all 45 files parse under a
Kotlin-aware brace check" is not the same as "it compiles" — a single type error
would still fail. The emulator screenshots are the strongest evidence of a
working build, and they came from your machine, not mine.

## What I did not touch

`main` is your branch and I made no commits to it. The `titanium/` work lives on
`arena/20b7da89-browserhub`. If you want the 4 blockers fixed, say which way:
supply a `google-services.json`, or strip Firebase so the app builds without it.
