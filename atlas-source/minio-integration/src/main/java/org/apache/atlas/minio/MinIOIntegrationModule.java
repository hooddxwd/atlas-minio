package org.apache.atlas.minio;

import org.apache.atlas.minio.bridge.MinIOBridge;
import org.apache.atlas.minio.classification.ClassificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;

/**
 * MinIO Integration Module Main Class
 * Responsible for initializing and managing MinIO integration features
 */
@ComponentScan(basePackages = "org.apache.atlas.minio")
public class MinIOIntegrationModule {

    private static final Logger LOG = LoggerFactory.getLogger(MinIOIntegrationModule.class);

    private final MinIOBridge bridge;
    private final ClassificationService classificationService;

    public MinIOIntegrationModule(MinIOBridge bridge,
                                   ClassificationService classificationService) {
        this.bridge = bridge;
        this.classificationService = classificationService;
    }

    /**
     * Initialize MinIO integration module
     */
    public void initialize() {
        LOG.info("Initializing MinIO integration module...");

        try {
            // Initialize bridge
            bridge.initialize();

            LOG.info("MinIO integration module initialized successfully");
        } catch (Exception e) {
            LOG.error("Failed to initialize MinIO integration module", e);
            throw new RuntimeException("Failed to initialize MinIO integration", e);
        }
    }

    /**
     * Shutdown MinIO integration module
     */
    public void shutdown() {
        LOG.info("Shutting down MinIO integration module...");

        try {
            bridge.shutdown();

            LOG.info("MinIO integration module shut down");
        } catch (Exception e) {
            LOG.error("Error shutting down MinIO integration module", e);
        }
    }
}
