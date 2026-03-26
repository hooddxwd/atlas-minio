package org.apache.atlas.minio.rest;

import org.apache.atlas.annotation.Timed;
import org.apache.atlas.minio.bridge.MinIOBridge;
import org.apache.atlas.minio.bridge.SyncScheduler;
import org.apache.atlas.minio.classification.ClassificationService;
import org.apache.atlas.minio.event.EventQueue;
import org.apache.atlas.minio.model.ClassificationResult;
import org.apache.atlas.minio.model.MinioBucket;
import org.apache.atlas.minio.model.MinioEvent;
import org.apache.atlas.minio.model.MinioObject;
import org.apache.atlas.minio.model.SyncReport;
import org.apache.atlas.utils.AtlasPerfTracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for MinIO Integration
 * Provides endpoints for managing MinIO metadata synchronization and classification
 */
@Path("/api/atlas/minio")
@Singleton
@Component
@Produces({MediaType.APPLICATION_JSON})
@Consumes({MediaType.APPLICATION_JSON})
public class MinioResource {

    private static final Logger LOG = LoggerFactory.getLogger(MinioResource.class);
    private static final Logger PERF_LOG = AtlasPerfTracer.getPerfLogger("rest.MinioResource");

    private static final String DEFAULT_LIMIT = "100";
    private static final String DEFAULT_PREFIX = "";
    private static final String STATUS_CONNECTED = "connected";
    private static final String STATUS_DISCONNECTED = "disconnected";

    private final MinIOBridge minioBridge;
    private final SyncScheduler syncScheduler;
    private final EventQueue eventQueue;
    private final ClassificationService classificationService;

    @Inject
    public MinioResource(MinIOBridge minioBridge,
                         SyncScheduler syncScheduler,
                         EventQueue eventQueue,
                         ClassificationService classificationService) {
        this.minioBridge = minioBridge;
        this.syncScheduler = syncScheduler;
        this.eventQueue = eventQueue;
        this.classificationService = classificationService;
    }

    /**
     * 1. Test MinIO Connection
     * GET /api/atlas/minio/test
     */
    @GET
    @Path("/test")
    @Timed
    public Response testConnection() {
        AtlasPerfTracer perf = null;
        try {
            if (AtlasPerfTracer.isPerfTraceEnabled(PERF_LOG)) {
                perf = AtlasPerfTracer.getPerfTracer(PERF_LOG, "MinioResource.testConnection()");
            }

            LOG.info("Testing MinIO connection...");

            boolean isConnected = minioBridge.testConnection();
            String endpoint = minioBridge.getEndpoint();

            TestConnectionResponse response;
            if (isConnected) {
                response = new TestConnectionResponse(STATUS_CONNECTED, endpoint,
                        "Successfully connected to MinIO server");
                LOG.info("MinIO connection test successful: {}", endpoint);
            } else {
                response = new TestConnectionResponse(STATUS_DISCONNECTED, endpoint,
                        "Failed to connect to MinIO server");
                LOG.warn("MinIO connection test failed for endpoint: {}", endpoint);
            }

            return Response.ok(response).build();

        } catch (Exception e) {
            LOG.error("Error testing MinIO connection", e);
            return Response.serverError()
                    .entity(new TestConnectionResponse(STATUS_DISCONNECTED, null,
                            "Error: " + e.getMessage()))
                    .build();
        } finally {
            AtlasPerfTracer.log(perf);
        }
    }

