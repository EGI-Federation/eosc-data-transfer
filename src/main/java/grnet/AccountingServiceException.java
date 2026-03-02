package grnet;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;


/**
 * Exception class for AccountingService API calls
 */
public class AccountingServiceException extends WebApplicationException {

    private String responseBody;

    public AccountingServiceException() {
        super();
    }

    public AccountingServiceException(Response resp, String body) {
        super(resp);
        this.responseBody = body;
    }

    String responseBody() { return responseBody; }
}
