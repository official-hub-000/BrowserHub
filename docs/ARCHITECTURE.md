# BrowserHub — Master Hybrid Android Architecture Blueprint

## 1. Architectural Vision
Most mobile browsers force a painful trade-off:
- **Chromium-based browsers** (Chrome, Edge) strip away extension support on Android and embed telemetry.
- **Privacy browsers** (Tor, Mullvad, DuckDuckGo) sacrifice ergonomics, extensions, or rich AI features.
- **AI browsers** (Arc, Edge Copilot) send your DOM to cloud servers or demand $20/month subscriptions and API keys.

**BrowserHub** eliminates every compromise through a **3-Layer Hybrid Android-Exclusive Architecture** (zero iOS WebKit restrictions, 100% focused on Android ARM64/x86_64):

```
┌─────────────────────────────────────────────────────────────────────────┐
│ LAYER 1: 120Hz Reactive Hybrid UI Shell (TypeScript + React + Tailwind) │
│ • Bottom / Top / Floating Arc Omnibox • Split-Screen Dual-Tab View      │
│ • Theme Studio (AMOLED, Custom Accents) • Mobile F12 DevTools Drawer    │
└───────────────────────────────┬─────────────────────────────────────────┘
                                │ Bidirectional Bridge (window.BrowserHubNative)
┌───────────────────────────────▼─────────────────────────────────────────┐
│ LAYER 2: Android Native Runtime (Kotlin 2.1 • SDK 35 • System WebView) │
│ • HybridBrowserEngine.kt      → Desktop 1280p Viewport + Container Jars │
│ • UniversalExtensionManager   → CRX3 Header Unpacker + AMO .xpi + GM_* │
│ • HubVpnService.kt            → Android VpnService local shield + DoH   │
└───────────────────────────────┬─────────────────────────────────────────┘
                                │ JNI / Local Zero-Cloud Memory Bus
┌───────────────────────────────▼─────────────────────────────────────────┐
│ LAYER 3: Native Shield & Zero-Key Neural Core (Rust / ONNX / GGUF)      │
│ • HubShieldEngine             → 142k EasyList/uBlock DFA + Canvas Noise │
│ • LocalNeuralEngine (0 Keys)  → Q4_K_M GGUF RAG + Bergamot Int8 MT     │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 2. How Universal Extensions ("Add Any Extension in the World") Work
Standard Android `WebView` cannot execute desktop browser extensions. BrowserHub solves this with a **4-Path Universal Extension Pipeline**:

1. **Firefox Add-ons (`.xpi` / AMO)**: Archived, inspected and unpacked by BrowserHub so you can see what a package contains. The behaviours BrowserHub can actually execute today (request filtering, injected CSS/JS, URL cleanup, background media) run natively; full third-party extension sandboxes are not available inside Android System WebView, and the store marks those entries **PREVIEW** rather than pretending.
2. **Chrome Web Store (`.crx` / Unpacked `.zip`)**: `UniversalExtensionManager.kt` parses Google's binary **CRX2 / CRX3** container (`Cr24` magic header + protobuf signature block), extracts the clean WebExtension archive, injects the `chrome.*` ↔ `browser.*` namespace shim, and mounts content & background scripts.
3. **Userscripts (`.user.js` — Tampermonkey / Violentmonkey / GreasyFork)**: Built-in `GM_addStyle`, `GM_notification`, `GM_setValue`, and `GM_xmlhttpRequest` runtime plus an in-browser live Userscript IDE.
4. **1-Tap Native Mods**: Pre-verified zero-overhead versions of uBlock Origin, Dark Reader, SponsorBlock, Return YouTube Dislike, and Cookie Auto-Rejector.

---

## 3. Zero-API-Key, Zero-Subscription Custom AI (`HubAI NeuralCore`)
- **Zero Cloud Egress**: Page text, DOM trees, passwords, and search queries never leave the user's device.
- **No API Keys or Accounts**: Works immediately out-of-the-box for free forever.
- **Dual Execution Tier**:
  - **Fast Tier (<15ms)**: Deterministic TF-IDF/TextRank centrality summarizer, BM25 local RAG Q&A with exact verbatim DOM citations, Userscript/AdBlock rule synthesizer, and lexical-phrase hybrid translation.
  - **Deep NPU/GPU Tier (Android APK)**: Quantized 4-bit `HubNano-1.5B-Instruct-Q4_K_M.gguf` via Vulkan/ARMv9 NEON + Mozilla's **Bergamot Int8** offline neural machine translation models.

---

## 4. Peak Privacy & Built-In Zero-Log VPN (`HubVpnService`)
- **Direct Native AdBlock**: Evaluates EasyList, EasyPrivacy, uBlock Filters, Peter Lowe, and Fanboy Annoyances prior to TLS socket handshake, saving 40–65% RAM and mobile data.
- **Interactive Element Zapper**: Point-and-tap cosmetic DOM eliminator.
- **URL Tracking Stripper**: Automatically strips `utm_*`, `fbclid`, `gclid`, `msclkid`, `mc_eid`, `igshid`, and `si`.
- **Android `VpnService` shield**: establishes a local tunnel interface for on-device filtering and records an Encrypted-DoH resolver preference (Quad9, Mullvad, AdGuard, Cloudflare, NextDNS). **Remote exit-node routing (WireGuard handshake, Obfs4, multi-hop Tor) is not implemented** — the node list is presented as planned locations and labelled as such in the app.
- **Anti-Fingerprinting**: Per-origin deterministic Canvas 2D bit-flipping, WebGL renderer masking, WebRTC non-proxied UDP block, Dynamic First-Party Cookie Isolation (dFPI) across 5 Containers (`Personal`, `Work`, `Banking`, `Research`, `Incognito Vault`), and a 1-tap **DuckDuckGo-style Fire Shredder**.
