package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;


/**
 * A new transfer job
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Transfer {

    public List<TransferPayload> files;
    public TransferParameters params;


    /**
     * Constructor
     */
    public Transfer() { this.files = new ArrayList<>(); }


    /***
     * The supported transfer destinations
     * Used as list of values for "dest" parameter of the API endpoints
     */
    public enum Destination
    {
        dcache("dcache"),
        s3("s3"),
        ftp("ftp");

        // TODO: Keep in sync with supported destinations in configuration file

        private String destination;

        Destination(String destination) { this.destination = destination; }

        public String getDestination() { return this.destination; }
    }
}
