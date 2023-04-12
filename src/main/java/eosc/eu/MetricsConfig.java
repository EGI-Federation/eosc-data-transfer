package eosc.eu;

import io.smallrye.config.ConfigMapping;

import java.util.List;
import java.util.Optional;


/***
 * The configuration of the service QoS
 */
@ConfigMapping(prefix = "eosc.qos")
public interface MetricsConfig {

    Optional<List<Double>> quantiles();
    Optional<List<Long>> slos();
}
