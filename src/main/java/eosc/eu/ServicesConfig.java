package eosc.eu;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

import java.util.*;

/***
 * The configuration of the transfer services
 */
@ConfigMapping(prefix = "proxy.transfer")
public interface ServicesConfig {

    public String destination();

    // Selects a service based on the destination
    public Map<String, String> destinations();

    // Contains the details of each specific transfer service
    public Map<String, TransferServiceConfig> services();

    public interface TransferServiceConfig {
        public String name();
        public String url();

        @WithName("class")
        public String className();
    }
}
