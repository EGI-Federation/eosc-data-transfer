package eosc.eu;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.*;

/***
 * The configuration of the supported storage types and the transfer services that will handle them
 */
@ConfigMapping(prefix = "proxy.transfer")
public interface TransfersConfig {

    // Selects a service based on the destination
    public Map<String, DataStorageConfig> destinations();

    // Contains the details of each specific transfer service
    public Map<String, TransferServiceConfig> services();


    /***
     * The configuration of a storage system
     */
    public interface DataStorageConfig {

        @WithName("service")
        public String serviceId(); // Transfer service that handles transfers to storages of this type

        @WithName("auth")
        public String authType();

        public Optional<String> description();
    }

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
