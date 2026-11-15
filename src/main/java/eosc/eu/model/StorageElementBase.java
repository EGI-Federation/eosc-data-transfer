package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;


/**
 * Base properties of a storage element (file or folder)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageElementBase {

    @Schema(description = "Type discriminator of the schema that extends this class", example = "StorageElement")
    public String kind;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description="The name of the file. Can be used, together with the field 'path', " +
                        "to construct the destination path when this file is included in a " +
                        "data transfer.",
            example = "data.zip")
    public String name;

    /***
     * The path of the file. Can be used, together with the field "name",
     * to construct the destination path when this file is included in a
     * data transfer.
     */
    @Schema(description="The path of the file in the data repository. Can be used, together with the field 'name', " +
                        "to construct the destination path when this file is included in a " +
                        "data transfer.",
            example = "/records/6511035")
    public String path = "/";


    /**
     * Constructor
     * @param kind The discriminator of the concrete schema
     * @param name The name of the element
     */
    public StorageElementBase(String kind, String name) {
        this.kind = kind;
        this.name = name;
    }

    /**
     * Construct but change kind
     * @param kind The discriminator of the concrete schema
     */
    public StorageElementBase(String kind) {
        this.kind = kind;
    }
}
