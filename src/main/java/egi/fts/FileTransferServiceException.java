package egi.fts;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;


/**
 * Exception class for FileTransferService API calls
 */
public class FileTransferServiceException extends WebApplicationException {

    private String responseBody;

    public FileTransferServiceException() {
        super();
    }

    public FileTransferServiceException(Response resp, String body) {
        super(resp);
        this.responseBody = body;
    }

    String responseBody() { return responseBody; }
}
