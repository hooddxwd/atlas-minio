package org.apache.atlas.minio.event;

import org.apache.atlas.minio.bridge.MinIOBridge;
import org.apache.atlas.minio.model.MinioEvent;
import org.apache.atlas.minio.model.MinioObject;
import org.apache.atlas.minio.utils.MinIOConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Process MinIO events from queue and update Atlas
 * Uses thread pool for async processing with retry logic
 */
@Component
public class MinioEventHandler {
    private static final Logger LOG = LoggerFactory.getLogger(MinioEventHandler.class);
    private static final int SHUTDOWN_WAIT_SECONDS = 60;

    private final EventQueue eventQueue;
    private final MinIOBridge minioBridge;
    private final int threadPoolSize;
    private final int maxRetries;
    private final long retryDelay;

    private ExecutorService executorService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private final AtomicLong retriedCount = new AtomicLong(0);
    private final LinkedBlockingQueue<MinioEvent> deadLetterQueue = new LinkedBlockingQueue<>();

    /**
     * Constructor with dependency injection
     */
    @Autowired
    public MinioEventHandler(
            EventQueue eventQueue,
            MinIOBridge minioBridge,
            @Value("${" + MinIOConstants.CONFIG_EVENT_THREAD_POOL_SIZE + ":" + MinIOConstants.DEFAULT_EVENT_THREAD_POOL_SIZE + "}") int threadPoolSize,
            @Value("${" + MinIOConstants.CONFIG_EVENT_RETRY_MAX + ":" + MinIOConstants.DEFAULT_EVENT_RETRY_MAX + "}") int maxRetries,
            @Value("${" + MinIOConstants.CONFIG_EVENT_RETRY_DELAY + ":" + MinIOConstants.DEFAULT_EVENT_RETRY_DELAY + "}") long retryDelay) {
        this.eventQueue = eventQueue;
        this.minioBridge = minioBridge;
        this.threadPoolSize = threadPoolSize;
        this.maxRetries = maxRetries;
        this.retryDelay = retryDelay;
    }

    @PostConstruct
    public void init() {
        LOG.info("MinioEventHandler initializing with thread pool size: {}, max retries: {}, retry delay: {}ms",
                threadPoolSize, maxRetries, retryDelay);
        start();
    }

    @PreDestroy
    public void destroy() {
        LOG.info("MinioEventHandler shutting down...");
        stop();
    }

