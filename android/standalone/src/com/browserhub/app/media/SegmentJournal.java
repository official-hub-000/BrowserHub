package com.browserhub.app.media;

/**
 * Works out the byte offset that corresponds to a segment boundary.
 *
 * Resume needs "truncate the file to where segment N ended". The only way to
 * know that without trusting a journal blindly is to re-derive it from the
 * playlist: the init section plus every segment before N. Segment sizes are not
 * in the playlist, so this offset can only be used together with the journal,
 * which records the count *after* those bytes were flushed.
 *
 * This class exists as its own unit because it is the arithmetic the resume path
 * depends on, and because getting it wrong silently corrupts a resumable
 * download — the kind of bug that only shows up as "the video plays in the
 * middle but the start is static".
 */
public final class SegmentJournal {

    private SegmentJournal() {}

    /**
     * Bytes that precede segment {@code index} in the merged file.
     *
     * Only meaningful when the caller also knows the written size of every
     * preceding segment. When those are unknown (the normal case) this returns a
     * value derived from the durations as a *lower* bound, and the caller must
     * use the journal count instead. Returns 0 when nothing can be established.
     */
    public static long offsetOf(HlsPlaylist playlist, int index) {
        if (playlist == null || playlist.segments == null) return 0;
        if (index < 0 || index > playlist.segments.size()) return 0;
        // Sizes are unknown here; a boundary offset is only safe when the
        // playlist declares byte ranges, which pins every segment precisely.
        long offset = 0;
        boolean allRanged = true;
        for (int i = 0; i < index; i++) {
            HlsPlaylist.Segment seg = playlist.segments.get(i);
            if (seg.rangeLength <= 0) {
                allRanged = false;
                break;
            }
            offset += seg.rangeLength;
        }
        if (!allRanged) return 0;
        if (playlist.mapUri != null && playlist.mapLength > 0) offset += playlist.mapLength;
        return offset;
    }

    /**
     * The rule the journal depends on: a segment count is only usable when the
     * file is at least as large as the bytes that count implies. Callers pass the
     * count recorded on disk and the actual file length.
     */
    public static boolean isConsistent(long fileLength, long impliedOffset) {
        if (impliedOffset <= 0) return false;
        return fileLength >= impliedOffset;
    }
}
