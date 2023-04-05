package eosc.eu;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Produces;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.jboss.logging.Logger;


/***
 * customizes the metrics emitted by MeterRegistry instances
 * See https://quarkus.io/guides/micrometer
 */
@Singleton
public class MetricsCustomization {

    private static final Logger LOG = Logger.getLogger(MetricsConfig.class);

    @Inject
    MetricsConfig qos;

    // Enable histogram buckets for specific timer(s)
    @Produces
    @Singleton
    public MeterFilter enableHistogram() {
        return new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {

                LOG.debugf("Metric: %s", id.getName());

                if(id.getName().startsWith("http.server.requests")) {
                    return DistributionStatisticConfig.builder()
                            .percentiles(qos.percentile())                      // 95th percentile, not aggregable
                            .percentilesHistogram(true)     // histogram buckets
                            .serviceLevelObjectives(qos.slo())      // SLO in milliseconds
                            .build()
                            .merge(config);
                }
                return config;
            }
        };
    }
}
