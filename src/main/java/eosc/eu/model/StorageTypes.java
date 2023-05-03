package eosc.eu.model;

import jakarta.ws.rs.core.Response;
import java.util.ArrayList;


/**
 * Collection of all supported destination storage types
 */
public class StorageTypes extends ArrayList<StorageInfo> {

    /***
     * Constructor
     */
    public StorageTypes() {
        super();
    }

    /***
     * Construct with specified capacity
     */
    public StorageTypes(int initialCapacity) {
        super(initialCapacity);
    }

    /***
     * Convert to Response
     */
    public Response toResponse() {
        return Response.ok(this).build();
    }

}
