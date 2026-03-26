package org.apache.atlas.minio.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * Response object for connection test
 */
@ApiModel(description = "Response for MinIO connection test")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TestConnectionResponse {

    @ApiModelProperty(value = "Connection status", allowableValues = "connected,disconnected")
    private String status;

    @ApiModelProperty(value = "MinIO endpoint URL", example = "http://localhost:9000")
    private String endpoint;

    @ApiModelProperty(value = "Additional message about the connection")
    private String message;

    public TestConnectionResponse() {
    }

    public TestConnectionResponse(String status, String endpoint, String message) {
        this.status = status;
        this.endpoint = endpoint;
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
