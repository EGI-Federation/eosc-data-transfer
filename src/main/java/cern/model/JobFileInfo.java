package egi.fts.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;
import java.util.Optional;
import java.util.Date;


/**
 * Details of a file that is part of a job
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobFileInfo {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String file_state;    // https://fts3-docs.web.cern.ch/fts3-docs/docs/state_machine.html

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String source_se;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String source_surl;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String destination_se;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String destination_surl;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<Long>  file_size;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<Date> start_time;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<Date> finish_time;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String reason;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<Integer> priority;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<Integer> retry;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String checksum;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, String> file_metadata;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String vo_name;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String job_id;


    /**
     * Constructor
     */
    public JobFileInfo() {
        this.file_size = Optional.empty();
        this.start_time = Optional.empty();
        this.finish_time = Optional.empty();
        this.priority = Optional.empty();
        this.retry = Optional.empty();
    }
}
