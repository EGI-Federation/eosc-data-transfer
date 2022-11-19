package parser.b2share;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;


/**
 * Exception class for B2Share API calls
 */
public class B2ShareException extends WebApplicationException {

    private String responseBody;

    public B2ShareException() {
        super();
    }

    public B2ShareException(Response resp, String body) {
        super(resp);
        this.responseBody = body;
    }

    String responseBody() { return responseBody; }
}
