package eosc.eu;

import io.smallrye.config.ConfigMapping;

import java.util.List;
import java.util.Optional;


/***
 * The configuration of the service QoS
 */
@ConfigMapping(prefix = "eosc.qos")
public interface MetricsConfig {

    /***
     * List of quantiles (percentiles) to create histogram bucket(s) for
     * @return Quantiles
     */
    Optional<List<Double>> quantiles();

    /***
     * List of service level objectives (SLOs) to create histogram bucket(s) for
     * @return SLOs [milliseconds]
     */
    Optional<List<Long>> slos();
}
