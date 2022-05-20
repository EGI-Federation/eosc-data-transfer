package egi.fts;

import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;
import org.jboss.resteasy.reactive.common.jaxrs.ResponseImpl;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;


/**
 * Custom exception mapper for FTS API calls, allows access to response body in case of error
 */
@Priority(Priorities.USER)
public class FileTransferServiceExceptionMapper implements ResponseExceptionMapper<FileTransferServiceException> {

    @Override
    public FileTransferServiceException toThrowable(Response response) {
        try {
            response.bufferEntity();
        } catch(Exception ignored) {
        }

        String msg = getBody(response);
        return new FileTransferServiceException(response, msg);
    }

    @Override
    public boolean handles(int status, MultivaluedMap<String, Object> headers) {
        return status >= 400;
    }

    private String getBody(Response response) {
        String body = "";
        if(response.hasEntity()) {
            ByteArrayInputStream is = (ByteArrayInputStream)response.getEntity();
            if(null == is)
                is = (ByteArrayInputStream)((ResponseImpl)response).getEntityStream();
            byte[] bytes = new byte[is.available()];
            is.read(bytes, 0, is.available());
            body = new String(bytes);
        }
        return body;
    }
}
