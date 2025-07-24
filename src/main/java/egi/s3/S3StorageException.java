package egi.s3;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;


/**
 * Exception class for S3 storage API calls
 */
public class S3StorageException extends WebApplicationException {

    private String responseBody;

    public S3StorageException() {
        super();
    }

    public S3StorageException(Response resp, String body) {
        super(resp);
        this.responseBody = body;
    }

    String responseBody() { return responseBody; }
}
