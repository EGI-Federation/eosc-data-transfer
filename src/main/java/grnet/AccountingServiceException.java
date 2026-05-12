package grnet;

import cern.FileTransferServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;


/**
 * Exception class for AccountingService API calls
 */
public class AccountingServiceException extends WebApplicationException {

    private String responseBody;
    private AccountingError error;

    public AccountingServiceException() {
        super();
    }

    public AccountingServiceException(Response resp, String body) {
        super(resp);
        this.responseBody = body;

        if(null != this.responseBody) {
            var mapper = new ObjectMapper();

            try {
                this.error = mapper.readValue(body, AccountingServiceException.AccountingError.class);
            } catch(JsonProcessingException e) {}
        }
    }

    public String responseBody() { return responseBody; }
    public String errorDetail()  { return null != error ? error.message : super.getMessage(); }

    static class AccountingError {
        public String message;
        public int  code;
    }
}
