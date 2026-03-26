package eosc.eu;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

import java.util.Optional;


/***
 * The configuration of the service
 */
@ConfigMapping(prefix = "eosc.service")
public interface ServiceConfig {

    // Unique instance ID
    Optional<String> instance();

    // Contains details of the OIDC client
    CheckinConfig checkin();

    // Contains details of the service accounting
    AccountingConfig accounting();

    /***
     * Configuration for OIDC integration
     */
    interface CheckinConfig {

        // OIDC server
        String server();

    }

    /***
     * Configuration for accounting usage
     */
    interface AccountingConfig {

        // Accounting server to send accounting records to
        Optional<String> server();

        Optional<String> installation();

        // Type of accounting record to use
        Optional<String> metric();

        Optional<String> group();

        @WithName("check-transfer-status")
        int pollInterval();
    }
}
