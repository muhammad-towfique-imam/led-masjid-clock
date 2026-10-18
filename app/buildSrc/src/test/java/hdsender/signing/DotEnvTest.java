package hdsender.signing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DotEnvTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private Map<String, String> read(String text) throws Exception {
        File file = tmp.newFile(".env");
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
        return DotEnv.read(file);
    }

    @Test
    public void readsValuesCommentsAndQuotes() throws Exception {
        Map<String, String> env = read(
            "# release key\n"
                + "\n"
                + "HDSENDER_KEY_PASSPHRASE = five random words here\n"
                + "QUOTED=\"with = sign\"\n"
                + "SINGLE='x'\n"
                + "export EXPORTED=yes\n");
        assertEquals("five random words here", env.get("HDSENDER_KEY_PASSPHRASE"));
        assertEquals("with = sign", env.get("QUOTED"));
        assertEquals("x", env.get("SINGLE"));
        assertEquals("yes", env.get("EXPORTED"));
        assertNull(env.get("release key"));
    }

    @Test
    public void missingFileGivesNoValues() {
        assertEquals(0, DotEnv.read(new File(tmp.getRoot(), "none.env")).size());
    }
}
