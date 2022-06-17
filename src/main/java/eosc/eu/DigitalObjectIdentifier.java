package eosc.eu;

import io.smallrye.mutiny.Uni;
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
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestQuery;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import eosc.eu.model.*;


@Path("/")
@SecuritySchemes(value = {
    @SecurityScheme(securitySchemeName = "none"),
    @SecurityScheme(securitySchemeName = "bearer",
            type = SecuritySchemeType.HTTP,
            scheme = "Bearer")} )
@Produces(MediaType.APPLICATION_JSON)
public class DigitalObjectIdentifier {

    @Inject
    ParsersConfig config;

    private static final Logger LOG = Logger.getLogger(DigitalObjectIdentifier.class);


    /**
     * Prepare a parser service that can parse the specified DOI.
     *
     * @param params Will receive the parser
     * @param doi The DOI for a data set
     * @return true on success, updates field "parser"
     */
    @PostConstruct
    private boolean getParser(ActionParameters params, String doi) {

        LOG.debug("Selecting parser...");

        if (null != params.parser)
            return true;

        if(null == doi || doi.isEmpty()) {
            LOG.error("No DOI specified");
            return false;
        }

        try {
            // Try each registered parser
            for(var pKey : this.config.parsers().keySet()) {
                var parserConfig = this.config.parsers().get(pKey);

                // Get the class of the parser
                var classType = Class.forName(parserConfig.className());
                params.parser = (ParserService)classType.getDeclaredConstructor().newInstance();
                if(params.parser.initParser(parserConfig)) {
                    LOG.infof("Trying parser <%s>", params.parser.getParserName());

                    if(params.parser.canParseDOI(doi)) {
                        params.source = pKey;
                        return true;
                    }
                }

                params.source = null;
                params.parser = null;
            }
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

        // None of the configured parsers supports this DOI
        return false;
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
    public Uni<Response> parseDOI(@RestHeader("Authorization") String auth,
                                  @Parameter(description = "The DOI to parse", required = true, example = "https://doi.org/12.3456/zenodo.12345678")
                                  @RestQuery String doi) {

        LOG.infof("Parse DOI %s", doi);

        Uni<Response> result = Uni.createFrom().nullItem()

                .chain(unused -> {
                    // Pick parser service that recognizes this DOI
                    var params = new ActionParameters();
                    if (!getParser(params, doi)) {
                        // Could not find suitable parser
                        return Uni.createFrom().failure(new TransferServiceException("invalidParserConfig"));
                    }

                    return Uni.createFrom().item(params);
                })
                .chain(params -> {
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
