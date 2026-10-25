package com.grapefruitmusic.source;

/** Folder-path helpers for excluded folders. Pure Java (unit tested). */
public final class Folders {
    private Folders() {
    }

    /** " /sdcard/Music// " -> "/sdcard/Music". Returns null for something that isn't an absolute path. */
    public static String normalize(String path) {
        if (path == null) return null;
        String p = path.trim();
        while (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        if (!p.startsWith("/") || p.length() < 2) return null;
        return p;
    }

    /** SQL LIKE pattern (with ESCAPE '\') matching every file inside folder, at any depth. */
    public static String likeInside(String folder) {
        String escaped = folder.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return escaped + "/%";
    }

    public static boolean isInside(String path, String folder) {
        return path != null && path.startsWith(folder + "/");
    }

    /** "/sdcard/Music/Song.mp3" -> "/sdcard/Music". */
    public static String parent(String path) {
        int slash = path == null ? -1 : path.lastIndexOf('/');
        return slash > 0 ? path.substring(0, slash) : null;
    }

    /** Last path segment: "/sdcard/Music/Rock" -> "Rock". */
    public static String name(String folder) {
        int slash = folder.lastIndexOf('/');
        return slash >= 0 && slash < folder.length() - 1 ? folder.substring(slash + 1) : folder;
    }

    /** Path relative to the phone's storage root when inside it: "/sdcard/Music/Rock" -> "Music/Rock". */
    public static String relative(String folder, String storageRoot) {
        if (storageRoot != null && isInside(folder, storageRoot)) return folder.substring(storageRoot.length() + 1);
        return folder;
    }
}
