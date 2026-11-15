package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.ws.rs.core.Response;


/**
 * Details of a transfer destination
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "A destination storage type configured on this instance")
public class DestinationInfo {

    @Schema(description = "Type discriminator of this schema", example = "DestinationInfo")
    public String kind = "DestinationInfo";

    @Schema(description = "Value to pass as the 'dest' query parameter", example = "dcache")
    public String destination;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description = "Human-readable description of the destination")
    public String description;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description = "Type of credentials the storage system expects",
            enumeration = {"token", "keys"})
    public String authType;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description = "Protocol of URIs that point to this storage", example = "https")
    public String protocol;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description = "Transfer engine that handles transfers to this destination",
            example = "EGI Data Transfer")
    public String transferWith;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description = "Storage service used to browse and manage elements; absent when the " +
                          "destination does not support storage operations",
            example = "WebDAV storage")
    public String browseWith;


    /**
     * Constructor
     * @param destination The destination storage type
     * @param authType The type of credentials the storage expects
     * @param protocol The protocol of URIs that point to the storage
     * @param description Human-readable description of the destination
     */
    public DestinationInfo(String destination, String authType, String protocol, String description) {
        this.destination = destination;
        this.authType = authType;
        this.protocol = protocol;
        this.description = description;
    }

    /**
     * Extended constructor
     * @param destination The destination storage type
     * @param authType The type of credentials the storage expects
     * @param protocol The protocol of URIs that point to the storage
     * @param transferWith The transfer engine that handles this destination
     * @param browseWith The storage service that can browse this destination
     * @param description Human-readable description of the destination
     */
    public DestinationInfo(String destination, String authType, String protocol,
                           String transferWith, String browseWith, String description) {
        this.destination = destination;
        this.authType = authType;
        this.protocol = protocol;
        this.transferWith = transferWith;
        this.browseWith = browseWith;
        this.description = description;
    }

    /***
     * Convert to Response
     * @return Response object
     */
    public Response toResponse() {
        return Response.ok(this).build();
    }
}
