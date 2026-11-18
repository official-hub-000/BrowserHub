package com.browserhub.app.media;

/**
 * Resolves a possibly-relative URI against the URL of the document that
 * declared it.
 *
 * java.net.URL's own resolve is fine for the simple cases but mishandles a few
 * that real playlists hit constantly: protocol-relative references (`//cdn/…`),
 * root-relative references (`/hls/…`), parent traversal (`../`), and query-only
 * references (`?token=…`). Getting any of these wrong produces a 404 on every
 * segment — the failure looks like "the site blocks downloads" when it is
 * actually arithmetic.
 */
public final class Resolver {

    private Resolver() {}

    public static String resolve(String baseUrl, String ref) {
        if (ref == null) return null;
        String refTrim = ref.trim();
        if (refTrim.length() == 0) return baseUrl;

        // Absolute, including non-http schemes we pass through untouched.
        if (refTrim.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) {
            return refTrim;
        }

        String scheme = scheme(baseUrl);
        if (scheme == null) return refTrim;

        // Protocol-relative.
        if (refTrim.startsWith("//")) {
            return scheme + ":" + refTrim;
        }

        String authority = authority(baseUrl);
        if (authority == null) return refTrim;

        String basePath = path(baseUrl);

        // Query-only or fragment-only reference keeps the base path.
        if (refTrim.startsWith("?")) {
            return scheme + "://" + authority + basePath + refTrim;
        }
        if (refTrim.startsWith("#")) {
            return stripQueryAndFragment(baseUrl) + refTrim;
        }

        String combined;
        if (refTrim.startsWith("/")) {
            combined = refTrim;
        } else {
            int slash = basePath.lastIndexOf('/');
            String dir = slash >= 0 ? basePath.substring(0, slash + 1) : "/";
            combined = dir + refTrim;
        }

        String normalised = normalisePath(combined);
        return scheme + "://" + authority + normalised;
    }

    /** Collapses `.` and `..` segments and duplicate slashes in a path. */
    public static String normalisePath(String path) {
        // Separate query/fragment so they are not touched.
        String suffix = "";
        int q = indexOfAny(path, '?', '#');
        if (q >= 0) {
            suffix = path.substring(q);
            path = path.substring(0, q);
        }

        boolean trailingSlash = path.endsWith("/");
        String[] parts = path.split("/");
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (String part : parts) {
            if (part.length() == 0 || ".".equals(part)) continue;
            if ("..".equals(part)) {
                if (!out.isEmpty()) out.remove(out.size() - 1);
                continue;
            }
            out.add(part);
        }
        StringBuilder sb = new StringBuilder();
        for (String part : out) {
            sb.append('/').append(part);
        }
        if (sb.length() == 0) sb.append('/');
        else if (trailingSlash && sb.charAt(sb.length() - 1) != '/') sb.append('/');
        return sb + suffix;
    }

    public static String scheme(String url) {
        if (url == null) return null;
        int i = url.indexOf("://");
        if (i <= 0) {
            // Could be "https:relative"? Treat anything before ':' that looks like
            // a scheme as one.
            int c = url.indexOf(':');
            if (c > 0 && url.substring(0, c).matches("[a-zA-Z][a-zA-Z0-9+.-]*")) {
                return url.substring(0, c);
            }
            return null;
        }
        return url.substring(0, i);
    }

    public static String authority(String url) {
        if (url == null) return null;
        int i = url.indexOf("://");
        if (i < 0) return null;
        int start = i + 3;
        int end = start;
        while (end < url.length()) {
            char c = url.charAt(end);
            if (c == '/' || c == '?' || c == '#') break;
            end += 1;
        }
        return url.substring(start, end);
    }

    public static String path(String url) {
        if (url == null) return "/";
        int i = url.indexOf("://");
        int start = i < 0 ? 0 : i + 3;
        int slash = url.indexOf('/', start);
        if (slash < 0) return "/";
        int q = indexOfAny(url, '?', '#');
        if (q >= 0 && q < slash) return "/";
        return q >= 0 ? url.substring(slash, q) : url.substring(slash);
    }

    public static String nameOf(String url) {
        String p = path(url);
        int slash = p.lastIndexOf('/');
        return slash >= 0 ? p.substring(slash + 1) : p;
    }

    /** True when the URL looks like a playlist rather than a media file. */
    public static boolean isPlaylist(String url) {
        String p = path(url).toLowerCase(java.util.Locale.US);
        return p.endsWith(".m3u8") || p.endsWith(".m3u");
    }

    private static String stripQueryAndFragment(String url) {
        int i = indexOfAny(url, '?', '#');
        return i >= 0 ? url.substring(0, i) : url;
    }

    private static int indexOfAny(String s, char a, char b) {
        int ia = s.indexOf(a);
        int ib = s.indexOf(b);
        if (ia < 0) return ib;
        if (ib < 0) return ia;
        return Math.min(ia, ib);
    }
}
