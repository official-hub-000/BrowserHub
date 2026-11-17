import React, { useState } from 'react';
import {
  Download,
  CheckCircle2,
  X,
  Smartphone,
  ShieldCheck,
  PackageCheck,
  ExternalLink,
  FileArchive,
  Sparkles,
} from 'lucide-react';

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

interface PlayStoreDownloadModalProps {
  deferredInstallPrompt: BeforeInstallPromptEvent | null;
  onClose: () => void;
}

const PLAY_STORE_CHECKS = [
  'Signed Release APK (v1 + v2 + v3 RSA-4096 APK Signature Verified)',
  'Target SDK 35 (Android 15) & Min SDK 26 (Android 8.0 — Android 16+)',
  'Native Dual-WebView Engine (Browses ANY real website on your phone!)',
  'Native Network Ad & Tracker Blocker (shouldInterceptRequest)',
  'Mandatory Prominent Disclosure Dialog prior to Android VpnService',
  'Real Desktop Mode + Pinch-to-Zoom + Dark Reader & Cookie-Wall Killer',
  '100% Offline Zero-Key On-Device AI Summarizer & Page Q&A',
  'Real Incognito Screenshot Lock (FLAG_SECURE) + 1-Tap Fire Shredder',
  'Android System DownloadManager Integration for Real File Downloads',
  'Adaptive Cyber-Shield Launcher Icon + Hardware Back Gesture Support',
  '512×512 Store Icon, 1024×500 Feature Graphic & Privacy Policy Ready',
  'Play Store .AAB Gradle Project & Submission Kit Included',
];

