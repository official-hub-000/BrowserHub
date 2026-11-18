/**
 * Viewport sync coalescing.
 *
 * `BrowserHubNative.syncWebViewport` is a JNI call with seventeen arguments.
 * On the native side it re-measures and re-lays-out the WebView that hosts the
 * actual page, so it is not a cheap signal — it is a layout barrier. The shell
 * used to call it on every animation frame, on a 900 ms interval that ran for
 * the whole session, and from an effect with no dependency array, which meant
 * once per React render (once per token while an AI answer streamed).
 *
 * None of those extra calls carried new information. This module turns the
 * arguments into one comparable value so the caller can skip the crossing when
 * nothing it would receive has changed, while still forwarding every genuine
 * change — a navigation, a zoom step, a shield toggle, a different custom sheet.
 *
 * The separator matters: joining without one would make ["ab", "c"] and
 * ["a", "bc"] produce the same string, and a real change would be swallowed.
 */

/** Everything the native viewport call receives, minus formatting details. */
export interface ViewportSyncInputs {
  url: string;
  topCss: number;
  bottomCss: number;
  height: number;
  isDesktop: boolean;
  adblockOn: boolean;
  darkReaderOn: boolean;
  cookieKillerOn: boolean;
  customCss: string;
  zoom: number;
  findQuery: string;
  showNativeWeb: boolean;
  blockTrackers: boolean;
  antiFingerprinting: boolean;
  httpsEverywhere: boolean;
  backgroundPlay: boolean;
  hasCustomCss: boolean;
}

const SEP = '\u0001';

/** One comparable string covering every argument of the native call. */
export function viewportPayload(inputs: ViewportSyncInputs): string {
  return [
    inputs.url,
    inputs.topCss,
    inputs.bottomCss,
    inputs.height,
    inputs.isDesktop ? 1 : 0,
    inputs.adblockOn ? 1 : 0,
    inputs.darkReaderOn ? 1 : 0,
    inputs.cookieKillerOn ? 1 : 0,
    inputs.customCss,
    inputs.zoom,
    inputs.findQuery,
    inputs.showNativeWeb ? 1 : 0,
    inputs.blockTrackers ? 1 : 0,
    inputs.antiFingerprinting ? 1 : 0,
    inputs.httpsEverywhere ? 1 : 0,
    inputs.backgroundPlay ? 1 : 0,
    inputs.hasCustomCss ? 1 : 0,
  ].join(SEP);
}

/**
 * A stateful gate. Returns `true` the first time it sees a payload and then
 * only when the payload differs from the last one it let through — so the
 * caller can be invoked as often as it likes without paying the JNI cost.
 */
export function makeViewportGate() {
  let last: string | null = null;
  return {
    shouldSync(inputs: ViewportSyncInputs): boolean {
      const next = viewportPayload(inputs);
      if (next === last) return false;
      last = next;
      return true;
    },
    /** Forget the last payload, forcing the next call through. */
    reset() {
      last = null;
    },
  };
}