    /**
     * Start the event processor thread pool
     */
    public void start() {
        if (running.get()) {
            LOG.warn("MinioEventHandler is already running");
            return;
        }

        LOG.info("Starting MinioEventHandler with {} worker threads", threadPoolSize);

        // Create thread pool with custom rejection policy
        executorService = new ThreadPoolExecutor(
                threadPoolSize,
                threadPoolSize,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        // Start worker threads
        for (int i = 0; i < threadPoolSize; i++) {
            final int workerId = i + 1;
            executorService.submit(() -> runWorker(workerId));
        }

        running.set(true);
        LOG.info("MinioEventHandler started successfully");
    }

    /**
     * Graceful shutdown
     */
    public void stop() {
        if (!running.get()) {
            LOG.warn("MinioEventHandler is not running");
            return;
        }

        LOG.info("Stopping MinioEventHandler...");
        running.set(false);

        // Shutdown executor service
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                    LOG.warn("Executor did not terminate in {} seconds, forcing shutdown", SHUTDOWN_WAIT_SECONDS);
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                LOG.error("Interrupted while waiting for executor to terminate", e);
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Log final statistics
        HandlerStats stats = getStats();
        LOG.info("MinioEventHandler stopped. Final stats: {}", stats);
        LOG.info("Dead letter queue size: {}", deadLetterQueue.size());
    }

    /**
     * Worker thread that processes events from the queue
     */
    private void runWorker(int workerId) {
        LOG.info("Worker thread {} started", workerId);
        Thread.currentThread().setName("MinioEventHandler-Worker-" + workerId);

        while (running.get()) {
            try {
                // Dequeue event with timeout
                MinioEvent event = eventQueue.dequeue(5, TimeUnit.SECONDS);
                if (event == null) {
                    // No event available, continue loop
                    continue;
                }

                // Process the event
                processEventWithRetry(event, workerId);

            } catch (InterruptedException e) {
                LOG.warn("Worker {} interrupted", workerId);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOG.error("Unexpected error in worker {}", workerId, e);
            }
        }

        LOG.info("Worker thread {} stopped", workerId);
    }

    /**
     * Process event with retry logic
     */
    private void processEventWithRetry(MinioEvent event, int workerId) {
        int attempt = 0;
        boolean success = false;

        while (attempt <= maxRetries && !success) {
            try {
                attempt++;
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Worker {} processing event (attempt {}/{}): {}",
                            workerId, attempt, maxRetries, event);
                }

                processEvent(event);
                success = true;
                processedCount.incrementAndGet();

                if (attempt > 1) {
                    retriedCount.incrementAndGet();
                    LOG.info("Event processing succeeded on attempt {}: {}", attempt, event);
                }

            } catch (Exception e) {
                LOG.warn("Failed to process event (attempt {}/{}): {}",
                        attempt, maxRetries, event, e);

                if (attempt <= maxRetries) {
                    // Retry after delay
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        LOG.warn("Retry delay interrupted for event: {}", event);
                        break;
                    }
                }
            }
        }

        if (!success) {
            // Move to dead letter queue
            failedCount.incrementAndGet();
            deadLetterQueue.offer(event);
            LOG.error("Event processing failed after {} attempts, moved to dead letter queue: {}",
                    maxRetries + 1, event);
        }
    }

    /**
     * Handle a single event
     */
    public void processEvent(MinioEvent event) throws Exception {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        LOG.debug("Processing event: type={}, bucket={}, object={}",
                event.getEventType(), event.getBucketName(), event.getObjectPath());

        try {
            // Parse event type and take appropriate action
            if (event.isObjectCreated()) {
                handleObjectCreated(event);
            } else if (event.isObjectRemoved()) {
                handleObjectRemoved(event);
            } else if (event.isObjectUpdated()) {
                handleObjectUpdated(event);
            } else {
                LOG.warn("Unknown event type: {}", event.getEventType());
            }

            LOG.debug("Successfully processed event: {}", event);

        } catch (Exception e) {
            LOG.error("Failed to process event: {}", event, e);
            throw e;
        }
    }

    /**
     * Handle ObjectCreated event
     * Import new entity to Atlas
     */
    private void handleObjectCreated(MinioEvent event) throws Exception {
        LOG.info("Handling ObjectCreated event: {}/{}", event.getBucketName(), event.getObjectPath());

        // Create MinioObject from event
        MinioObject object = createMinioObjectFromEvent(event);

        // Import to Atlas using MinIOBridge
        minioBridge.importObjectToAtlas(object);

        LOG.info("Successfully imported object to Atlas: {}/{}",
                event.getBucketName(), event.getObjectPath());
    }

    /**
     * Handle ObjectRemoved event
     * Delete entity from Atlas
     */
    private void handleObjectRemoved(MinioEvent event) throws Exception {
        LOG.info("Handling ObjectRemoved event: {}/{}", event.getBucketName(), event.getObjectPath());

        // Delete from Atlas
        // Note: MinIOBridge doesn't have a delete method yet, so we'll log this
        // This will be implemented when we add delete functionality
        LOG.warn("Object removal not yet implemented for: {}/{}",
                event.getBucketName(), event.getObjectPath());

        // TODO: Implement delete from Atlas when MinIOBridge.deleteObject() is available
    }

    /**
     * Handle ObjectUpdated event
     * Update existing entity in Atlas
     */
    private void handleObjectUpdated(MinioEvent event) throws Exception {
        LOG.info("Handling ObjectUpdated event: {}/{}", event.getBucketName(), event.getObjectPath());

        // Create MinioObject from event
        MinioObject object = createMinioObjectFromEvent(event);

        // Import to Atlas using MinIOBridge (will update if exists)
        minioBridge.importObjectToAtlas(object);

        LOG.info("Successfully updated object in Atlas: {}/{}",
                event.getBucketName(), event.getObjectPath());
    }

    /**
     * Create MinioObject from event
     * This is a simplified version - in production, you'd fetch full metadata from MinIO
     */
    private MinioObject createMinioObjectFromEvent(MinioEvent event) {
        MinioObject object = new MinioObject();
        object.setBucketName(event.getBucketName());
        object.setPath(event.getObjectPath());
        object.setLastModified(event.getEventTime());

        // In production, you would fetch full metadata from MinIO here
        // For now, we use the basic information from the event
        // The importObjectToAtlas method will fetch additional details if needed

        return object;
    }

    /**
     * Check if processor is active
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Get statistics
     */
    public HandlerStats getStats() {
        return new HandlerStats(
                processedCount.get(),
                failedCount.get(),
                retriedCount.get(),
                deadLetterQueue.size(),
                threadPoolSize,
                running.get()
        );
    }

    /**
     * Get dead letter queue for inspection
     */
    public List<MinioEvent> getDeadLetterQueueItems() {
        return new ArrayList<>(deadLetterQueue);
    }

    /**
     * Clear dead letter queue
     */
    public void clearDeadLetterQueue() {
        int size = deadLetterQueue.size();
        deadLetterQueue.clear();
        LOG.info("Cleared {} items from dead letter queue", size);
    }

    /**
     * Handler statistics
     */
    public static class HandlerStats {
        private final long processed;
        private final long failed;
        private final long retried;
        private final int deadLetterQueueSize;
        private final int threadPoolSize;
        private final boolean running;

        public HandlerStats(long processed, long failed, long retried,
                           int deadLetterQueueSize, int threadPoolSize, boolean running) {
            this.processed = processed;
            this.failed = failed;
            this.retried = retried;
            this.deadLetterQueueSize = deadLetterQueueSize;
            this.threadPoolSize = threadPoolSize;
            this.running = running;
        }

        public long getProcessed() {
            return processed;
        }

        public long getFailed() {
            return failed;
        }

        public long getRetried() {
            return retried;
        }

        public int getDeadLetterQueueSize() {
            return deadLetterQueueSize;
        }

        public int getThreadPoolSize() {
            return threadPoolSize;
        }

        public boolean isRunning() {
            return running;
        }

        public long getTotal() {
            return processed + failed;
        }

        @Override
        public String toString() {
            return "HandlerStats{" +
                    "processed=" + processed +
                    ", failed=" + failed +
                    ", retried=" + retried +
                    ", deadLetterQueueSize=" + deadLetterQueueSize +
                    ", threadPoolSize=" + threadPoolSize +
                    ", running=" + running +
                    ", total=" + getTotal() +
                    '}';
        }
    }
}
