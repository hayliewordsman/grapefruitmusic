package io.github.hayliewordsman.grapefruitai.oc;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * How careful to be with an OpenCode permission request. The server enforces permissions; this
 * app adds a second, explicit confirmation for destructive operations, Git pushes and anything
 * touching a project the user marked as protected, and never offers "always allow" for them.
 */
public final class Risk {
    public static final int NORMAL = 0;
    public static final int CAUTION = 1;
    public static final int DESTRUCTIVE = 2;

    public final int level;
    public final List<String> reasons;

    private Risk(int level, List<String> reasons) {
        this.level = level;
        this.reasons = reasons;
    }

    private static final String[][] DESTRUCTIVE_PATTERNS = {
            {"\\bgit\\s+push\\b", "Git push (publishes commits to a remote)"},
            {"\\bgit\\s+reset\\s+--hard\\b", "git reset --hard (discards changes)"},
            {"\\bgit\\s+clean\\b", "git clean (deletes untracked files)"},
            {"\\bgit\\s+(checkout|restore)\\s+(--\\s+)?\\.", "Discards working-tree changes"},
            {"\\bgit\\s+branch\\s+-[dD]\\b", "Deletes a Git branch"},
            {"\\bgit\\s+(rebase|filter-branch|filter-repo)\\b", "Rewrites Git history"},
            {"\\bgit\\s+commit\\b.*--amend", "Rewrites the last commit"},
            {"\\bgit\\s+tag\\s+-d\\b", "Deletes a tag"},
            {"--force\\b|\\s-f\\b.*\\bpush\\b|\\bpush\\b.*\\s-f\\b", "Forced operation"},
            {"(^|[;&|\\s])rm\\s", "Deletes files (rm)"},
            {"\\b(rmdir|unlink|shred|truncate)\\b", "Deletes or truncates files"},
            {"\\bfind\\b.*-delete\\b", "Deletes files (find -delete)"},
            {"\\b(mkfs|dd|fdisk|parted|wipefs)\\b", "Low-level disk operation"},
            {"\\b(chmod|chown)\\s+-R\\b", "Recursive permission change"},
            {"\\bsudo\\b|\\bsu\\s", "Runs as another user (sudo/su)"},
            {"(curl|wget)[^|]*\\|\\s*(ba|z)?sh\\b", "Runs a downloaded script"},
            {"\\b(drop|truncate)\\s+(table|database|schema)\\b", "Drops database objects"},
            {"\\bgh\\s+(release|repo)\\s+(delete|create|edit)\\b", "Changes GitHub releases or repositories"},
            {"\\bgh\\s+(pr\\s+merge|workflow\\s+(run|disable)|secret)\\b", "Changes GitHub state (merge/workflow/secrets)"},
            {"\\b(npm|yarn|pnpm)\\s+publish\\b|\\btwine\\s+upload\\b|\\bcargo\\s+publish\\b", "Publishes a package"},
            {"\\b(kubectl|helm)\\s+(delete|uninstall)\\b|\\bterraform\\s+(apply|destroy)\\b", "Changes infrastructure"},
            {"\\bdocker\\s+(rm|rmi|system\\s+prune|volume\\s+rm)\\b", "Deletes Docker data"},
            {"\\bsystemctl\\s+(stop|disable|restart|mask)\\b|\\b(shutdown|reboot)\\b", "Changes system services"},
            {">\\s*/(etc|usr|bin|boot|var)/", "Writes to system paths"},
    };

    private static final String[][] CAUTION_PATTERNS = {
            {"\\bgit\\s+(commit|merge|cherry-pick|revert|stash|checkout|switch|tag)\\b", "Changes Git state"},
            {"\\b(npm|pip|pip3|apt|apt-get|gem|cargo)\\s+(install|uninstall|remove)\\b", "Installs or removes packages"},
            {"\\b(mv|cp)\\s", "Moves or copies files"},
            {"\\bgh\\s", "Uses the GitHub CLI"},
    };

    /**
     * Classifies a permission request ({permission, patterns, metadata}). protectedPaths are
     * directories the user marked as protected in Backend settings.
     */
    public static Risk of(JSONObject request, List<String> protectedPaths) {
        List<String> reasons = new ArrayList<String>();
        int level = NORMAL;
        String permission = request.optString("permission", request.optString("type", ""));
        String text = describeText(request);
        String lower = text.toLowerCase(Locale.US);
        for (String[] p : DESTRUCTIVE_PATTERNS) {
            if (Pattern.compile(p[0], Pattern.CASE_INSENSITIVE).matcher(lower).find()) {
                level = DESTRUCTIVE;
                if (!reasons.contains(p[1])) reasons.add(p[1]);
            }
        }
        if (level == NORMAL) {
            for (String[] p : CAUTION_PATTERNS) {
                if (Pattern.compile(p[0], Pattern.CASE_INSENSITIVE).matcher(lower).find()) {
                    level = CAUTION;
                    if (!reasons.contains(p[1])) reasons.add(p[1]);
                }
            }
        }
        if (permission.equals("external_directory")) {
            level = Math.max(level, CAUTION);
            reasons.add("Outside the project directory");
        }
        if (permission.equals("edit") || permission.equals("write") || permission.equals("patch")) {
            level = Math.max(level, CAUTION);
            reasons.add("Edits files");
        }
        if (protectedPaths != null) {
            for (String root : protectedPaths) {
                String r = root.trim();
                if (r.length() > 0 && text.contains(r)) {
                    level = DESTRUCTIVE;
                    reasons.add("Touches protected project " + r);
                }
            }
        }
        return new Risk(level, reasons);
    }

    /** The request's command, patterns and file paths as one string, for matching and display. */
    public static String describeText(JSONObject request) {
        StringBuilder b = new StringBuilder();
        JSONArray patterns = request.optJSONArray("patterns");
        if (patterns == null) {
            Object p = request.opt("pattern");
            if (p instanceof JSONArray) patterns = (JSONArray) p;
            else if (p instanceof String) b.append(p).append('\n');
        }
        for (int i = 0; patterns != null && i < patterns.length(); i++) b.append(patterns.optString(i)).append('\n');
        JSONObject meta = request.optJSONObject("metadata");
        if (meta != null) {
            for (String k : new String[]{"command", "filePath", "filepath", "path", "url", "description", "diff"}) {
                String v = meta.optString(k, "");
                if (v.length() > 0) b.append(v).append('\n');
            }
        }
        String title = request.optString("title", "");
        if (title.length() > 0) b.append(title).append('\n');
        return b.toString().trim();
    }

    public String label() {
        switch (level) {
            case DESTRUCTIVE: return "Destructive or publishing action";
            case CAUTION: return "Changes your project";
            default: return "Routine";
        }
    }
}
