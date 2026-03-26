package org.apache.atlas.minio.bridge;

import org.apache.atlas.AtlasClientV2;
import org.apache.atlas.minio.model.MinioBucket;
import org.apache.atlas.minio.model.MinioObject;
import org.apache.atlas.model.instance.AtlasEntity;
import org.apache.atlas.model.instance.AtlasEntity.AtlasEntityWithExtInfo;
import org.apache.atlas.model.instance.AtlasEntityHeader;
import org.apache.atlas.model.instance.EntityMutationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MinIO Bridge - Syncs MinIO metadata to Apache Atlas
 * Follows KafkaBridge pattern for Atlas entity management
 */
@Component
public class MinIOBridge {
    private static final Logger LOG = LoggerFactory.getLogger(MinIOBridge.class);
    private static final String ATTRIBUTE_QUALIFIED_NAME = "qualifiedName";
    private static final String FORMAT_BUCKET_QUALIFIED_NAME = "%s@%s";
    private static final String FORMAT_OBJECT_QUALIFIED_NAME = "%s@%s";

    // Atlas Entity Types
    private static final String MINIO_BUCKET_TYPE = "minio_bucket";
    private static final String MINIO_OBJECT_TYPE = "minio_object";

    @Value("${atlas.minio.cluster.name:primary}")
    private String clusterName;

    @Value("${atlas.minio.sync.batch.size:100}")
    private int batchSize;

    @Value("${atlas.minio.sync.thread.pool.size:5}")
    private int syncThreadPoolSize;

    private final AtlasClientV2 atlasClient;
    private final MinIOClient minioClient;
    private final MetadataExtractor metadataExtractor;
    private Date lastSyncTimestamp;

    public Date getLastSyncTimestamp() {
        return lastSyncTimestamp;
    }

    @Autowired
    public MinIOBridge(AtlasClientV2 atlasClient,
                       @Value("${atlas.minio.endpoint:http://localhost:9000}") String endpoint,
                       @Value("${atlas.minio.access.key:minioadmin}") String accessKey,
                       @Value("${atlas.minio.secret.key:minioadmin}") String secretKey,
                       @Value("${atlas.minio.region:us-east-1}") String region) {
        this.atlasClient = atlasClient;
        this.minioClient = new MinIOClient(endpoint, accessKey, secretKey, region);
        this.metadataExtractor = new MetadataExtractor(minioClient);
        LOG.info("MinIOBridge initialized with endpoint: {}", endpoint);
    }

    @PostConstruct
    public void init() {
        LOG.info("MinIOBridge starting...");
        initialize();
    }

    /**
     * Initialize the MinIOBridge - test connection and validate setup
     * This is the public method required by the specification
     */
    public void initialize() {
        LOG.info("Initializing MinIOBridge...");
        boolean connected = minioClient.testConnection();
        if (!connected) {
            throw new RuntimeException("Failed to connect to MinIO server at " + minioClient.getEndpoint());
        }
        LOG.info("MinIOBridge initialized successfully");
    }

    @PreDestroy
    public void destroy() {
        LOG.info("MinIOBridge shutting down...");
        shutdown();
    }

    /**
     * Shutdown the MinIOBridge gracefully
     * This is the public method required by the specification
     */
    public void shutdown() {
        LOG.info("Shutting down MinIOBridge...");
        try {
            if (minioClient != null) {
                minioClient.shutdown();
            }
        } catch (Exception e) {
            LOG.error("Error shutting down MinIO client", e);
        }
    }

