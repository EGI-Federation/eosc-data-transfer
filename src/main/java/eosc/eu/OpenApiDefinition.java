package eosc.eu;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;


/***
 * Declares the API groups that Swagger UI uses to organise the operations.
 * Only the group names and descriptions live here. The title and version below are fallbacks:
 * the authoritative values are configured in application.properties and application.yml, which
 * take precedence over annotations.
 */
@OpenAPIDefinition(
    info = @Info(title = "EOSC Data Transfer API", version = "1.1.107"),
    tags = {
        @Tag(name = "DOI Parsing",
             description = "Turn persistent identifiers into the list of files they point to."),
        @Tag(name = "Data Transfer",
             description = "Estimate, start, search, inspect and cancel data transfers."),
        @Tag(name = "Data Transfer User",
             description = "Information about the authenticated caller."),
        @Tag(name = "Data Storage",
             description = "List destination types and manage files and folders in a destination storage."),
        @Tag(name = "Service Info",
             description = "Version information for the running service.")
    })
public class OpenApiDefinition {
}
