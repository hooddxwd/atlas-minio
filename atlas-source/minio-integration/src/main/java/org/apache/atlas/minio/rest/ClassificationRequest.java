package org.apache.atlas.minio.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Request object for manual classification
 */
@ApiModel(description = "Request to manually classify a MinIO entity")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClassificationRequest {

    @ApiModelProperty(value = "Qualified name of the entity to classify", required = true, example = "bucket-name@cluster")
    private String qualifiedName;

    @ApiModelProperty(value = "List of classification tags to apply", required = true, example = "[\"PII\", " + "\"FINANCE\"]")
    private List<String> tags = new ArrayList<>();

    public ClassificationRequest() {
    }

    public ClassificationRequest(String qualifiedName, List<String> tags) {
        this.qualifiedName = qualifiedName;
        this.tags = tags;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public void setQualifiedName(String qualifiedName) {
        this.qualifiedName = qualifiedName;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}
