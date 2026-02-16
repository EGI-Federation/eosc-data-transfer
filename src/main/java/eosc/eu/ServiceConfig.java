package eosc.eu;

import io.smallrye.config.ConfigMapping;

import java.util.Optional;


/***
 * The configuration of the Data Transfer service
 */
@ConfigMapping(prefix = "eosc.service")
public interface ServiceConfig {

    Optional<String> instance();
}
