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
@Schema(description = "A request to transfer one or more files, with the job parameters to apply")
public class Transfer {

    private static final Logger LOG = Logger.getLogger(Transfer.class);

    @Schema(description = "One entry per file to transfer; each entry lists all known sources and all " +
                          "desired destinations")
    public List<TransferPayload> files;

    @Schema(description = "Job-wide parameters; omitted values use the engine defaults")
    public TransferParameters params;


    /**
     * Constructor
     */
    public Transfer() { this.files = new ArrayList<>(); }

    /***
     * Get all destination systems (from all payloads) in this transfer.
     * @param protocol Only return hostnames from URIs that match this protocol, pass null/empty to match all.
     * @return List of destination hostnames, null on error
     */
    public List<String> allDestinationStorages(String protocol) {
        var hosts = new HashSet<String>();

        for(var payload : this.files) {
            for(var destUri : payload.destinations) {
                // Parse the destination URI and get the hostname
                try {
                    URI uri = new URI(destUri);
                    String proto = uri.getScheme();
                    if(proto.equalsIgnoreCase(Destination.s3.toString()) ||
                       proto.equalsIgnoreCase(Destination.s3s.toString()))
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
                    MDC.put("invalidUri", destUri);
                    LOG.error(e);
                    LOG.error("Invalid destination URI");
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
    @Schema(description = "Destination storage type that selects the transfer engine and storage system")
    public enum Destination
    {
        dcache("dcache"),
        storm("storm"),
        s3("s3"),
        s3s("s3s"),
        ftp("ftp");

        // Note: Keep in sync with supported destinations in configuration file

        private final String destination;

        Destination(String destination) { this.destination = destination; }

        public String destination() { return this.destination; }
    }

    /***
     * The supported authorization models
     * Used as list of values for "auth" field of storages in configuration file
     */
    @Schema(description = "How clients authenticate to a storage system: 'token' reuses the OIDC access " +
                          "token, 'keys' needs an access key and a secret key")
    public enum AuthorizeWith
    {
        token("token"),
        keys("keys");

        private final String auth;

        AuthorizeWith(String auth) { this.auth = auth; }

        public String auth() { return this.auth; }
    }
}
