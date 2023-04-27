package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;


/**
 * Base properties of a storage element (file or folder)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageElementBase {

    public String kind;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description="The name of the file. Can be used, together with the field 'path', " +
                        "to construct the destination path when this file is included in a " +
                        "data transfer.")
    public String name;

    /***
     * The path of the file. Can be used, together with the field "name",
     * to construct the destination path when this file is included in a
     * data transfer.
     */
    @Schema(description="The path of the file in the data repository. Can be used, together with the field 'path', " +
                        "to construct the destination path when this file is included in a " +
                        "data transfer.")
    public String path = "/";


    /**
     * Constructor
     */
    public StorageElementBase(String kind, String name) {
        this.kind = kind;
        this.name = name;
    }

    /**
     * Construct but change kind
     */
    public StorageElementBase(String kind) {
        this.kind = kind;
    }
}
