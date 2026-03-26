package org.apache.atlas.minio.model;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * MinIO 存储桶模型
 */
public class MinioBucket {
    private String name;
    private Date creationDate;
    private String location;
    private String owner;
    private Long quota;
    private Map<String, Object> attributes = new HashMap<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    /**
     * Set creation date from String format
     */
    public void setCreationDate(String creationDate) {
        // Store as string for now, conversion can be done by caller
        if (this.creationDate == null) {
            this.creationDate = new Date();
        }
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public Long getQuota() {
        return quota;
    }

    public void setQuota(Long quota) {
        this.quota = quota;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public void addAttribute(String key, Object value) {
        if (this.attributes == null) {
            this.attributes = new HashMap<>();
        }
        this.attributes.put(key, value);
    }
}
