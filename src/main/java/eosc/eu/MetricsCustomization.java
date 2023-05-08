package eosc.eu;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Produces;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.ArrayList;


/***
 * customizes the metrics emitted by MeterRegistry instances
 * See https://quarkus.io/guides/micrometer
 */
@Singleton
public class MetricsCustomization {

    private static final Logger log = Logger.getLogger(MetricsConfig.class);

    @Inject
    MetricsConfig qos;

    /***
     * Enable histogram buckets for specific timer(s)
     * @return MeterFilter to be injected
     */
    @Produces
    @Singleton
    public MeterFilter enableHistogram() {
        return new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {

                log.debugf("Metric: %s", id.getName());

                if(id.getName().startsWith("http.server.requests")) {

                    var builder = DistributionStatisticConfig.builder();

                    // If quantiles were specified, use them
                    if(qos.quantiles().isPresent()) {
                        var quantiles = qos.quantiles().get();
                        if(!quantiles.isEmpty())
                            builder = builder.percentiles(quantiles
                                                            .stream()
                                                            .mapToDouble(Double::doubleValue)
                                                            .toArray());
                    }

                    // If SLOs were specified, use them
                    if(qos.slos().isPresent()) {
                        var slos = qos.slos().get();
                        var slosNano = new ArrayList<Double>();
                        for(var slo : slos)
                            slosNano.add((double)Duration.ofMillis(slo).toNanos()); // SLO in milliseconds

                        if(!slosNano.isEmpty())
                            builder = builder.serviceLevelObjectives(slosNano
                                                                        .stream()
                                                                        .mapToDouble(Double::doubleValue)
                                                                        .toArray());
                    }

                    return builder
                            .build()
                            .merge(config);
                }
                return config;
            }
        };
    }
}