    /**
     * 2. Trigger Sync
     * POST /api/atlas/minio/sync?mode={full|incremental}
     */
    @POST
    @Path("/sync")
    @Timed
    public Response triggerSync(@QueryParam("mode") @DefaultValue("incremental") String mode) {
        AtlasPerfTracer perf = null;
        try {
            if (AtlasPerfTracer.isPerfTraceEnabled(PERF_LOG)) {
                perf = AtlasPerfTracer.getPerfTracer(PERF_LOG, "MinioResource.triggerSync(" + mode + ")");
            }

            LOG.info("Triggering {} sync", mode);

            // Validate mode
            if (!"full".equalsIgnoreCase(mode) && !"incremental".equalsIgnoreCase(mode)) {
                LOG.error("Invalid sync mode: {}", mode);
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Collections.singletonMap("error", "Invalid mode: " + mode + ". Must be 'full' or 'incremental'"))
                        .build();
            }

            SyncReport report;
            if ("full".equalsIgnoreCase(mode)) {
                report = syncScheduler.triggerFullSync();
            } else {
                report = syncScheduler.triggerIncrementalSync();
            }

            LOG.info("Sync triggered successfully: {}", report.getSummary());
            return Response.ok(report).build();

        } catch (Exception e) {
            LOG.error("Error triggering sync", e);
            return Response.serverError()
                    .entity(Collections.singletonMap("error", "Error triggering sync: " + e.getMessage()))
                    .build();
        } finally {
            AtlasPerfTracer.log(perf);
        }
    }

    /**
     * 3. List Buckets
     * GET /api/atlas/minio/buckets
     */
    @GET
    @Path("/buckets")
    @Timed
    public Response listBuckets() {
        AtlasPerfTracer perf = null;
        try {
            if (AtlasPerfTracer.isPerfTraceEnabled(PERF_LOG)) {
                perf = AtlasPerfTracer.getPerfTracer(PERF_LOG, "MinioResource.listBuckets()");
            }

            LOG.debug("Listing MinIO buckets...");

            List<MinioBucket> buckets = minioBridge.listMinioBuckets();

            LOG.debug("Retrieved {} buckets", buckets.size());
            return Response.ok(buckets).build();

        } catch (Exception e) {
            LOG.error("Error listing buckets", e);
            return Response.serverError()
                    .entity(Collections.singletonMap("error", "Error listing buckets: " + e.getMessage()))
                    .build();
        } finally {
            AtlasPerfTracer.log(perf);
        }
    }

    /**
     * 4. List Objects
     * GET /api/atlas/minio/objects?bucket={bucketName}&prefix={prefix}&limit={limit}
     */
    @GET
    @Path("/objects")
    @Timed
    public Response listObjects(@QueryParam("bucket") String bucketName,
                                @QueryParam("prefix") @DefaultValue(DEFAULT_PREFIX) String prefix,
                                @QueryParam("limit") @DefaultValue(DEFAULT_LIMIT) int limit) {
        AtlasPerfTracer perf = null;
        try {
            if (AtlasPerfTracer.isPerfTraceEnabled(PERF_LOG)) {
                perf = AtlasPerfTracer.getPerfTracer(PERF_LOG, "MinioResource.listObjects(" + bucketName + ")");
            }

            if (bucketName == null || bucketName.trim().isEmpty()) {
                LOG.error("Bucket name is required");
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Collections.singletonMap("error", "Bucket name is required"))
                        .build();
            }

            LOG.debug("Listing objects in bucket: {} with prefix: {}, limit: {}", bucketName, prefix, limit);

            List<MinioObject> objects = minioBridge.listObjects(bucketName, prefix, limit);

            LOG.debug("Retrieved {} objects from bucket: {}", objects.size(), bucketName);
            return Response.ok(objects).build();

        } catch (Exception e) {
            LOG.error("Error listing objects in bucket: {}", bucketName, e);
            return Response.serverError()
                    .entity(Collections.singletonMap("error", "Error listing objects: " + e.getMessage()))
                    .build();
        } finally {
            AtlasPerfTracer.log(perf);
        }
    }

    /**
     * 5. Manual Classification
     * POST /api/atlas/minio/classify
     */
    @POST
    @Path("/classify")
    @Timed
    public Response classifyEntity(ClassificationRequest request) {
        AtlasPerfTracer perf = null;
        try {
            if (AtlasPerfTracer.isPerfTraceEnabled(PERF_LOG)) {
                perf = AtlasPerfTracer.getPerfTracer(PERF_LOG, "MinioResource.classifyEntity(" + request.getQualifiedName() + ")");
            }

            // Validate request
            if (request.getQualifiedName() == null || request.getQualifiedName().trim().isEmpty()) {
                LOG.error("Qualified name is required for classification");
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Collections.singletonMap("error", "Qualified name is required"))
                        .build();
            }

            if (request.getTags() == null || request.getTags().isEmpty()) {
                LOG.error("Tags are required for classification");
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Collections.singletonMap("error", "At least one tag is required"))
                        .build();
            }

            LOG.info("Classifying entity {} with tags: {}", request.getQualifiedName(), request.getTags());

            // Find the entity first (bucket or object)
            String qualifiedName = request.getQualifiedName();

            // Try to find as bucket first
            MinioBucket bucket = minioBridge.getBucket(qualifiedName);
            if (bucket != null) {
                ClassificationResult result = classificationService.classifyBucket(bucket, request.getTags());
                LOG.info("Successfully classified bucket: {}", qualifiedName);
                return Response.ok(result).build();
            }

            // Try to find as object
            MinioObject object = minioBridge.getObject(qualifiedName);
            if (object != null) {
                ClassificationResult result = classificationService.classifyObject(object, request.getTags());
                LOG.info("Successfully classified object: {}", qualifiedName);
                return Response.ok(result).build();
            }

            // Entity not found
            LOG.warn("Entity not found for classification: {}", qualifiedName);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Collections.singletonMap("error", "Entity not found: " + qualifiedName))
                    .build();

        } catch (Exception e) {
            LOG.error("Error classifying entity: {}", request.getQualifiedName(), e);
            return Response.serverError()
                    .entity(Collections.singletonMap("error", "Error classifying entity: " + e.getMessage()))
                    .build();
        } finally {
            AtlasPerfTracer.log(perf);
        }
    }

    /**
     * 6. Receive MinIO Events (Webhook)
     * POST /api/atlas/minio/events
     */
    @POST
    @Path("/events")
    @Timed
    public Response receiveEvent(Map<String, Object> eventData) {
        AtlasPerfTracer perf = null;
        try {
            if (AtlasPerfTracer.isPerfTraceEnabled(PERF_LOG)) {
                perf = AtlasPerfTracer.getPerfTracer(PERF_LOG, "MinioResource.receiveEvent()");
            }

            LOG.debug("Received MinIO event: {}", eventData);

            // Parse MinIO event (S3 event format)
            MinioEvent event = parseMinioEvent(eventData);

            if (event == null) {
                LOG.warn("Failed to parse MinIO event");
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Collections.singletonMap("error", "Invalid event format"))
                        .build();
            }

            // Enqueue event for async processing
            boolean enqueued = eventQueue.enqueue(event);

            if (enqueued) {
                LOG.info("MinIO event enqueued successfully: {}", event);
                return Response.status(Response.Status.ACCEPTED)
                        .entity(Collections.singletonMap("status", "Event enqueued for processing"))
                        .build();
            } else {
                LOG.warn("Event queue is full, event dropped");
                return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                        .entity(Collections.singletonMap("error", "Event queue is full"))
                        .build();
            }

        } catch (Exception e) {
            LOG.error("Error processing MinIO event", e);
            return Response.serverError()
                    .entity(Collections.singletonMap("error", "Error processing event: " + e.getMessage()))
                    .build();
        } finally {
            AtlasPerfTracer.log(perf);
        }
    }

    /**
     * 7. Get Sync Report
     * GET /api/atlas/minio/sync/report
     */
    @GET
    @Path("/sync/report")
    @Timed
    public Response getSyncReport() {
        AtlasPerfTracer perf = null;
        try {
            if (AtlasPerfTracer.isPerfTraceEnabled(PERF_LOG)) {
                perf = AtlasPerfTracer.getPerfTracer(PERF_LOG, "MinioResource.getSyncReport()");
            }

            LOG.debug("Fetching most recent sync report...");

            SyncReport report = syncScheduler.getLatestReport();

            if (report == null) {
                LOG.debug("No sync reports available");
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Collections.singletonMap("error", "No sync reports available"))
                        .build();
            }

            LOG.debug("Retrieved sync report: {}", report.getSummary());
            return Response.ok(report).build();

        } catch (Exception e) {
            LOG.error("Error fetching sync report", e);
            return Response.serverError()
                    .entity(Collections.singletonMap("error", "Error fetching sync report: " + e.getMessage()))
                    .build();
        } finally {
            AtlasPerfTracer.log(perf);
        }
    }

    /**
     * 8. Get Sync History
     * GET /api/atlas/minio/sync/history?limit={limit}
     */
    @GET
    @Path("/sync/history")
    @Timed
    public Response getSyncHistory(@QueryParam("limit") @DefaultValue("10") int limit) {
        AtlasPerfTracer perf = null;
        try {
            if (AtlasPerfTracer.isPerfTraceEnabled(PERF_LOG)) {
                perf = AtlasPerfTracer.getPerfTracer(PERF_LOG, "MinioResource.getSyncHistory(" + limit + ")");
            }

            LOG.debug("Fetching sync history with limit: {}", limit);

            // Validate limit
            if (limit <= 0 || limit > 1000) {
                LOG.error("Invalid limit: {}", limit);
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Collections.singletonMap("error", "Limit must be between 1 and 1000"))
                        .build();
            }

            List<SyncReport> history = syncScheduler.getReportHistory(limit);

            LOG.debug("Retrieved {} sync reports", history.size());
            return Response.ok(history).build();

        } catch (Exception e) {
            LOG.error("Error fetching sync history", e);
            return Response.serverError()
                    .entity(Collections.singletonMap("error", "Error fetching sync history: " + e.getMessage()))
                    .build();
        } finally {
            AtlasPerfTracer.log(perf);
        }
    }

    /**
     * Parse MinIO event from webhook payload
     * Handles S3 event format from MinIO
     */
    private MinioEvent parseMinioEvent(Map<String, Object> eventData) {
        try {
            if (eventData == null || eventData.isEmpty()) {
                return null;
            }

            MinioEvent event = new MinioEvent();

            // Extract event name (e.g., "s3:ObjectCreated:Put")
            String eventName = extractString(eventData, "EventName");
            event.setEventName(eventName);
            event.setEventType(eventName);

            // Extract bucket name
            Object bucketObj = eventData.get("BucketName");
            if (bucketObj != null) {
                event.setBucketName(bucketObj.toString());
            }

            // Extract object key/path
            Object keyObj = eventData.get("Key");
            if (keyObj != null) {
                event.setObjectPath(keyObj.toString());
            }

            // Extract records array (standard S3 event format)
            Object recordsObj = eventData.get("Records");
            if (recordsObj instanceof List) {
                List<Map<String, Object>> records = (List<Map<String, Object>>) recordsObj;
                if (!records.isEmpty()) {
                    Map<String, Object> firstRecord = records.get(0);
                    parseS3Record(event, firstRecord);
                }
            }

            // Store raw event data
            event.setRawData(eventData);

            return event;

        } catch (Exception e) {
            LOG.error("Error parsing MinIO event", e);
            return null;
        }
    }

    /**
     * Parse S3 record from event
     */
    private void parseS3Record(MinioEvent event, Map<String, Object> record) {
        try {
            // Extract event name
            String eventName = extractString(record, "eventName");
            if (eventName != null) {
                event.setEventName(eventName);
                event.setEventType(eventName);
            }

            // Extract event time
            Object eventTimeObj = record.get("eventTime");
            if (eventTimeObj != null) {
                // Parse ISO 8601 timestamp
                // For simplicity, we'll just store it as string and let Jackson handle conversion
                // In production, you'd parse this to a Date object
            }

            // Extract S3 bucket info
            Map<String, Object> s3Obj = (Map<String, Object>) record.get("s3");
            if (s3Obj != null) {
                Map<String, Object> bucketObj = (Map<String, Object>) s3Obj.get("bucket");
                if (bucketObj != null) {
                    String bucketName = extractString(bucketObj, "name");
                    if (bucketName != null) {
                        event.setBucketName(bucketName);
                    }
                }

                // Extract S3 object info
                Map<String, Object> objectObj = (Map<String, Object>) s3Obj.get("object");
                if (objectObj != null) {
                    String key = extractString(objectObj, "key");
                    if (key != null) {
                        event.setObjectPath(key);
                    }
                }
            }

            // Extract user identity
            Map<String, Object> userIdentityObj = (Map<String, Object>) record.get("userIdentity");
            if (userIdentityObj != null) {
                Map<String, String> userIdentity = new HashMap<>();
                for (Map.Entry<String, Object> entry : userIdentityObj.entrySet()) {
                    if (entry.getValue() != null) {
                        userIdentity.put(entry.getKey(), entry.getValue().toString());
                    }
                }
                event.setUserIdentity(userIdentity);
            }

            // Extract source IP
            String sourceIP = extractString(record, "sourceIPAddress");
            if (sourceIP != null) {
                event.setSourceIPAddress(sourceIP);
            }

        } catch (Exception e) {
            LOG.error("Error parsing S3 record", e);
        }
    }

    /**
     * Helper method to extract string value from map
     */
    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
}
