package eosc.eu;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;


/***
 * The configuration of the service QoS
 */
@ConfigMapping(prefix = "eosc.qos")
public interface MetricsConfig {

    @WithDefault("100")
    public double slo(); // Request SLO in milliseconds
}
