package org.apache.atlas.minio.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * Request object for sync operations
 */
@ApiModel(description = "Request to trigger synchronization")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyncRequest {

    @ApiModelProperty(value = "Sync mode: full or incremental", required = true, allowableValues = "full,incremental")
    private String mode;

    public SyncRequest() {
    }

    public SyncRequest(String mode) {
        this.mode = mode;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }
}
