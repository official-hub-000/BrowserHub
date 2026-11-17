/**
 * Gamepad support.
 *
 * There are two different things people mean by "play with a gamepad", and the
 * app has to be honest about which one it can do:
 *
 *  1. A game that already speaks the Gamepad API (EmulatorJS, many itch.io
 *     titles). On Android WebView the API exists but is often empty because the
 *     platform does not surface raw gamepad devices to WebView. Nothing in a web
 *     page can fix that — it needs native plumbing.
 *  2. A game that only understands keyboard or touch. For those we can act as a
 *     translator: read the pad and synthesise key events or touch points.
 *
 * This module implements both the detection and the translator, and reports
 * clearly which mode is actually available instead of pretending.
 */

export type GamepadMode = 'native' | 'emulated' | 'unavailable';

export interface GamepadStatus {
  mode: GamepadMode;
  /** Number of pads the browser currently reports. */
  connected: number;
  /** Friendly names, e.g. "Xbox Wireless Controller". */
  names: string[];
  /** Why the mode is what it is, in plain words. */
  reason: string;
}

/** Standard Gamepad API button indices. */
const BUTTON = {
  a: 0,
  b: 1,
  x: 2,
  y: 3,
  lb: 4,
  rb: 5,
  lt: 6,
  rt: 7,
  back: 8,
  start: 9,
  ls: 10,
  rs: 11,
  up: 12,
  down: 13,
  left: 14,
  right: 15,
} as const;

/** Default keyboard mapping used in emulation mode. */
const DEFAULT_KEYMAP: Record<number, string> = {
  [BUTTON.a]: 'Space',
  [BUTTON.b]: 'Escape',
  [BUTTON.x]: 'KeyE',
  [BUTTON.y]: 'KeyF',
  [BUTTON.up]: 'ArrowUp',
  [BUTTON.down]: 'ArrowDown',
  [BUTTON.left]: 'ArrowLeft',
  [BUTTON.right]: 'ArrowRight',
  [BUTTON.start]: 'Enter',
  [BUTTON.back]: 'Backspace',
  [BUTTON.lb]: 'KeyQ',
  [BUTTON.rb]: 'KeyR',
};

function readPads(): (Gamepad | null)[] {
  try {
    if (typeof navigator.getGamepads !== 'function') return [];
    const pads = navigator.getGamepads();
    return Array.from(pads).filter((p): p is Gamepad => p !== null && p.connected);
  } catch {
    return [];
  }
}

/**
 * Reports what is genuinely possible. `native` means a real pad is visible to
 * the page; `emulated` means we can still translate input for keyboard games.
 */
export function getGamepadStatus(): GamepadStatus {
  if (typeof navigator === 'undefined' || typeof navigator.getGamepads !== 'function') {
    return {
      mode: 'unavailable',
      connected: 0,
      names: [],
      reason: 'This browser engine does not expose the Gamepad API.',
    };
  }

  const pads = readPads();
  if (pads.length > 0) {
    return {
      mode: 'native',
      connected: pads.length,
      names: pads.map((p, i) => p?.id || `Controller ${i + 1}`),
      reason: 'A controller is connected and visible to the page.',
    };
  }

  // The API exists but reports nothing. On Android WebView that usually means
  // the platform is not passing devices through, which is outside the page's
  // control — so we say so rather than showing a dead toggle.
  return {
    mode: 'emulated',
    connected: 0,
    names: [],
    reason:
      'No controller is visible to the page yet. Press any button on the pad — ' +
      'if it stays invisible, this WebView is not passing the device through, ' +
      'and only keyboard-style translation can work.',
  };
}

type Unlisten = () => void;

/**
 * Translates a connected pad into key events, for games that only read the
 * keyboard. Returns an unsubscribe function.
 */
export function emulateKeysFromGamepad(
  target: Document | HTMLElement = document,
  keymap: Record<number, string> = DEFAULT_KEYMAP
): Unlisten {
  if (typeof window === 'undefined' || typeof navigator.getGamepads !== 'function') {
    return () => undefined;
  }

  let raf = 0;
  const previous = new Map<number, boolean>();

  const send = (type: 'keydown' | 'keyup', code: string) => {
    const event = new KeyboardEvent(type, {
      code,
      key: code.replace(/^Key/, '').toLowerCase(),
      bubbles: true,
      cancelable: true,
    });
    const el = (target as Document).body ?? (target as HTMLElement);
    el?.dispatchEvent(event);
  };

  const tick = () => {
    const pads = readPads();
    const pad = pads[0];
    if (pad) {
      Object.entries(keymap).forEach(([indexStr, code]) => {
        const index = Number(indexStr);
        const pressed = Boolean(pad.buttons[index]?.pressed);
        const was = previous.get(index) ?? false;
        if (pressed !== was) {
          previous.set(index, pressed);
          send(pressed ? 'keydown' : 'keyup', code);
        }
      });
    }
    raf = requestAnimationFrame(tick);
  };

  raf = requestAnimationFrame(tick);
  return () => cancelAnimationFrame(raf);
}

/**
 * Reads the left stick as a direction, which some games need as movement rather
 * than as arrow keys.
 */
export function readStick(pad: Gamepad, stick: 'left' | 'right' = 'left'): { x: number; y: number } {
  const offset = stick === 'left' ? 0 : 2;
  const x = pad.axes[offset] ?? 0;
  const y = pad.axes[offset + 1] ?? 0;
  // Ignore drift below the usual deadzone.
  const dead = 0.18;
  return {
    x: Math.abs(x) < dead ? 0 : x,
    y: Math.abs(y) < dead ? 0 : y,
  };
}

/** Human name for a button index, for the on-screen test panel. */
export function buttonLabel(index: number): string {
  const found = Object.entries(BUTTON).find(([, v]) => v === index);
  return found ? found[0].toUpperCase() : `B${index}`;
}
