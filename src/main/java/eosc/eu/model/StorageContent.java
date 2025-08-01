package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import egi.s3.model.ObjectInfo;


/**
 * Content of a storage
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageContent {

    public String kind = "StorageContent";
    public int count;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
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
     * Construct from FTS folder listing
     * @param folderUrl The URL to the folder.
     * @param folderContent The list of files in the folder.
     */
    public StorageContent(String folderUrl, Map<String, ObjectInfo> folderContent) {
        this.elements = new ArrayList<>();

        if('/' != folderUrl.charAt(folderUrl.length() - 1))
            folderUrl += "/";

        for(var seName : folderContent.keySet()) {
            StorageElement se = new StorageElement(folderContent.get(seName));
            se.name = seName;
            se.accessUrl = folderUrl + seName;

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
