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
    Map<String, DataStorageConfig> destinations();

    // Contains the details of each specific transfer service
    @WithName("service")
    Map<String, TransferServiceConfig> services();


    /***
     * The configuration of a storage system
     */
    interface DataStorageConfig {

        @WithName("service")
        String serviceId(); // Transfer service that handles transfers to storages of this type

        @WithName("auth")
        String authType();

        String protocol();

        @WithDefault("false")
        boolean browse();

        Optional<String> description();
    }

    /***
     * The configuration of a transfer service
     */
    interface TransferServiceConfig {
        String name();
        String url();

        @WithDefault("5000")
        int timeout(); // milliseconds

        @WithName("class")
        String className();

        @WithName("trust-store-file")
        Optional<String> trustStoreFile();

        @WithName("trust-store-password")
        Optional<String> trustStorePassword();

        @WithName("key-store-file")
        Optional<String> keyStoreFile();

        @WithName("key-store-password")
        Optional<String> keyStorePassword();
    }
}
