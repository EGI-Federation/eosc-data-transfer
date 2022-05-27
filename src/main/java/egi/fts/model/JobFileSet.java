package egi.fts.model;

import eosc.eu.model.TransferPayload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;


/**
 * A set of files to transfer, includes a source file list and a destination file list
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobFileSet {

    public List<String> sources;
    public List<String> destinations;

    @Schema(title="Overrides transfer priority for this set of files, from 1 to 5, 1 being the lowest priority")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public int priority = 0;

    @Schema(title="User defined checksum in the form algorithm:value")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String checksum;


    /**
     * Constructor
     */
    public JobFileSet() {
        this.sources = new ArrayList<>();
        this.destinations = new ArrayList<>();
    }

    /**
     * Construct from generic transfer file set, makes deep copy
     */
    public JobFileSet(TransferPayload payload) {
        this.sources = new ArrayList<>(payload.sources.size());
        this.destinations = new ArrayList<>(payload.destinations.size());
        this.sources.addAll(payload.sources);
        this.destinations.addAll(payload.destinations);
    }
}
