package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import javax.ws.rs.core.Response;


/**
 * Details of a storage element (file or folder)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageInfo {

    public String kind = "StorageInfo";
    public boolean canBrowse = true;
    public String destination;

    /**
     * Constructor
     */
    public StorageInfo(String destination, boolean canBrowse) {
        this.destination = destination;
        this.canBrowse = canBrowse;
    }

    /***
     * Convert to Response
     */
    public Response toResponse() {
        return Response.ok(this)
                .status(canBrowse ? Response.Status.OK : Response.Status.NOT_IMPLEMENTED)
                .build();
    }
}
