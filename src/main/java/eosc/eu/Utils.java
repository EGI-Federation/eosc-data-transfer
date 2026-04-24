package eosc.eu;

import org.jboss.logging.Logger;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Optional;


/***
 * Utility functions
 */
public class Utils {

    /***
     * Prevent construction
     */
    private Utils() {}

    /***
     * Load a certificate store from a resource file.
     * @param filePath File path relative to the "src/main/resource" folder
     * @param password The password for the certificate store
     * @param log is the logger to use
     * @return Loaded key store, empty optional on error
     */
    public static Optional<KeyStore> loadKeyStore(String filePath, String password, Logger log) {

        Optional<KeyStore> oks = Optional.empty();
        try {
            var classLoader = Utils.class.getClassLoader();
            var ksf = classLoader.getResourceAsStream(filePath);
            var ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(ksf, password.toCharArray());
            oks = Optional.of(ks);
        }
        catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException e) {
            log.error(e);
        }

        return oks;
    }
}
