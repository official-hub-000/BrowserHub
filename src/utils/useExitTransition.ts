import { useEffect, useRef, useState } from 'react';

/**
 * Keeps an element mounted for the length of its exit animation.
 *
 * Every overlay in this app animates *in* — that was always easy. Coming *out*
 * was an instant unmount, so a drawer slid open and then vanished. This hook
 * gives close the same treatment as open: the element stays in the DOM for one
 * animation duration while a reversing class runs, then unmounts.
 *
 * `requestClose` is what a close button or a scrim tap should call instead of
 * the parent's setter. The parent's setter still runs, but only after the
 * animation has finished, so the component never disappears mid-transition.
 *
 * The duration is read from the same CSS variable the entrance animation uses,
 * so the two can never drift apart.
 */

/** Longest exit animation, used when the stylesheet is unavailable. */
const FALLBACK_MS = 280;

export interface ExitTransition {
  /** True while the exit animation is playing. */
  isClosing: boolean;
  /**
   * Classes to put on the animating element: the entrance class normally, the
   * exit class while closing.
   */
  className: string;
  /** Call instead of the parent's close setter. */
  requestClose: () => void;
}

/**
 * @param onClose     the parent's setter — runs when the animation is over
 * @param enterClass  entrance class already on the element, e.g. "bh-drawer-right"
 * @param exitClass   the reversing class, e.g. "bh-drawer-right-out"
 */
export function useExitTransition(
  onClose: () => void,
  enterClass: string,
  exitClass: string,
): ExitTransition {
  const [isClosing, setIsClosing] = useState(false);
  const timerRef = useRef<number | null>(null);
  const closedRef = useRef(false);

  useEffect(() => {
    return () => {
      if (timerRef.current !== null) window.clearTimeout(timerRef.current);
    };
  }, []);

  const requestClose = () => {
    // A double tap on the scrim must not queue two closes.
    if (closedRef.current) return;
    closedRef.current = true;
    setIsClosing(true);
    timerRef.current = window.setTimeout(() => {
      timerRef.current = null;
      onClose();
    }, exitDurationMs());
  };

  return {
    isClosing,
    className: isClosing ? exitClass : enterClass,
    requestClose,
  };
}

/** The exit duration in ms, read from the stylesheet with a safe fallback. */
export function exitDurationMs(): number {
  try {
    const raw = getComputedStyle(document.documentElement).getPropertyValue('--bh-exit-ms');
    const value = parseInt(raw, 10);
    if (isFinite(value) && value > 0) return value;
  } catch {
    // No DOM (a test, or the first paint): use the fallback.
  }
  return FALLBACK_MS;
}
