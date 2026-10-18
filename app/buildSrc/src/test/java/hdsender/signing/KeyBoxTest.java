package hdsender.signing;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import org.junit.Test;

public class KeyBoxTest {

    private final char[] passphrase = "correct horse battery staple".toCharArray();
    private final byte[] secret = "keystore bytes \u0000ÿ".getBytes(StandardCharsets.ISO_8859_1);

    @Test
    public void sealedDataOpensWithTheSamePassphrase() throws Exception {
        byte[] sealed = KeyBox.seal(secret, passphrase, new SecureRandom());
        assertArrayEquals(secret, KeyBox.open(sealed, passphrase));
    }

    @Test
    public void sealedDataStartsWithTheFormatMarker() throws Exception {
        byte[] sealed = KeyBox.seal(secret, passphrase, new SecureRandom());
        assertEquals("HDKEY1", new String(sealed, 0, 6, StandardCharsets.US_ASCII));
    }

    @Test
    public void wrongPassphraseIsRejected() throws Exception {
        byte[] sealed = KeyBox.seal(secret, passphrase, new SecureRandom());
        assertThrows(GeneralSecurityException.class, () -> KeyBox.open(sealed, "wrong".toCharArray()));
    }

    @Test
    public void changedDataIsRejected() throws Exception {
        byte[] sealed = KeyBox.seal(secret, passphrase, new SecureRandom());
        sealed[sealed.length - 1] ^= 1;
        assertThrows(GeneralSecurityException.class, () -> KeyBox.open(sealed, passphrase));
    }

    @Test
    public void fileWithoutMarkerIsRejected() {
        assertThrows(GeneralSecurityException.class, () -> KeyBox.open(new byte[64], passphrase));
    }

    @Test
    public void eachSealUsesNewRandomSaltAndNonce() throws Exception {
        byte[] a = KeyBox.seal(secret, passphrase, new SecureRandom());
        byte[] b = KeyBox.seal(secret, passphrase, new SecureRandom());
        assertFalse(Arrays.equals(a, b));
    }

    @Test
    public void bundleKeepsTheKeystoreAndThePasswords() {
        byte[] keystore = new byte[4428];
        new SecureRandom().nextBytes(keystore);
        ReleaseKey key = new ReleaseKey(keystore, "store pass", "wfsender", "key=pass#1");
        ReleaseKey back = ReleaseKey.fromBytes(key.toBytes());
        assertArrayEquals(keystore, back.keystore);
        assertEquals("store pass", back.storePassword);
        assertEquals("wfsender", back.keyAlias);
        assertEquals("key=pass#1", back.keyPassword);
    }
}
