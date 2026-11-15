package eosc.eu;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.WebClient;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.annotation.security.PermitAll;

import java.util.concurrent.Flow;


@Path("/")
@Tag(name = "Service Info", description = "Version information for the running service.")
public class ServiceInfo {

    private static final Logger log = Logger.getLogger(ServiceInfo.class);
    private static WebClient client;
    private Flow.Subscription subscription;

    @Inject
    MeterRegistry registry;

    @Inject
    ParsersConfig config;

    @Inject
    PortConfig port;


    /***
     * Construct with Vertx
     * @param vertx Injected Vertx instance
     */
    @Inject
    ServiceInfo(Vertx vertx) {
        this.client = WebClient.create(vertx);
    }


    /**
     * Return the version of the running service.
     * @return API Response, wraps the version string or an ActionError entity
     */
    @GET
    @Path("/version")
    @PermitAll
    @Operation(operationId = "version",  summary = "Return service version information",
               description = "Returns the version of the running service as plain text.")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Version string",
                    content = @Content(mediaType = MediaType.TEXT_PLAIN,
                    schema = @Schema(implementation = String.class, example = "1.1.107"))),
    })
    public Uni<Response> version() {

        log.info("Querying version");

        var params = new ActionParameters();
        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Load module version from configuration
                final var config = ConfigProvider.getConfig();
                var version = config.getValue("quarkus.smallrye-openapi.info-version", String.class);

                // Got version information
                MDC.put("version", version);
                log.info("Got version");
                return Uni.createFrom().item(Response.ok(version).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Cannot load version");
                return new ActionError(e).toResponse();
            });

        return result;
    }
}
