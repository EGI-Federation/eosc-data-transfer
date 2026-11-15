package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Optional;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;

import cern.model.JobFileInfo;


/**
 * The status of one transfer payload in a transfer job
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description="The status of one file in a transfer job; returned when the caller asks for " +
                    "fileInfo=failed or fileInfo=all")
public class TransferPayloadInfo {

    @Schema(description = "Type discriminator of this schema", example = "TransferPayloadInfo")
    public String kind = "TransferPayloadInfo";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description="State of this individual file")
    public FileState fileState;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description="Source storage element", example = "https://zenodo.org/record/6511035/files/data.zip")
    public String sourceSE;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description="Destination storage element", example = "https://dcache.example.org/data/data.zip")
    public String destinationSE;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description = "Size of the file in bytes")
    public Optional<Long> size;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssZ")
    @Schema(implementation = String.class, format = "date-time",
            description="Date and time when transfer of the file started",
            example = "2022-10-15T20:14:22+0100")
    public Date startedAt;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssZ")
    @Schema(implementation = String.class, format = "date-time",
            description="Date and time when transfer of the file finished",
            example = "2022-10-15T20:25:32+0100")
    public Date finishedAt;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description="Cause of file transfer failure")
    public String reason;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description = "Transfer priority of this file, 1 (lowest) to 5 (highest)")
    public Optional<Integer> priority;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description = "Number of retry attempts for this file")
    public Optional<Integer> retry;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description = "Checksum in the form 'algorithm:value'")
    public String checksum;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description = "Engine-specific metadata recorded for this file")
    public Map<String, String> fileMetadata;


    /***
     * Construct from FTS job file info
     * @param jfi The concrete file info to construct from
     */
    public TransferPayloadInfo(JobFileInfo jfi) {
        this.fileState = FileState.fromString(jfi.file_state);

        this.sourceSE = jfi.source_surl;
        this.destinationSE = jfi.dest_surl;

        this.size = jfi.filesize;
        this.startedAt = jfi.start_time.orElse(null);
        this.finishedAt = jfi.finish_time.orElse(null);
        this.reason = jfi.reason;
        this.priority = jfi.priority;
        this.retry = jfi.retry;
        this.checksum = jfi.checksum;

        this.fileMetadata = new HashMap<>();
        if(null != jfi.file_metadata && !jfi.file_metadata.isEmpty())
            this.fileMetadata.putAll(jfi.file_metadata);
    }

    /***
     * The status of a file in a transfer
     */
    @Schema(description="State of one file in a transfer")
    public enum FileState
    {
        submitted("submitted"),
        active("active"),
        succeeded("succeeded"),
        failed("failed"),
        canceled("canceled"),
        unknown("unknown");

        private final String status;

        /***
         * Construct from a string
         * @param status the desired status
         */
        FileState(String status) { this.status = status; }

        /***
         * Build from a string
         * @param status the desired status
         * @return TransferState with specified status
         */
        public static FileState fromString(String status) {
            final String statusLo = status.toLowerCase();
            if(statusLo.contains("staging") || statusLo.contains("ready") || statusLo.contains("active") ||
               statusLo.contains("start") || statusLo.contains("arch") || statusLo.contains("progress"))
                return active;
            else if(statusLo.contains("succe") || statusLo.contains("finish"))
                return succeeded;
            else if(statusLo.contains("fail"))
                return failed;
            else if(statusLo.contains("cancel"))
                return canceled;
            else if(statusLo.contains("not_used"))
                return unknown;

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

    /***
     * For which files in a transfer to return status
     */
    @Schema(description = "Controls which per-file details are included: 'none', only 'failed' files, or 'all'")
    public enum FileDetails
    {
        none("none"),
        failed("failed"),
        all("all");

        private final String detailsFor;

        FileDetails(String detailsFor) { this.detailsFor = detailsFor; }

        /***
         * Build from a string
         * @param detailsFor the desired details
         * @return FileDetails with specified details
         */
        public static FileDetails fromString(String detailsFor) {
            final String detailsForLo = detailsFor.toLowerCase();
            if(detailsForLo.equals("all"))
                return all;
            else if(detailsForLo.equals("failed"))
                return failed;

            return none;
        }

        public String detailsFor() { return this.detailsFor; }
    }
}
