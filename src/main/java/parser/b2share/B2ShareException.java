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

    public B2ShareException(Response response, String responseBody) {
        super(response);
        this.responseBody = responseBody;
    }

    String responseBody() { return responseBody; }
}
