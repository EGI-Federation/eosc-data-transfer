package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.Date;

import cern.model.JobFileInfo;
import cern.model.JobInfoExtended;

import eosc.eu.model.TransferPayloadInfo.FileDetails;
import eosc.eu.model.TransferPayloadInfo.FileState;


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
    public Map<String, String> jobMetadata;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description="Source storage system")
    public String sourceSS;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description="Destination storage system")
    public String destinationSS;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public boolean verifyChecksum;

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
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssZ")
    @Schema(description="Date and time when transfer was submitted", example = "2022-10-15T20:14:22Z+2")
    public Date submittedAt;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String submittedTo;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssZ")
    @Schema(description="Date and time when transfer ended", example = "2022-10-15T20:14:22Z+2")
    public Date finishedAt;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String voName;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String userId;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String credId;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description="Detailed status of each transfer payload in this job "+
                        "(if the used transfer engine can supply it)")
    public Optional<List<TransferPayloadInfo>> payload;


    /***
     * Construct from extended FTS job info
     * @param jie The concrete job to construct from
     */
    public TransferInfoExtended(JobInfoExtended jie) {
        this(jie, FileDetails.none);
    }

    /***
     * Construct from extended FTS job info
     * @param jie The concrete job to construct from
     * @param fileInfo For which files to include transfer info
     */
    public TransferInfoExtended(JobInfoExtended jie, FileDetails fileInfo) {

        super(jie);

        this.kind = "TransferInfoExtended";
        this.jobId = jie.job_id;
        this.jobState = TransferState.fromString(jie.job_state);

        this.jobMetadata = new HashMap<>();
        if(null != jie.job_metadata && !jie.job_metadata.isEmpty())
            this.jobMetadata.putAll(jie.job_metadata);

        this.sourceSS = jie.source_se;
        this.destinationSS = jie.destination_se;
        this.verifyChecksum = null != jie.verify_checksum && jie.verify_checksum.equalsIgnoreCase("Y");

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
        this.finishedAt = jie.job_finished.orElse(null);
        this.reason = jie.reason;

        this.voName = jie.vo_name;
        this.userId = jie.user_dn;
        this.credId = jie.cred_id;

        if(FileDetails.none != fileInfo && jie.file_info.isPresent()) {
            List<JobFileInfo> jfl = jie.file_info.get();
            List<TransferPayloadInfo> tpl = new ArrayList<>();
            final boolean allFiles = FileDetails.all == fileInfo;

            for(JobFileInfo jf : jfl)
                if(allFiles || FileState.failed == FileState.fromString(jf.file_state))
                    tpl.add(new TransferPayloadInfo(jf));

            this.payload = Optional.of(tpl);
        }
        else
            this.payload = Optional.empty();
    }


    /***
     * The status of a transfer
     */
    @Schema(description="Will be 'succeeded' when all files transferred successfully, "+
                        "and 'failed' if all files failed to transfer.")
    public enum TransferState
    {
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
            if(statusLo.contains("staging") || statusLo.contains("ready") ||
               statusLo.contains("active") || statusLo.contains("progress"))
                return active;
            else if(statusLo.contains("dirty"))
                return partial;
            else if(statusLo.contains("succe") || statusLo.contains("finish"))
                return succeeded;
            else if(statusLo.contains("fail"))
                return failed;
            else if(statusLo.contains("cancel"))
                return canceled;

            return submitted;
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
