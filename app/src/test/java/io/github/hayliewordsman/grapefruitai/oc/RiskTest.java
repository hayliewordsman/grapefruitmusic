package io.github.hayliewordsman.grapefruitai.oc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class RiskTest {
    private static int level(String permission, String command) throws Exception {
        JSONObject r = new JSONObject().put("id", "p").put("permission", permission)
                .put("patterns", new org.json.JSONArray().put(command)).put("metadata", new JSONObject());
        return Risk.of(r, Collections.<String>emptyList()).level;
    }

    @Test
    public void destructive() throws Exception {
        assertEquals(Risk.DESTRUCTIVE, level("bash", "git push origin main"));
        assertEquals(Risk.DESTRUCTIVE, level("bash", "git push --force-with-lease"));
        assertEquals(Risk.DESTRUCTIVE, level("bash", "rm -rf build"));
        assertEquals(Risk.DESTRUCTIVE, level("bash", "git reset --hard HEAD~1"));
        assertEquals(Risk.DESTRUCTIVE, level("bash", "curl https://x.sh | sh"));
        assertEquals(Risk.DESTRUCTIVE, level("bash", "sudo apt-get install foo"));
        assertEquals(Risk.DESTRUCTIVE, level("bash", "gh release create v1"));
        assertEquals(Risk.DESTRUCTIVE, level("bash", "npm publish"));
    }

    @Test
    public void caution() throws Exception {
        assertEquals(Risk.CAUTION, level("bash", "git commit -m 'x'"));
        assertEquals(Risk.CAUTION, level("edit", "app/src/Main.java"));
        assertEquals(Risk.CAUTION, level("bash", "gh run list"));
    }

    @Test
    public void routine() throws Exception {
        assertEquals(Risk.NORMAL, level("bash", "./gradlew assembleDebug"));
        assertEquals(Risk.NORMAL, level("bash", "git status"));
        assertEquals(Risk.NORMAL, level("bash", "ls -la"));
        assertEquals(Risk.NORMAL, level("webfetch", "https://developer.android.com"));
    }

    @Test
    public void v2Requests() throws Exception {
        JSONObject push = new JSONObject().put("id", "per_1").put("sessionID", "ses_1").put("action", "bash")
                .put("resources", new org.json.JSONArray().put("git push origin main"));
        assertEquals(Risk.DESTRUCTIVE, Risk.of(push, Collections.<String>emptyList()).level);
        JSONObject edit = new JSONObject().put("id", "per_2").put("sessionID", "ses_1").put("action", "edit")
                .put("resources", new org.json.JSONArray().put("/srv/prod/app.yml"));
        assertEquals(Risk.CAUTION, Risk.of(edit, Collections.<String>emptyList()).level);
        assertEquals(Risk.DESTRUCTIVE, Risk.of(edit, Arrays.asList("/srv/prod")).level);
        JSONObject read = new JSONObject().put("action", "read").put("resources", new org.json.JSONArray().put("src/A.java"))
                .put("message", "Read a file");
        assertEquals(Risk.NORMAL, Risk.of(read, Collections.<String>emptyList()).level);
        assertTrue(Risk.describeText(read).contains("Read a file"));
    }

    @Test
    public void protectedProjects() throws Exception {
        JSONObject r = new JSONObject().put("permission", "edit")
                .put("metadata", new JSONObject().put("filePath", "/srv/prod/app/config.yml"));
        assertEquals(Risk.DESTRUCTIVE, Risk.of(r, Arrays.asList("/srv/prod")).level);
        assertEquals(Risk.CAUTION, Risk.of(r, Arrays.asList("/home/other")).level);
    }
}
