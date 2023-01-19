package eosc.eu;


import io.smallrye.mutiny.tuples.Tuple2;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class DataStorageCredentials {

    private String username;
    private String password;

    private static final Logger LOG = Logger.getLogger(DataStorageCredentials.class);


    /***
     * Create from Base64-encoded credentials
     * @param storageAuth contains the Base64-encoded 'username:password'
     */
    public DataStorageCredentials(String storageAuth) {
        var credentials = extractStorageCredentials(storageAuth);
        if(null != credentials) {
            this.username = credentials.getItem1();
            this.password = credentials.getItem2();
        }
    }

    /***
     * Check if contains valid credentials
     * @return true if it contains valid decoded credentials
     */
    public boolean isValid() {
        return null != this.username && !this.username.isBlank() &&
               null != this.password && !this.password.isEmpty();
    }

    /**
     * Extract storage credentials from Base64-encoded string
     * @param storageAuth contains the Base64-encoded 'username:password'
     * @return Tuple containing username and password, null on error
     */
    public static Tuple2<String, String> extractStorageCredentials(String storageAuth) {
        if(null == storageAuth || storageAuth.isBlank()) {
            // No credentials?
            LOG.warn("Storage credentials missing");
            return null;
        }

        // Remove Base64 encoding
        byte[] userInfoBytes = Base64.getDecoder().decode(storageAuth);
        String userInfo = new String(userInfoBytes, StandardCharsets.UTF_8);

        // Now we should have a string of the form "username:password"
        // Split it into parts
        Pattern p = Pattern.compile("^([^:]+):(.+)$");
        Matcher m = p.matcher(userInfo);
        if(m.matches()) {
            String username = m.group(1);
            String password = m.group(2);
            return Tuple2.of(username, password);
        }

        LOG.warn("Storage credentials bad format");
        return null;
    }

    public String getUsername() { return this.username; }
    public String getPassword() {
        return this.password;
    }

    public String getAccessKey() { return this.username; }
    public String getSecretKey() {
        return this.password;
    }
}
