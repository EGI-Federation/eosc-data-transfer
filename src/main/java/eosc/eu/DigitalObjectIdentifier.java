package eosc.eu;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.tuples.Tuple2;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.security.SecuritySchemes;
import org.eclipse.microprofile.openapi.models.headers.Header;
import org.jboss.logging.Logger;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.WebClient;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestQuery;
import org.reactivestreams.Subscription;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.lang.reflect.InvocationTargetException;

import eosc.eu.model.*;
import parser.ParserHelper;


@RequestScoped
@Path("/")
@SecuritySchemes(value = {
    @SecurityScheme(securitySchemeName = "none"),
    @SecurityScheme(securitySchemeName = "bearer",
            type = SecuritySchemeType.HTTP,
            scheme = "Bearer")} )
@Produces(MediaType.APPLICATION_JSON)
public class DigitalObjectIdentifier {

    private static final Logger LOG = Logger.getLogger(DigitalObjectIdentifier.class);
    private static WebClient client;
    private Subscription subscription;

    @Inject
    ParsersConfig config;


    /***
     * Construct with Vertx
     */
    @Inject
    DigitalObjectIdentifier(Vertx vertx) {
        this.client = WebClient.create(vertx);
    }

    /**
     * Prepare a parser service that can parse the specified DOI.
     *
     * @param params Will receive the parser
     * @param auth The access token needed to call the parser
     * @param doi The DOI for a data set
     * @return true on success, updates field "parser"
     */
    @PostConstruct
    private Uni<Boolean> getParser(String auth, ActionParameters params, String doi) {

        if(null == doi || doi.isBlank()) {
            LOG.error("No DOI specified");
            return Uni.createFrom().item(false);
        }

        LOG.debugf("Selecting parser for DOI %s", doi);

        // Now try each parser until we find one that can parse the specified DOI
        ParserHelper helper = new ParserHelper(this.client);
        Uni<Boolean> result = Multi.createFrom().iterable(this.config.parsers().keySet())

            .onSubscription().invoke(sub -> {
                // Save the subscription, so we can cancel later
                this.subscription = sub;
            })
            .onItem().transformToUniAndConcatenate(parserId -> {
                // Instantiate parser
                var parserConfig = this.config.parsers().get(parserId);
                LOG.debugf("Trying parser %s", parserConfig.name());

                ParserService parser = null;

                try {
                    // Get the class of the parser
                    var classType = Class.forName(parserConfig.className());

                    // Instantiate parser
                    parser = (ParserService)classType.getDeclaredConstructor(String.class).newInstance(parserId);
                }
                catch (ClassNotFoundException e) {
                    LOG.error(e.getMessage());
                }
                catch (NoSuchMethodException e) {
                    LOG.error(e.getMessage());
                }
                catch (InstantiationException e) {
                    LOG.error(e.getMessage());
                }
                catch (InvocationTargetException e) {
                    LOG.error(e.getMessage());
                }
                catch (IllegalAccessException e) {
                    LOG.error(e.getMessage());
                }
                catch (IllegalArgumentException e) {
                    LOG.error(e.getMessage());
                }

                return null != parser ? Uni.createFrom().item(parser) : Uni.createFrom().nullItem(); // Multi discards null items
            })
            .onItem().transformToUniAndConcatenate(parser -> {
                // TODO: Remove this check once we can cancel the Multi stream
                // Check if this parser can handle the DOI
                if(null == params.parser)
                    return parser.canParseDOI(auth, doi, helper);

                // Once we selected a parser that supports the DOI, skip all others
                return Uni.createFrom().item(Tuple2.of(false, parser));
            })
            .onItem().transformToUniAndConcatenate(parserInfo -> {
                // Got info about a parser's support for our DOI
                var supported = parserInfo.getItem1();
                if(supported) {
                    // DOI supported
                    params.parser = parserInfo.getItem2();

                    // Initialize parser
                    var parserConfig = this.config.parsers().get(params.parser.getId());
                    var initOK = (null != parserConfig) ? params.parser.init(parserConfig) : false;

                    // Cancel iteration of parsers
//                    if(null != this.subscription) {
//                        var sub = this.subscription;
//                        this.subscription = null;
//                        sub.cancel();
//                    }

                    LOG.infof("Using parser %s for DOI %s", params.parser.getName(), doi);
                    return Uni.createFrom().item(initOK);
                }

                return Uni.createFrom().item(false);
            })
            .onFailure().invoke(e -> {
                LOG.errorf("Failed to query configured parsers for support of DOI %s", doi);
            })
            .collect()
            .in(BooleanAccumulator::new, (acc, supported) -> {
                acc.accumulateAny(supported);
            })
            .onItem().transform(BooleanAccumulator::get);

        return result;
    }

    /**
     * Parse Digital Object Identifier at specified URL and return list of files.
     *
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @GET
    @Path("/parser")
    @SecurityRequirement(name = "bearer")
    @Operation(operationId = "parse",  summary = "Extract source files from DOI")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = StorageContent.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "404", description="Source not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class)))
    })
    public Uni<Response> parseDOI(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                  @Parameter(description = "The DOI to parse", required = true, example = "https://doi.org/12.3456/zenodo.12345678")
                                  @RestQuery String doi) {

        LOG.infof("Parse DOI %s", doi);

        var params = new ActionParameters();
        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Pick parser service that recognizes this DOI
                return getParser(auth, params, doi);
            })
            .chain(canParse -> {
                if (!canParse) {
                    // Could not find suitable parser
                    LOG.errorf("No parser can handle DOI %s", doi);
                    return Uni.createFrom().failure(new TransferServiceException("doiNotSupported"));
                }

                // Parse DOI and get source files
                return params.parser.parseDOI(auth, doi);
            })
            .chain(sourceFiles -> {
                // Got list of source files
                LOG.infof("Got %d source files", sourceFiles.count);

                // Success
                return Uni.createFrom().item(Response.ok(sourceFiles).build());
            })
            .onFailure().recoverWithItem(e -> {
                LOG.errorf("Failed to parse DOI %s", doi);
                return new ActionError(e, Tuple2.of("doi", doi)).toResponse();
            });

        return result;
    }
}
