package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

import egi.s3.model.ObjectInfo;


/**
 * Content of a storage
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "A list of storage elements returned by a DOI parser or by a folder listing")
public class StorageContent {

    @Schema(description = "Type discriminator of this schema", example = "StorageContent")
    public String kind = "StorageContent";

    @Schema(description = "Number of entries in 'elements'")
    public int count;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description = "The files and folders in this storage or data set")
    public List<StorageElement> elements;


    /**
     * Constructor
     */
    public StorageContent() {
        this.count = 0;
        this.elements = new ArrayList<>();
    }

    /**
     * Constructor with allocation
     * @param size Initial capacity for storage elements.
     */
    public StorageContent(int size) {
        this.count = 0;
        this.elements = new ArrayList<>(size);
    }

    /**
     * Construct from Minio bucket content list.
     * @param folderUrl The URL to the bucket.
     * @param folderContent The list of objects in the bucket.
     */
    public StorageContent(String folderUrl, List<ObjectInfo> folderContent) {
        this.elements = new ArrayList<>();

        if('/' != folderUrl.charAt(folderUrl.length() - 1))
            folderUrl += "/";

        for(var element : folderContent) {
            StorageElement se = new StorageElement(element);
            se.accessUrl = folderUrl + se.name;

            this.elements.add(se);
        }

        this.count = this.elements.size();
    }

    /**
     * Copy constructor makes deep copy
     * @param storage The storage to copy all elements from.
     */
    public StorageContent(StorageContent storage) {
        this.elements = new ArrayList<>(this.count);
        merge(storage);
    }

    /***
     * Add a new element
     * @param se The storage element to add.
     */
    public void add(StorageElement se) {
        if(null != se) {
            this.elements.add(se);
            this.count++;
        }
    }

    /***
     * Add all elements from another storage
     * @param storage Storage to add all elements from.
     */
    public void merge(StorageContent storage) {
        if(null != storage) {
            this.elements.addAll(storage.elements);
            this.count = this.elements.size();
        }
    }
}
