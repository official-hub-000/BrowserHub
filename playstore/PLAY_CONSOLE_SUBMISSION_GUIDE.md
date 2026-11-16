# 🏪 Google Play Store Upload & Approval Guide — BrowserHub

BrowserHub is configured to meet all **2026 Google Play Console technical & policy requirements** (`targetSdk = 35` / Android 15, 16 KB ELF Page Alignment, AndroidX `WebViewAssetLoader`, Adaptive Icons, R8 Minification, Prominent `VpnService` In-App Disclosure, and `specialUse` Foreground Service Subtype).

---

## 1. Getting Your Signed `.AAB` (Play Store) & `.APK` (Direct Install)

### Option A — Zero-Setup Cloud Build via GitHub Actions (Recommended, No Local Android SDK Needed)
1. When you are ready to commit & push this branch to GitHub, GitHub Actions (`.github/workflows/build-android-apk.yml`) automatically runs on Ubuntu runners with JDK 17 + Gradle 8.10.2 + Android SDK 35.
2. Open your repository's **Actions** tab on GitHub → click the latest **Build BrowserHub Play Store (.AAB) & Android (.APK)** run → download:
   - **`BrowserHub-Google-PlayStore-Bundle-AAB`** (`app-playstore-release.aab` — upload this to Google Play Console)
   - **`BrowserHub-Universal-Release-APK`** (`app-universal-release.apk` — install directly on any Android phone)

### Option B — Build Locally in Android Studio or CLI
1. Download **`BrowserHub-PlayStore-Ready-Package.zip`** directly from the in-app **📲 Download & Play Store Hub** button.
2. Generate your permanent Google Play upload key:
   ```bash
   ./scripts/generate-upload-keystore.sh
   ```
3. Build both the Play Store `.aab` and direct `.apk`:
   ```bash
   ./scripts/build-playstore-release.sh
   ```

---

## 2. Copy-Paste Store Listing Metadata (Play Console → Main Store Listing)

- **App Name (max 30 chars):**
  `BrowserHub: Fast AI Browser` *(27 chars)*
- **Short Description (max 80 chars):**
  `120Hz private browser with AdBlock, free VPN, desktop extensions & local AI.` *(76 chars)*
- **Category:** `Communication` or `Tools` → **Web Browsers**
- **Graphic Assets (already generated in `playstore/`):**
  - App Icon (512 × 512 32-bit PNG): `playstore/icon-512x512.png`
  - Feature Graphic (1024 × 500 PNG): `playstore/feature-graphic-1024x500.png`

---

## 3. Copy-Paste Policy Declarations (Play Console → App Content)

### A. Privacy Policy URL
Host `public/privacy-policy.html` (e.g., via GitHub Pages or your domain) and paste the HTTPS URL.

### B. Data Safety Questionnaire
- **Does your app collect or share any of the required user data types?** → **No**
- **Is all user data encrypted in transit?** → **Yes** (HTTPS / TLS 1.3 / WireGuard / DoH)
- **Do you provide a way for users to request that their data is deleted?** → **Yes** (1-Tap in-app **Fire Wipe** button clears all local storage immediately; zero server-side accounts exist).

### C. Mandatory `VpnService` Declaration
- **Core Functionality Category:** `Web Browser / Privacy & Security VPN`
- **Declaration Text (copy-paste):**
  > *"BrowserHub is a privacy-focused mobile web browser that uses `android.net.VpnService` exclusively when initiated by the user to establish an encrypted WireGuard / DNS-over-HTTPS privacy tunnel protecting browser traffic on untrusted networks. A prominent in-app consent dialog (`MainActivity.requestConsentAndStartVpn`) is shown prior to `VpnService.prepare()`. Zero browsing traffic, DNS queries, or IP addresses are logged, stored, or monetized, and BrowserHub does not intercept or manipulate traffic from other apps."*

### D. Foreground Service (`FOREGROUND_SERVICE_SPECIAL_USE`) Justification
- **Declaration Text (copy-paste):**
  > *"Used exclusively by `HubVpnService` (`android.net.VpnService`) while the user has actively enabled the encrypted VPN tunnel so Android keeps the active network interface alive across tab switches."*
