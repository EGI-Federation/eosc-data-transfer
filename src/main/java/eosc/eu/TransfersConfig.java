package eosc.eu;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.Map;
import java.util.Optional;


/***
 * The configuration of the supported storage types and the transfer services that will handle them
 */
@ConfigMapping(prefix = "eosc.transfer")
public interface TransfersConfig {

    // Selects a service based on the destination
    @WithName("destination")
    public Map<String, DataStorageConfig> destinations();

    // Contains the details of each specific transfer service
    @WithName("service")
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

        @WithName("trust-store-file")
        public Optional<String> trustStoreFile();

        @WithName("trust-store-password")
        public Optional<String> trustStorePassword();

        @WithName("key-store-file")
        public Optional<String> keyStoreFile();

        @WithName("key-store-password")
        public Optional<String> keyStorePassword();
    }
}
