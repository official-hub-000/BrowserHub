package com.browserhub.app.media;

/**
 * The one thing the engine cannot do for itself on YouTube.
 *
 * A scrambled stream's signature has to be produced by the player's own
 * JavaScript. That code only exists in the live page, so the engine cannot reach
 * it — and the engine must not import UI classes to try, or the download engine
 * stops being testable without an Android device.
 *
 * So the app registers an implementation here and the engine calls it. When no
 * implementation is registered (desktop tests, or a service running with no
 * activity) solving simply reports that it could not, and the caller falls back
 * to whatever the page already captured.
 */
public interface SignatureSolver {

    /**
     * Runs {@code script} in the live page and returns its raw result.
     *
     * @return the page's reply, or null when no page is available
     */
    String evaluate(String script, long timeoutMs);

    /** Installs the implementation. Passing null removes it. */
    final class Holder {
        private static volatile SignatureSolver solver;

        private Holder() {}

        public static void set(SignatureSolver implementation) {
            solver = implementation;
        }

        public static SignatureSolver get() {
            return solver;
        }

        /**
         * Asks the page to transform one scrambled signature.
         *
         * @return the transformed signature, or an empty string when unavailable
         */
        public static String solve(String scrambled) {
            SignatureSolver s = solver;
            if (s == null || scrambled == null || scrambled.length() == 0) return "";
            try {
                return YouTubeThrottle.parseSolverReply(
                        s.evaluate(YouTubeThrottle.solverScript(scrambled), 4000));
            } catch (Throwable t) {
                return "";
            }
        }
    }
}
