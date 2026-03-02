package eosc.eu;

import io.smallrye.config.ConfigMapping;

import java.util.Optional;


/***
 * The configuration of the service
 */
@ConfigMapping(prefix = "eosc.service")
public interface ServiceConfig {

    // Unique instance ID
    Optional<String> instance();

    // Contains details of the service accounting
    AccountingConfig accounting();

    /***
     * The configuration of service accounting
     */
    interface AccountingConfig {

        // Accounting server to send accounting records to
        Optional<String> server();

        Optional<String> installation();

        // Type of accounting record to use
        Optional<String> metric();

        Optional<String> group();
    }
}
