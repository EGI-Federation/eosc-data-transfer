package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.ws.rs.core.Response;
import java.util.Optional;


/**
 * Details of a destination storage type
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageInfo {

    public String kind = "StorageInfo";
    public String destination;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String description;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String authType;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String protocol;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Optional<Boolean> canBrowse;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String transferWith;


    /**
     * Constructor
     */
    public StorageInfo(String destination, String authType, String protocol, String description) {
        this.destination = destination;
        this.authType = authType;
        this.protocol = protocol;
        this.description = description;
    }

    /**
     * Extended constructor
     */
    public StorageInfo(String destination, String authType, String protocol, boolean canBrowse, String transferWith, String description) {
        this.destination = destination;
        this.authType = authType;
        this.protocol = protocol;
        this.canBrowse = Optional.of(canBrowse);
        this.transferWith = transferWith;
        this.description = description;
    }

    /***
     * Convert to Response
     */
    public Response toResponse() {
        return Response.ok(this).build();
    }
}
