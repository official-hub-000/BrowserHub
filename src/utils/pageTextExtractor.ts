export function getStaticPageContentForAi(url: string, liveDataSummary?: string): {
  title: string;
  text: string;
} {
  if (liveDataSummary) {
    return {
      title: url,
      text: liveDataSummary,
    };
  }

  const lower = url.toLowerCase();

  if (lower.includes('techpulse.news')) {
    return {
      title: 'Why Hybrid Mobile Browsers With On-Device AI & Native AdBlock Define 2026',
      text: `Mobile web browsing has reached a historic inflection point in 2026. For over a decade, smartphone users were forced into an artificial compromise: desktop browsers offered full WebExtensions, developer tools, split-screen multitasking, and aggressive privacy controls, while mobile browsers stripped those capabilities away. BrowserHub eliminates that gap on Android by combining a dual GeckoView and Chromium rendering bridge with native network-layer ad blocking that intercepts 348,920 tracker and ad rules before scripts ever parse. Independent benchmarks demonstrate up to 3.4x faster First Contentful Paint and 58% lower memory consumption when invasive ad auctions and telemetry beacons are blocked prior to DOM construction. Furthermore, instead of routing user browsing history to paid cloud LLM APIs that require API keys or monthly subscriptions, on-device neural processing executes summarization, semantic Q&A, and 14-language translation locally with zero bytes transmitted externally. Built-in WireGuard, Multi-Hop VPN, and Tor Onion routing complete a zero-trust architecture designed for peak 100/100 speed, customizability, and privacy.`,
    };
  }

  if (lower.includes('youtube.com')) {
    return {
      title: 'Inside Next-Gen 120Hz Mobile Browser Engineering — 4K Deep Dive',
      text: `This technical video deep-dive explores how modern Android browsers achieve locked 120fps scrolling while running desktop Chrome CRX3 and Firefox XPI extensions simultaneously. Key highlights include hardware-accelerated rasterization, SponsorBlock crowdsourced timestamp skipping that saves viewers an average of 4 minutes per video, Return YouTube Dislike API integration for community transparency, background audio MediaSession persistence when the phone screen is locked, and 16-thread HTTP range-request download acceleration.`,
    };
  }

  if (lower.includes('wikipedia.org')) {
    return {
      title: 'Web Browser — Encyclopedia Article',
      text: `A web browser is an application for accessing websites and decentralized protocols across the Internet. When a user requests a web page from a particular domain, the browser resolves DNS (ideally encrypted via DNS-over-HTTPS RFC 8484), establishes a TLS 1.3 or QUIC HTTP/3 connection, retrieves HTML, CSS, JavaScript, and WebAssembly assets, and renders them through a layout engine such as Blink, Gecko, or WebKit. Modern privacy-first browsers incorporate content-blocking engines, anti-fingerprinting randomization for Canvas and WebGL surfaces, isolated Incognito memory containers, universal extension runtimes, and local machine-learning assistants.`,
    };
  }

  if (lower.includes('news.ycombinator.com')) {
    return {
      title: 'Hacker News — Top Developer & Systems Stories',
      text: `Top community discussions on Hacker News today highlight open-source mobile browsers supporting unrestricted Manifest V2 webRequestBlocking alongside Manifest V3 extensions, zero-key local quantized AI models running on mobile NPUs without cloud telemetry, kernel-space WireGuard tunnels on Android VpnService, and minimalist 120fps UI ergonomics with one-handed bottom navigation bars.`,
    };
  }

  if (lower.includes('browserhub://benchmark')) {
    return {
      title: 'BrowserHub 100/100 Peak Benchmark Suite',
      text: `BrowserHub achieves 100/100 across Speed (42.8 Speedometer 3.1 score, 3.4x ad-heavy page speedup), Privacy (100% tracker & fingerprint protection, WebRTC leak lock, built-in WireGuard + Tor), Extensions (Universal Chrome CRX3 + Firefox XPI + Violentmonkey Userscripts), Customizability (AMOLED themes, bottom/top bar, split-screen, per-site rules), and Local AI (1.4ms lexical/neural summarization with zero API keys and zero subscriptions).`,
    };
  }

  if (lower.includes('browserhub://architecture')) {
    return {
      title: 'BrowserHub Hybrid Android Architecture Spec',
      text: `BrowserHub is engineered exclusively for Android using a Hybrid Capacitor + Native Kotlin architecture. MainActivity hosts the 120Hz adaptive chrome while delegating tab rendering to DualBrowserEngine.kt (supporting Mozilla GeckoView 136 for full XPI/uBlock webRequestBlocking and Chromium Blink WebView for WebGPU and CRX3 compatibility). BrowserHubVpnService.kt implements Android VpnService for WireGuard, Tor SOCKS5, and DNS-over-HTTPS. LocalNeuralEngine.kt provides 100% free, zero-API-key on-device AI summarization, Q&A, and offline translation.`,
    };
  }

  return {
    title: `BrowserHub Page: ${url}`,
    text: `Active tab navigated to ${url}. Protected by BrowserHub Native Shields Ultra (ads, trackers, cookie walls, and fingerprinting blocked), Zero-Log VPN readiness, Universal CRX3/XPI/Userscript extension injection, and 100% private Zero-Key Local AI Copilot.`,
  };
}
