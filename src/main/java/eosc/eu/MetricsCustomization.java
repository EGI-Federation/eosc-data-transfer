package eosc.eu;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Produces;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;


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

                    var builder = DistributionStatisticConfig.builder();

                    // If percentiles were specified, use them
                    if(qos.percentiles().isPresent()) {
                        var percentiles = qos.percentiles().get();
                        if(!percentiles.isEmpty())
                            builder = builder.percentiles(percentiles.stream().mapToDouble(Double::doubleValue).toArray());
                    }

                    // If SLOs were specified, use them
                    if(qos.slos().isPresent()) {
                        var slos = qos.slos().get();
                        var slosNano = new ArrayList<Double>();
                        for(var slo : slos)
                            slosNano.add((double)Duration.ofMillis(slo).toNanos()); // SLO in milliseconds

                        if(!slosNano.isEmpty())
                            builder = builder.serviceLevelObjectives(slosNano.stream().mapToDouble(Double::doubleValue).toArray());
                    }

                    return builder
                            //.percentilesHistogram(true)
                            .build()
                            .merge(config);
                }
                return config;
            }
        };
    }
}
