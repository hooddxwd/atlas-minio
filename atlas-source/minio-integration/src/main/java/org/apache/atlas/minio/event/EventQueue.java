package org.apache.atlas.minio.event;

import org.apache.atlas.minio.model.MinioEvent;
import org.apache.atlas.minio.utils.MinIOConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe bounded queue for async MinIO event processing
 * Uses BlockingQueue to prevent OOM and provide thread-safe operations
 */
@Component
public class EventQueue {
    private static final Logger LOG = LoggerFactory.getLogger(EventQueue.class);

    private final int capacity;
    private final BlockingQueue<MinioEvent> queue;
    private final AtomicLong enqueuedCount = new AtomicLong(0);
    private final AtomicLong dequeuedCount = new AtomicLong(0);
    private final AtomicLong droppedCount = new AtomicLong(0);
    private volatile boolean running = true;

    /**
     * Constructor with configurable capacity
     */
    public EventQueue(@Value("${" + MinIOConstants.CONFIG_EVENT_QUEUE_CAPACITY + ":" + MinIOConstants.DEFAULT_EVENT_QUEUE_CAPACITY + "}") int capacity) {
        this.capacity = capacity;
        this.queue = new ArrayBlockingQueue<>(capacity);
        LOG.info("EventQueue initialized with capacity: {}", capacity);
    }

    @PostConstruct
    public void init() {
        LOG.info("EventQueue starting with capacity: {}", capacity);
    }

    @PreDestroy
    public void destroy() {
        LOG.info("EventQueue shutting down. Final stats - enqueued: {}, dequeued: {}, dropped: {}, size: {}",
                enqueuedCount.get(), dequeuedCount.get(), droppedCount.get(), queue.size());
        running = false;
    }

    /**
     * Add event to queue
     * If queue is full, drops oldest event and logs warning
     *
     * @param event Event to enqueue
     * @return true if enqueued successfully, false if queue is full and couldn't add
     */
    public boolean enqueue(MinioEvent event) {
        if (!running) {
            LOG.warn("EventQueue is not running, rejecting event: {}", event);
            return false;
        }

        if (event == null) {
            LOG.warn("Attempted to enqueue null event, ignoring");
            return false;
        }

        try {
            // Try to offer without blocking first
            if (queue.offer(event)) {
                long count = enqueuedCount.incrementAndGet();
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Enqueued event: {}, queue size: {}, total enqueued: {}",
                            event, queue.size(), count);
                }
                return true;
            }

            // Queue is full, drop oldest event and add new one
            MinioEvent dropped = queue.poll();
            if (dropped != null) {
                droppedCount.incrementAndGet();
                LOG.warn("EventQueue is full (capacity: {}), dropped oldest event: {}, adding new event: {}",
                        capacity, dropped, event);
            }

            // Now try to add the new event
            if (queue.offer(event)) {
                enqueuedCount.incrementAndGet();
                return true;
            }

            // Still couldn't add
            LOG.error("Failed to enqueue event even after dropping oldest: {}", event);
            return false;

        } catch (Exception e) {
            LOG.error("Error enqueuing event: {}", event, e);
            return false;
        }
    }

    /**
     * Remove and return event from head of queue
     * Blocks if empty with timeout
     *
     * @param timeout Maximum time to wait
     * @param unit Time unit
     * @return Event or null if timeout occurs
     */
    public MinioEvent dequeue(long timeout, TimeUnit unit) throws InterruptedException {
        if (!running) {
            LOG.debug("EventQueue is not running, returning null from dequeue");
            return null;
        }

        try {
            MinioEvent event = queue.poll(timeout, unit);
            if (event != null) {
                long count = dequeuedCount.incrementAndGet();
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Dequeued event: {}, queue size: {}, total dequeued: {}",
                            event, queue.size(), count);
                }
            }
            return event;
        } catch (InterruptedException e) {
            LOG.warn("Dequeue interrupted", e);
            throw e;
        }
    }

    /**
     * Remove and return event from head of queue without blocking
     *
     * @return Event or null if queue is empty
     */
    public MinioEvent dequeue() {
        if (!running) {
            return null;
        }

        MinioEvent event = queue.poll();
        if (event != null) {
            long count = dequeuedCount.incrementAndGet();
            if (LOG.isTraceEnabled()) {
                LOG.trace("Dequeued event: {}, queue size: {}, total dequeued: {}",
                        event, queue.size(), count);
            }
        }
        return event;
    }

    /**
     * Get current queue size
     */
    public int size() {
        return queue.size();
    }

    /**
     * Check if queue is empty
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * Check if queue is full
     */
    public boolean isFull() {
        return queue.remainingCapacity() == 0;
    }

    /**
     * Get remaining capacity
     */
    public int getRemainingCapacity() {
        return queue.remainingCapacity();
    }

    /**
     * Clear all events from queue
     */
    public void clear() {
        int size = queue.size();
        queue.clear();
        LOG.info("Cleared {} events from queue", size);
    }

    /**
     * Get max capacity
     */
    public int getCapacity() {
        return capacity;
    }

    /**
     * Check if queue is running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Get statistics
     */
    public QueueStats getStats() {
        return new QueueStats(
                enqueuedCount.get(),
                dequeuedCount.get(),
                droppedCount.get(),
                queue.size(),
                capacity
        );
    }

    /**
     * Queue statistics
     */
    public static class QueueStats {
        private final long enqueued;
        private final long dequeued;
        private final long dropped;
        private final int currentSize;
        private final int capacity;

        public QueueStats(long enqueued, long dequeued, long dropped, int currentSize, int capacity) {
            this.enqueued = enqueued;
            this.dequeued = dequeued;
            this.dropped = dropped;
            this.currentSize = currentSize;
            this.capacity = capacity;
        }

        public long getEnqueued() {
            return enqueued;
        }

        public long getDequeued() {
            return dequeued;
        }

        public long getDropped() {
            return dropped;
        }

        public int getCurrentSize() {
            return currentSize;
        }

        public int getCapacity() {
            return capacity;
        }

        public long getPending() {
            return enqueued - dequeued;
        }

        @Override
        public String toString() {
            return "QueueStats{" +
                    "enqueued=" + enqueued +
                    ", dequeued=" + dequeued +
                    ", dropped=" + dropped +
                    ", pending=" + getPending() +
                    ", currentSize=" + currentSize +
                    ", capacity=" + capacity +
                    '}';
        }
    }
}