    /**
     * Perform full sync - scan all buckets and objects
     */
    public SyncStats performFullSync() {
        LOG.info("Starting full MinIO metadata sync...");
        SyncStats stats = new SyncStats();

        try {
            // Sync all buckets
            List<MinioBucket> buckets = minioClient.listBuckets();
            LOG.info("Found {} buckets to sync", buckets.size());

            for (MinioBucket bucket : buckets) {
                try {
                    syncBucket(bucket);
                    stats.bucketsProcessed.incrementAndGet();

                    // Sync objects in this bucket
                    List<MinioObject> objects = minioClient.listObjects(bucket.getName());
                    LOG.info("Found {} objects in bucket {}", objects.size(), bucket.getName());

                    for (MinioObject object : objects) {
                        try {
                            syncObject(object);
                            stats.objectsProcessed.incrementAndGet();
                        } catch (Exception e) {
                            LOG.error("Failed to sync object {} in bucket {}", object.getPath(), bucket.getName(), e);
                            stats.objectsFailed.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    LOG.error("Failed to sync bucket {}", bucket.getName(), e);
                    stats.bucketsFailed.incrementAndGet();
                }
            }

            this.lastSyncTimestamp = new Date();
            stats.completed = true;
            LOG.info("Full sync completed. Buckets: {}/{}, Objects: {}/{}",
                    stats.bucketsProcessed.get(), buckets.size(),
                    stats.objectsProcessed.get(), stats.objectsProcessed.get() + stats.objectsFailed.get());

        } catch (Exception e) {
            LOG.error("Full sync failed", e);
            stats.completed = false;
        }

        return stats;
    }

    /**
     * Perform incremental sync - only objects modified since last sync
     */
    public SyncStats performIncrementalSync() {
        LOG.info("Starting incremental MinIO metadata sync since {}...", lastSyncTimestamp);
        SyncStats stats = new SyncStats();

        if (lastSyncTimestamp == null) {
            LOG.info("No previous sync found, performing full sync instead");
            return performFullSync();
        }

        try {
            List<MinioBucket> buckets = minioClient.listBuckets();

            for (MinioBucket bucket : buckets) {
                try {
                    // Get objects modified since last sync
                    List<MinioObject> objects = minioClient.listObjectsModifiedSince(bucket.getName(), lastSyncTimestamp);
                    LOG.info("Found {} modified objects in bucket {} since {}", objects.size(), bucket.getName(), lastSyncTimestamp);

                    for (MinioObject object : objects) {
                        try {
                            syncObject(object);
                            stats.objectsProcessed.incrementAndGet();
                        } catch (Exception e) {
                            LOG.error("Failed to sync object {} in bucket {}", object.getPath(), bucket.getName(), e);
                            stats.objectsFailed.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    LOG.error("Failed to list modified objects in bucket {}", bucket.getName(), e);
                }
            }

            this.lastSyncTimestamp = new Date();
            stats.completed = true;
            LOG.info("Incremental sync completed. Objects: {}/{}",
                    stats.objectsProcessed.get(), stats.objectsProcessed.get() + stats.objectsFailed.get());

        } catch (Exception e) {
            LOG.error("Incremental sync failed", e);
            stats.completed = false;
        }

        return stats;
    }

    /**
     * Import a MinIO bucket to Atlas
     * This is the public method required by the specification
     */
    public void importBucketToAtlas(MinioBucket bucket) throws Exception {
        syncBucket(bucket);
    }

    /**
     * Import a MinIO object to Atlas
     * This is the public method required by the specification
     */
    public void importObjectToAtlas(MinioObject object) throws Exception {
        syncObject(object);
    }

    /**
     * Sync a single bucket to Atlas
     */
    public void syncBucket(MinioBucket bucket) throws Exception {
        String qualifiedName = getBucketQualifiedName(bucket.getName());
        AtlasEntityWithExtInfo existingEntity = findEntityInAtlas(MINIO_BUCKET_TYPE, qualifiedName);

        if (existingEntity == null) {
            LOG.info("Creating new bucket entity: {}", bucket.getName());
            AtlasEntity entity = createBucketEntity(bucket);
            createEntityInAtlas(new AtlasEntityWithExtInfo(entity));
        } else {
            LOG.debug("Updating existing bucket entity: {}", bucket.getName());
            AtlasEntity entity = createBucketEntity(bucket);
            entity.setGuid(existingEntity.getEntity().getGuid());
            updateEntityInAtlas(new AtlasEntityWithExtInfo(entity));
        }
    }

    /**
     * Sync a single object to Atlas
     */
    public void syncObject(MinioObject object) throws Exception {
        String qualifiedName = getObjectQualifiedName(object.getBucketName(), object.getPath());
        AtlasEntityWithExtInfo existingEntity = findEntityInAtlas(MINIO_OBJECT_TYPE, qualifiedName);

        if (existingEntity == null) {
            LOG.debug("Creating new object entity: {}/{}", object.getBucketName(), object.getPath());
            AtlasEntity entity = createObjectEntity(object);
            createEntityInAtlas(new AtlasEntityWithExtInfo(entity));
        } else {
            LOG.debug("Updating existing object entity: {}/{}", object.getBucketName(), object.getPath());
            AtlasEntity entity = createObjectEntity(object);
            entity.setGuid(existingEntity.getEntity().getGuid());
            updateEntityInAtlas(new AtlasEntityWithExtInfo(entity));
        }
    }

    /**
     * Helper method to create Atlas bucket entity from MinioBucket
     */
    public AtlasEntity createBucketEntity(MinioBucket bucket) {
        AtlasEntity entity = new AtlasEntity(MINIO_BUCKET_TYPE);

        String qualifiedName = getBucketQualifiedName(bucket.getName());
        entity.setAttribute(ATTRIBUTE_QUALIFIED_NAME, qualifiedName);
        entity.setAttribute("name", bucket.getName());
        entity.setAttribute("clusterName", clusterName);
        entity.setAttribute("owner", bucket.getOwner() != null ? bucket.getOwner() : "unknown");
        entity.setAttribute("location", bucket.getLocation() != null ? bucket.getLocation() : "default");

        if (bucket.getCreationDate() != null) {
            entity.setAttribute("creationDate", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(bucket.getCreationDate()));
        }

        if (bucket.getQuota() != null) {
            entity.setAttribute("quota", bucket.getQuota());
        }

        // Add attributes
        if (bucket.getAttributes() != null && !bucket.getAttributes().isEmpty()) {
            entity.setAttribute("attributes", bucket.getAttributes());
        }

        return entity;
    }

    /**
     * Helper method to create Atlas object entity from MinioObject
     */
    public AtlasEntity createObjectEntity(MinioObject object) {
        AtlasEntity entity = new AtlasEntity(MINIO_OBJECT_TYPE);

        String qualifiedName = getObjectQualifiedName(object.getBucketName(), object.getPath());
        entity.setAttribute(ATTRIBUTE_QUALIFIED_NAME, qualifiedName);
        entity.setAttribute("name", object.getPath());
        entity.setAttribute("clusterName", clusterName);
        entity.setAttribute("bucketName", object.getBucketName());
        entity.setAttribute("path", object.getPath());
        entity.setAttribute("size", object.getSize());
        entity.setAttribute("etag", object.getETag());

        if (object.getLastModified() != null) {
            entity.setAttribute("lastModified", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(object.getLastModified()));
        }

        if (object.getContentType() != null) {
            entity.setAttribute("contentType", object.getContentType());
        }

        if (object.getVersionId() != null) {
            entity.setAttribute("versionId", object.getVersionId());
        }

        if (object.getStorageClass() != null) {
            entity.setAttribute("storageClass", object.getStorageClass());
        }

        // Add user metadata
        if (object.getUserMetadata() != null && !object.getUserMetadata().isEmpty()) {
            entity.setAttribute("userMetadata", object.getUserMetadata());
        }

        // Add ACL
        if (object.getAcl() != null && !object.getAcl().isEmpty()) {
            entity.setAttribute("acl", object.getAcl());
        }

        // Add attributes
        if (object.getAttributes() != null && !object.getAttributes().isEmpty()) {
            entity.setAttribute("attributes", object.getAttributes());
        }

        return entity;
    }

    /**
     * Create entity in Atlas
     */
    private AtlasEntityWithExtInfo createEntityInAtlas(AtlasEntityWithExtInfo entity) throws Exception {
        AtlasEntityWithExtInfo ret = null;
        EntityMutationResponse response = atlasClient.createEntity(entity);
        List<AtlasEntityHeader> entities = response.getCreatedEntities();

        if (entities != null && !entities.isEmpty()) {
            AtlasEntityWithExtInfo getByGuidResponse = atlasClient.getEntityByGuid(entities.get(0).getGuid());
            ret = getByGuidResponse;
            LOG.info("Created {} entity: name={}, guid={}",
                    ret.getEntity().getTypeName(),
                    ret.getEntity().getAttribute(ATTRIBUTE_QUALIFIED_NAME),
                    ret.getEntity().getGuid());
        }

        return ret;
    }

    /**
     * Update entity in Atlas
     */
    private AtlasEntityWithExtInfo updateEntityInAtlas(AtlasEntityWithExtInfo entity) throws Exception {
        AtlasEntityWithExtInfo ret;
        EntityMutationResponse response = atlasClient.updateEntity(entity);

        if (response != null && response.getUpdatedEntities() != null && !response.getUpdatedEntities().isEmpty()) {
            AtlasEntityWithExtInfo getByGuidResponse = atlasClient.getEntityByGuid(response.getUpdatedEntities().get(0).getGuid());
            ret = getByGuidResponse;
            LOG.info("Updated {} entity: name={}, guid={}",
                    ret.getEntity().getTypeName(),
                    ret.getEntity().getAttribute(ATTRIBUTE_QUALIFIED_NAME),
                    ret.getEntity().getGuid());
        } else {
            LOG.info("Entity {} unchanged in Atlas", entity.getEntity().getAttribute(ATTRIBUTE_QUALIFIED_NAME));
            ret = entity;
        }

        return ret;
    }

    /**
     * Find entity in Atlas by qualified name
     */
    private AtlasEntityWithExtInfo findEntityInAtlas(String typeName, String qualifiedName) {
        AtlasEntityWithExtInfo ret = null;
        try {
            ret = atlasClient.getEntityByAttribute(typeName, Collections.singletonMap(ATTRIBUTE_QUALIFIED_NAME, qualifiedName));
        } catch (Exception e) {
            LOG.debug("Entity {} with qualifiedName {} not found: {}", typeName, qualifiedName, e.getMessage());
        }
        return ret;
    }

    /**
     * Get qualified name for bucket
     */
    public String getBucketQualifiedName(String bucketName) {
        return String.format(FORMAT_BUCKET_QUALIFIED_NAME, bucketName.toLowerCase(), clusterName);
    }

    /**
     * Get qualified name for object
     */
    public String getObjectQualifiedName(String bucketName, String objectPath) {
        return String.format(FORMAT_OBJECT_QUALIFIED_NAME,
                (bucketName.toLowerCase() + "/" + objectPath).toLowerCase(),
                clusterName);
    }

    /**
     * Sync statistics
     */
    public static class SyncStats {
        public AtomicInteger bucketsProcessed = new AtomicInteger(0);
        public AtomicInteger bucketsFailed = new AtomicInteger(0);
        public AtomicInteger objectsProcessed = new AtomicInteger(0);
        public AtomicInteger objectsFailed = new AtomicInteger(0);
        public boolean completed = false;

        @Override
        public String toString() {
            return String.format("SyncStats{buckets=%d/%d, objects=%d/%d, completed=%s}",
                    bucketsProcessed.get(), bucketsProcessed.get() + bucketsFailed.get(),
                    objectsProcessed.get(), objectsProcessed.get() + objectsFailed.get(),
                    completed);
        }
    }
}
