package eosc.eu.model;

import egi.fts.model.JobFileSet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;


/**
 * A set of files to transfer, includes a source file list and a destination file list
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransferPayload {

    public List<String> sources;
    public List<String> destinations;

    @Schema(title="Overrides transfer priority for this set of files, from 1 to 5, 1 being the lowest priority")
    public int priority = 0;

    @Schema(title="User defined checksum in the form algorithm:value")
    public String checksum;


    /**
     * Constructor
     */
    public TransferPayload() {
        this.sources = new ArrayList<>();
        this.destinations = new ArrayList<>();
    }

    /**
     * Check if valid (any files to transfer, source and destination the same size)
     */
    public boolean isValid() {
        if(null == this.sources || null == this.destinations ||
           this.sources.isEmpty() || this.sources.size() != this.destinations.size())
            // Something is wrong
            return false;

        return true;
    }
}
