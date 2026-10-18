package hdsender.signing;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ReleaseSigningTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final String PASSPHRASE = "five random words for testing";
    private final Map<String, String> noEnv = Collections.emptyMap();

    private File project() throws Exception {
        File dir = tmp.newFolder("app");
        new File(dir, "keystore").mkdirs();
        Files.write(new File(dir, "keystore/release.jks").toPath(), new byte[] {1, 2, 3, 4, 5});
        write(new File(dir, "keystore.properties"),
            "storeFile=keystore/release.jks\nstorePassword=sp\nkeyAlias=wfsender\nkeyPassword=kp\n");
        return dir;
    }

    private static void write(File file, String text) throws Exception {
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void encryptThenLoadFromTheEncryptedFile() throws Exception {
        File dir = project();
        write(new File(dir, ".env"), "HDSENDER_KEY_PASSPHRASE=" + PASSPHRASE + "\n");
        ReleaseSigning.encrypt(dir, noEnv);
        assertTrue(new File(dir, "keystore-backup/release-key.enc").isFile());

        // A fresh clone: no keystore folder and no keystore.properties.
        new File(dir, "keystore/release.jks").delete();
        new File(dir, "keystore.properties").delete();
        ReleaseSigning.Result result = ReleaseSigning.load(dir, noEnv);
        assertEquals("sp", result.storePassword);
        assertEquals("wfsender", result.keyAlias);
        assertEquals("kp", result.keyPassword);
        assertEquals(new File(dir, "build/signing/release.jks"), result.storeFile);
        assertArrayEquals(new byte[] {1, 2, 3, 4, 5}, Files.readAllBytes(result.storeFile.toPath()));
    }

    @Test
    public void environmentVariableWinsOverDotEnv() throws Exception {
        File dir = project();
        ReleaseSigning.encrypt(dir, Collections.singletonMap(ReleaseSigning.VAR, PASSPHRASE));
        write(new File(dir, ".env"), "HDSENDER_KEY_PASSPHRASE=wrong passphrase value\n");
        ReleaseSigning.Result result = ReleaseSigning.load(dir, Collections.singletonMap(ReleaseSigning.VAR, PASSPHRASE));
        assertEquals("sp", result.storePassword);
    }

    @Test
    public void wrongPassphraseStopsTheBuildWithAClearMessage() throws Exception {
        File dir = project();
        ReleaseSigning.encrypt(dir, Collections.singletonMap(ReleaseSigning.VAR, PASSPHRASE));
        write(new File(dir, ".env"), "HDSENDER_KEY_PASSPHRASE=this is not the passphrase\n");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> ReleaseSigning.load(dir, noEnv));
        assertTrue(e.getMessage(), e.getMessage().contains("HDSENDER_KEY_PASSPHRASE"));
    }

    @Test
    public void withoutPassphraseItUsesKeystoreProperties() throws Exception {
        File dir = project();
        ReleaseSigning.Result result = ReleaseSigning.load(dir, noEnv);
        assertEquals(new File(dir, "keystore/release.jks"), result.storeFile);
        assertEquals("kp", result.keyPassword);
    }

    @Test
    public void withoutAnyKeyThereIsNoSigning() throws Exception {
        assertNull(ReleaseSigning.load(tmp.newFolder("empty"), noEnv));
    }

    @Test
    public void encryptNeedsALongPassphrase() throws Exception {
        File dir = project();
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> ReleaseSigning.encrypt(dir, Collections.singletonMap(ReleaseSigning.VAR, "short")));
        assertTrue(e.getMessage(), e.getMessage().contains("16"));
    }

    @Test
    public void encryptNeedsAPassphrase() throws Exception {
        File dir = project();
        assertThrows(IllegalStateException.class, () -> ReleaseSigning.encrypt(dir, noEnv));
    }
}
