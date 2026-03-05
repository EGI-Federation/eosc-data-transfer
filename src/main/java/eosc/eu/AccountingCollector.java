package egi.eu;

import org.jboss.logging.Logger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.runtime.Startup;

import eosc.eu.ServiceConfig;


@Startup
@ApplicationScoped
public class AccountingCollector {

    private static final Logger log = Logger.getLogger(AccountingCollector.class);

    public static final String JOBSTORE_STREAM = "transfer:jobs";

    @Inject
    protected ServiceConfig serviceConfig;



}
