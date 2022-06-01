package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import egi.fts.model.ObjectInfo;


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
     * Construct from FTS folder listing
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
     */
    public StorageContent(StorageContent storage) {
        this.elements = new ArrayList<>(this.count);
        this.elements.addAll(storage.elements);
        this.count = this.elements.size();
    }
}
