import type { GameNeed } from '../data/games';

/**
 * Believe the device, not the marketing.
 *
 * Android WebView does not support everything a desktop browser does, and a
 * games hub that links to titles which cannot render is worse than no hub. This
 * probe asks the actual engine what it can do, so the UI can mark entries as
 * "runs here" or "may not run" instead of guessing.
 */

export interface DeviceCapabilities {
  webgl: boolean;
  webgl2: boolean;
  wasm: boolean;
  gamepad: boolean;
  pointerlock: boolean;
  touch: boolean;
  hardwareConcurrency: number;
  /** Rough memory hint in GB; 0 when the browser does not report it. */
  deviceMemory: number;
  maxTextureSize: number;
  /** Overall verdict used to warn before opening a heavy game. */
  tier: 'low' | 'medium' | 'high';
}

let cached: DeviceCapabilities | null = null;

function detectWebgl(version: 1 | 2): { ok: boolean; maxTexture: number } {
  // Guard for non-DOM contexts (SSR, static preview, tests).
  if (typeof document === 'undefined') return { ok: false, maxTexture: 0 };
  try {
    const canvas = document.createElement('canvas');
    const ctx = (
      version === 2
        ? canvas.getContext('webgl2')
        : canvas.getContext('webgl') || canvas.getContext('experimental-webgl')
    ) as WebGLRenderingContext | null;
    if (!ctx) return { ok: false, maxTexture: 0 };
    const maxTexture = Number(ctx.getParameter(ctx.MAX_TEXTURE_SIZE)) || 0;
    // Release the context; a stray GL context is expensive on mobile.
    const lose = (ctx as WebGLRenderingContext & { getExtension?: (n: string) => unknown })
      .getExtension?.('WEBGL_lose_context') as { loseContext?: () => void } | null | undefined;
    lose?.loseContext?.();
    return { ok: true, maxTexture };
  } catch {
    return { ok: false, maxTexture: 0 };
  }
}

const SSR_CAPABILITIES: DeviceCapabilities = {
  webgl: false,
  webgl2: false,
  wasm: true,
  gamepad: false,
  pointerlock: false,
  touch: true,
  hardwareConcurrency: 2,
  deviceMemory: 0,
  maxTextureSize: 0,
  tier: 'medium',
};

export function probeCapabilities(): DeviceCapabilities {
  if (cached) return cached;
  // Without a DOM there is nothing to probe. Report a neutral, non-alarming
  // profile so a prerender does not claim "unavailable" for a real device.
  if (typeof document === 'undefined' || typeof navigator === 'undefined') {
    return SSR_CAPABILITIES;
  }

  const gl1 = detectWebgl(1);
  const gl2 = detectWebgl(2);

  const nav = navigator as Navigator & { deviceMemory?: number };
  const cores = typeof nav.hardwareConcurrency === 'number' ? nav.hardwareConcurrency : 2;
  const memory = typeof nav.deviceMemory === 'number' ? nav.deviceMemory : 0;

  const capabilities: DeviceCapabilities = {
    webgl: gl1.ok,
    webgl2: gl2.ok,
    wasm: typeof WebAssembly === 'object' && typeof WebAssembly.instantiate === 'function',
    // Presence of the API is not proof a pad is connected; `connected` is
    // checked live by the pad controller.
    gamepad: typeof navigator.getGamepads === 'function',
    pointerlock: 'pointerLockElement' in document,
    touch: 'ontouchstart' in window || (nav.maxTouchPoints ?? 0) > 0,
    hardwareConcurrency: cores,
    deviceMemory: memory,
    maxTextureSize: Math.max(gl1.maxTexture, gl2.maxTexture),
    tier: 'medium',
  };

  // A phone with few cores or a small GPU texture limit will struggle with
  // heavy WebGL titles, so say so before the user waits for a load.
  const strongGpu = capabilities.maxTextureSize >= 8192 && gl2.ok;
  if (!gl1.ok || cores <= 2 || capabilities.maxTextureSize < 4096) capabilities.tier = 'low';
  else if (strongGpu && cores >= 6 && (memory === 0 || memory >= 4)) capabilities.tier = 'high';

  cached = capabilities;
  return capabilities;
}

export interface GameFit {
  ok: boolean;
  /** Missing capabilities, in words the user understands. */
  missing: string[];
  /** True when it will run but may stutter on this device. */
  warning: string | null;
}

const NEED_LABELS: Record<GameNeed, string> = {
  webgl: '3D graphics (WebGL)',
  keyboard: 'a keyboard',
  gamepad: 'a gamepad',
  touch: 'touch input',
  pointerlock: 'mouse capture',
  wasm: 'WebAssembly',
};

/** Decides honestly whether a given game can run on this device. */
export function checkGameFit(needs: GameNeed[], weight: 'light' | 'medium' | 'heavy'): GameFit {
  const caps = probeCapabilities();
  const missing: string[] = [];

  for (const need of needs) {
    if (need === 'webgl' && !caps.webgl) missing.push(NEED_LABELS.webgl);
    if (need === 'wasm' && !caps.wasm) missing.push(NEED_LABELS.wasm);
    if (need === 'gamepad' && !caps.gamepad) missing.push(NEED_LABELS.gamepad);
    if (need === 'pointerlock' && !caps.pointerlock) missing.push(NEED_LABELS.pointerlock);
  }

  let warning: string | null = null;
  if (weight === 'heavy' && caps.tier !== 'high') {
    warning = 'Heavy 3D game — it may drop frames on this device.';
  } else if (weight === 'medium' && caps.tier === 'low') {
    warning = 'This device has limited graphics power, so expect a lower frame rate.';
  }

  return { ok: missing.length === 0, missing, warning };
}

/** Human-readable summary for the hub header. */
export function describeCapabilities(caps: DeviceCapabilities = probeCapabilities()): string {
  const parts: string[] = [];
  parts.push(caps.webgl2 ? 'WebGL 2' : caps.webgl ? 'WebGL 1' : 'no WebGL');
  parts.push(`${caps.hardwareConcurrency} cores`);
  if (caps.deviceMemory) parts.push(`${caps.deviceMemory} GB RAM reported`);
  parts.push(caps.gamepad ? 'gamepad API' : 'no gamepad API');
  return parts.join(' · ');
}
