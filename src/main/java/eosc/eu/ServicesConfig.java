package eosc.eu;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import javax.enterprise.inject.Default;
import java.util.*;

/***
 * The configuration of the transfer services
 */
@ConfigMapping(prefix = "proxy.transfer")
public interface ServicesConfig {

    // Selects a service based on the destination
    public Map<String, String> destinations();

    // Contains the details of each specific transfer service
    public Map<String, TransferServiceConfig> services();


    /***
     * The configuration of a transfer service
     */
    public interface TransferServiceConfig {
        public String name();
        public String url();

        @WithDefault("5000")
        public int timeout(); // milliseconds

        @WithName("class")
        public String className();
    }
}
