package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.ws.rs.core.Response;


/**
 * Details of a transfer destination
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DestinationInfo {

    public String kind = "DestinationInfo";
    public String destination;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String description;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String authType;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String protocol;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String transferWith;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String browseWith;


    /**
     * Constructor
     */
    public DestinationInfo(String destination, String authType, String protocol, String description) {
        this.destination = destination;
        this.authType = authType;
        this.protocol = protocol;
        this.description = description;
    }

    /**
     * Extended constructor
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
     */
    public Response toResponse() {
        return Response.ok(this).build();
    }
}
