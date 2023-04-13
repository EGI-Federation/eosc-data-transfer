package eosc.eu;

import io.smallrye.config.ConfigMapping;


/***
 * The HTTP configuration of the application
 */
@ConfigMapping(prefix = "quarkus.http")
public interface PortConfig {

    // The port on which the application runs
    int port();
}