export const PlayStoreDownloadModal: React.FC<PlayStoreDownloadModalProps> = ({
  deferredInstallPrompt,
  onClose,
}) => {
  const [installNotice, setInstallNotice] = useState<string | null>(null);

  const handleInstantPwaInstall = async () => {
    if (deferredInstallPrompt) {
      await deferredInstallPrompt.prompt();
      const choice = await deferredInstallPrompt.userChoice;
      if (choice.outcome === 'accepted') {
        setInstallNotice('BrowserHub installed to your device home screen!');
      }
    } else {
      setInstallNotice(
        'Tip: Tap the big green "Download Release APK" button above to install the native Android APK directly on your physical phone!'
      );
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-3">
      <div className="w-full max-w-2xl rounded-3xl bg-slate-950 border border-emerald-500/40 shadow-2xl flex flex-col max-h-[90vh] overflow-hidden">
        {/* Header */}
        <div className="p-4 border-b border-slate-800 bg-gradient-to-r from-emerald-950/70 via-cyan-950/50 to-slate-950 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-10 h-10 rounded-2xl bg-emerald-500/20 border border-emerald-400/40 text-emerald-300 flex items-center justify-center">
              <PackageCheck className="w-5 h-5" />
            </div>
            <div>
              <div className="text-sm font-extrabold text-white flex items-center gap-2">
                BrowserHub v1.0.0 — Android Release Download
                <span className="text-[10px] px-2 py-0.5 rounded-full bg-emerald-500 text-slate-950 font-black">
                  SIGNED .APK READY
                </span>
              </div>
              <div className="text-[11px] text-slate-300">
                Install directly on your physical Android phone (Android 8.0 – Android 16)
              </div>
            </div>
          </div>
          <button aria-label="Close" title="Close"
            onClick={onClose}
            className="p-1.5 rounded-xl hover:bg-slate-800 text-slate-400 cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Scrollable Content */}
        <div className="overflow-y-auto p-4 space-y-4">
          {/* HERO BUTTON: REAL SIGNED ANDROID APK */}
          <a
            href="./api/download/apk"
            download="BrowserHub-v1.0.0-release.apk"
            className="w-full rounded-2xl bg-gradient-to-r from-emerald-400 via-emerald-500 to-cyan-400 text-slate-950 p-4 flex items-center justify-between gap-3 hover:opacity-95 transition shadow-xl shadow-emerald-500/25"
          >
            <div className="space-y-1">
              <div className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-black/15 text-[10px] font-black uppercase">
                <Smartphone size={11} className="shrink-0" />
                <span>Physical Phone Release • RSA-4096 Signed (v1+v2+v3)</span>
              </div>
              <div className="text-base sm:text-lg font-black">
                Download BrowserHub-v1.0.0-release.apk
              </div>
              <div className="text-xs font-semibold opacity-85">
                Tap to download &amp; install directly on your Android phone — browses real websites with Native AdBlock, VPN, Extensions, Desktop Mode &amp; Offline AI!
              </div>
            </div>
            <div className="w-12 h-12 rounded-2xl bg-black/15 flex items-center justify-center shrink-0">
              <Download className="w-6 h-6" />
            </div>
          </a>

          {/* Secondary Play Store Project & Store Assets Downloads */}
          <div className="grid sm:grid-cols-2 gap-3">
            <a
              href="./api/download/playstore-kit"
              download="BrowserHub-PlayStore-Android-Bundle.zip"
              className="rounded-2xl bg-slate-900 border border-slate-700 hover:border-emerald-400 text-white p-3.5 flex flex-col justify-between gap-2 transition"
            >
              <div className="flex items-center justify-between text-emerald-300">
                <FileArchive className="w-5 h-5" />
                <span className="text-[10px] font-bold uppercase px-2 py-0.5 rounded-full bg-emerald-500/15">
                  Source + Gradle (.ZIP)
                </span>
              </div>
              <div>
                <div className="text-xs font-bold">
                  Android Studio &amp; Play Store .AAB Bundle Kit
                </div>
                <div className="text-[11px] text-slate-400 mt-0.5">
                  Full source project for Play Console App Bundle (<code>.aab</code>) uploads.
                </div>
              </div>
            </a>

            <a
              href="./api/download/store-assets"
              download="BrowserHub-PlayConsole-Assets-And-Policies.zip"
              className="rounded-2xl bg-slate-900 border border-slate-700 hover:border-cyan-400 text-white p-3.5 flex flex-col justify-between gap-2 transition"
            >
              <div className="flex items-center justify-between text-cyan-300">
                <Download className="w-5 h-5" />
                <span className="text-[10px] font-bold uppercase px-2 py-0.5 rounded-full bg-cyan-500/15">
                  Play Console Pack (.ZIP)
                </span>
              </div>
              <div>
                <div className="text-xs font-bold">
                  Play Store Graphics &amp; Policy Kit
                </div>
                <div className="text-[11px] text-slate-400 mt-0.5">
                  512×512 Icon PNG, 1024×500 Feature Graphic &amp; VpnService Declarations.
                </div>
              </div>
            </a>
          </div>

          {/* Optional WebPWA + Privacy Policy */}
          <div className="rounded-2xl bg-slate-900/90 border border-slate-800 p-3 flex flex-wrap items-center justify-between gap-2">
            <div className="text-xs text-slate-300 flex items-center gap-2">
              <Smartphone className="w-4 h-4 text-purple-400 shrink-0" />
              <span>Allow &quot;Install unknown apps&quot; when opening the downloaded <code>.apk</code> on Android.</span>
            </div>
            <div className="flex items-center gap-2">
              <a
                href="./privacy-policy.html"
                target="_blank"
                rel="noreferrer"
                className="px-2.5 py-1 rounded-xl bg-slate-800 hover:bg-slate-700 text-[11px] text-cyan-300 font-semibold inline-flex items-center gap-1"
              >
                Privacy Policy <ExternalLink className="w-3 h-3" />
              </a>
              <button
                onClick={handleInstantPwaInstall}
                className="px-2.5 py-1 rounded-xl bg-purple-500/20 border border-purple-400/40 text-purple-200 text-[11px] font-bold inline-flex items-center gap-1 cursor-pointer"
              >
                <Sparkles className="w-3 h-3" />
                PWA Mode
              </button>
            </div>
          </div>

          {installNotice && (
            <div className="rounded-xl bg-purple-500/15 border border-purple-400/40 p-3 text-xs text-purple-200">
              {installNotice}
            </div>
          )}

          {/* 12-Point Release Verification */}
          <div className="rounded-2xl bg-slate-900/60 border border-slate-800 p-3.5 space-y-2">
            <div className="text-xs font-bold text-emerald-300 flex items-center gap-1.5">
              <ShieldCheck className="w-4 h-4" />
              v1.0.0 Physical Device Release Capabilities
            </div>
            <div className="grid sm:grid-cols-2 gap-1.5">
              {PLAY_STORE_CHECKS.map((item, idx) => (
                <div key={idx} className="flex items-start gap-1.5 text-[11px] text-slate-300">
                  <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0 mt-0.5" />
                  <span>{item}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
