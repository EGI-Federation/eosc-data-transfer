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
}
