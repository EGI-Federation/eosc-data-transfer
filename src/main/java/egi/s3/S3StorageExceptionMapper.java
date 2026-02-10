package egi.s3;

import io.netty.buffer.ByteBufInputStream;
import jakarta.annotation.Priority;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.common.jaxrs.ResponseImpl;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode;

import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.IOException;


/**
 * Custom exception mapper for S3 storage API calls, allows access to response body in case of error
 */
@Priority(Priorities.USER)
public final class S3StorageExceptionMapper implements ResponseExceptionMapper<S3StorageException> {

    private static final Logger log = Logger.getLogger(S3StorageExceptionMapper.class);

    @Override
    public S3StorageException toThrowable(Response response) {
        try {
            response.bufferEntity();
        } catch(Exception ignored) {}

        String msg = getBody(response);
        return new S3StorageException(response, msg);
    }

    @Override
    public boolean handles(int status, MultivaluedMap<String, Object> headers) {
        return status >= StatusCode.BAD_REQUEST;
    }

    private String getBody(Response response) {
        String body = "";
        if(response.hasEntity()) {
            byte[] bytes = null;
            var entity = response.getEntity();
            if(null == entity)
                entity = ((ResponseImpl) response).getEntityStream();

            if(entity instanceof  ByteArrayInputStream) {
                ByteArrayInputStream is = (ByteArrayInputStream) entity;
                int available = is.available();
                bytes = new byte[available];
                do {
                    int read = is.read(bytes, 0, available);
                    available -= read;
                } while(available > 0);
            }
            else if(entity instanceof ByteBufInputStream) {
                ByteBufInputStream is = (ByteBufInputStream) entity;
                int available = 0;
                try {
                    available = is.available();
                } catch(IOException unused) { }

                if(available > 0) {
                    bytes = new byte[available];
                    try {
                        do {
                            int read = is.read(bytes, 0, available);
                            available -= read;
                        } while(available > 0);
                    } catch(IOException ioe) {
                        log.error(ioe.getMessage());
                    }
                }
            }

            body = new String(bytes);
        }
        return body;
    }
}
