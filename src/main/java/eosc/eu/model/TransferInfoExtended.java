package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import egi.fts.model.JobInfoExtended;
import org.eclipse.microprofile.openapi.annotations.media.Schema;


/**
 * Extended details of a transfer job.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransferInfoExtended extends TransferInfo {

    // NOTE: When adding/renaming fields, also update all translateTransferInfoFieldName() methods

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description="Job state")
    public TransferState jobState;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description="Job state as reported by the transfer service")
    public String jobStateTS;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String jobType;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, String> jobMetadata;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description="Source storage element")
    public String source_se;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String source_space_token;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description="Destination storage element")
    public String destination_se;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String destination_space_token;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String verifyChecksum;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description="True to overwrite destination files")
    public Optional<Boolean> overwrite;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<Integer> priority;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<Integer> retry;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<Integer> retryDelay;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<Integer> maxTimeInQueue;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description="True if transfer was canceled")
    public Optional<Boolean> cancel;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description="Date and time when transfer was submitted", example = "2022-10-15T20:14:22")
    public Date submittedAt;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String submittedTo;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description="Date and time when transfer ended", example = "2022-10-15T20:14:22")
    public Date finishedAt;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String reason;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String vo_name;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String user_dn;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String cred_id;


    /**
     * Construct from extended FTS job info
     * @param jie The concrete job to construct from
     */
    public TransferInfoExtended(JobInfoExtended jie) {

        super(jie);

        this.kind = "TransferInfoExtended";
        this.jobId = jie.job_id;
        this.jobStateTS = jie.job_state;
        this.jobState = TransferState.fromString(jie.job_state);
        this.jobType = jie.job_type;

        this.jobMetadata = new HashMap<>();
        if(null != jie.job_metadata && !jie.job_metadata.isEmpty())
            this.jobMetadata.putAll(jie.job_metadata);

        this.source_se = jie.source_se;
        this.source_space_token = jie.source_space_token;
        this.destination_se = jie.destination_se;
        this.destination_space_token = jie.space_token;

        this.verifyChecksum = jie.verify_checksum;

        this.overwrite = jie.job_finished.isPresent() ?
                                Optional.of(jie.overwrite_flag.get().equals("Y")) :
                                Optional.empty();
        this.priority = jie.priority;
        this.retry = jie.retry;
        this.retryDelay = jie.retry_delay;
        this.maxTimeInQueue = jie.max_time_in_queue;
        this.cancel = jie.cancel_job;

        this.submittedAt = jie.submit_time;
        this.submittedTo = jie.submit_host;
        this.finishedAt = jie.job_finished.isPresent() ? jie.job_finished.get() : null;
        this.reason = jie.reason;

        this.vo_name = jie.vo_name;
        this.user_dn = jie.user_dn;
        this.cred_id = jie.cred_id;
    }


    /***
     * The status of a transfer
     */
    @Schema(description="Will be 'succeeded' when all files transferred successfully, "+
                        "and 'failed' if all files failed to transfer.")
    public enum TransferState
    {
        unknown("unknown"),
        staging("staging"),
        submitted("submitted"),
        active("active"),
        succeeded("succeeded"),
        partial("partial"),
        failed("failed"),
        canceled("canceled");

        private final String status;

        /***
         * Construct from a string
         * @param status the desired status
         */
        TransferState(String status) { this.status = status; }

        /***
         * Build from a string
         * @param status the desired status
         * @return TransferState with specified status
         */
        public static TransferState fromString(String status) {
            final String statusLo = status.toLowerCase();
            if(statusLo.contains("staging"))
                return staging;
            else if(statusLo.contains("submitted"))
                return submitted;
            else if(statusLo.contains("ready") || statusLo.contains("active") || statusLo.contains("progress"))
                return active;
            else if(statusLo.contains("dirty"))
                return partial;
            else if(statusLo.contains("succe") || statusLo.contains("finish"))
                return succeeded;
            else if(statusLo.contains("fail"))
                return failed;
            else if(statusLo.contains("cancel"))
                return canceled;

            return unknown;
        }

        /***
         * Convert to string
         * @return status string
         */
        public String toString() {
            return this.status;
        }
    }
}
