package egi.fts;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;


/**
 * Exception class for FTS API calls
 */
public class FileTransferServiceException extends WebApplicationException {

    private String responseBody;

    public FileTransferServiceException() {
        super();
    }

    public FileTransferServiceException(Response response, String responseBody) {
        super(response);
        this.responseBody = responseBody;
    }

    String responseBody() { return responseBody; }
}
