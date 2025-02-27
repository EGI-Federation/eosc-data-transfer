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
public interface TransfersStoragesConfig {

    // Selects a service (and optionally a storage system) based on the destination
    @WithName("destination")
    Map<String, DestinationConfig> destinations();

    // Contains the details of each specific transfer service
    @WithName("service")
    Map<String, TransferServiceConfig> services();

    // Contains the details of each supported storage system type
    @WithName("storage")
    Map<String, StorageSystemConfig> storages();


    /***
     * The configuration of a storage system
     */
    interface DestinationConfig {

        @WithName("service")
        String serviceId(); // Transfer service that handles transfers to storages of this type

        @WithName("storage")
        Optional<String> storageId(); // Storage system that can handle manipulating storage elements

        @WithName("auth")
        String authType();

        String protocol();

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

    /***
     * The configuration of a storage system type where storage element manipulation is supported
     */
    interface StorageSystemConfig {
        String name();

        @WithName("class")
        String className();
    }
}
