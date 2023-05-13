package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;


/**
 * A new transfer job
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Transfer {

    private static final Logger LOG = Logger.getLogger(Transfer.class);

    @Schema(description="The files to be transferred")
    public List<TransferPayload> files;

    @Schema(description="Transfer parameters")
    public TransferParameters params;


    /**
     * Constructor
     */
    public Transfer() { this.files = new ArrayList<>(); }

    /***
     * Get all destination systems (from all payloads) in this transfer.
     * @param protocol Only return hostnames from URLs that match this protocol, pass null or empty to match all.
     * @return List of destination hostnames, null on error
     */
    public List<String> allDestinationStorages(String protocol) {
        var hosts = new HashSet<String>();

        for(var payload : this.files) {
            for(var destUrl : payload.destinations) {
                // Parse the destination URL and get the hostname
                try {
                    URI uri = new URI(destUrl);
                    String proto = uri.getScheme();
                    if(proto.equalsIgnoreCase(Destination.s3.toString()))
                        this.params.setS3Destinations(true);

                    if(null != protocol && !proto.equalsIgnoreCase(protocol))
                        // Destination URL does not fit the protocol we want, skip it
                        continue;

                    String host = uri.getHost().toLowerCase();
                    if(!hosts.contains(host))
                        // This is a new distinct hostname
                        hosts.add(host);
                }
                catch (URISyntaxException e) {
                    MDC.put("invalidUrl", destUrl);
                    LOG.error(e);
                    LOG.error("Invalid destination URL");
                    return null;
                }
            }
        }

        return new ArrayList<>(hosts);
    }


    /***
     * The supported transfer destinations
     * Used as list of values for "dest" parameter of the API endpoints
     */
    public enum Destination
    {
        dcache("dcache"),
        storm("storm"),
        s3("s3"),
        ftp("ftp");

        // TODO: Keep in sync with supported destinations in configuration file

        private String destination;

        Destination(String destination) { this.destination = destination; }

        public String getDestination() { return this.destination; }
    }
}
