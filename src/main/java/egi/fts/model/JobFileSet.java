package egi.fts.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

import eosc.eu.model.TransferPayload;


/**
 * A source file to be transferred, available from multiple locations, and mulple destination where to transfer the file.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobFileSet {

    // Multiple sources for the same file
    public List<String> sources;

    // Multiple destination where to transfer the same file
    public List<String> destinations;

    @Schema(title="Overrides transfer priority for this set of files, from 1 to 5, 1 being the lowest priority")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public int priority = 0;

    @Schema(title="User defined checksum in the form 'algorithm:value'")
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
