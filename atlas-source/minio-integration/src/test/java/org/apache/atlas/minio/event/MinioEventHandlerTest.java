package org.apache.atlas.minio.event;

import org.apache.atlas.minio.bridge.MinIOBridge;
import org.apache.atlas.minio.model.MinioEvent;
import org.apache.atlas.minio.model.MinioObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MinioEventHandler
 */
class MinioEventHandlerTest {

    private EventQueue eventQueue;
    private MinIOBridge minioBridge;
    private MinioEventHandler handler;
    private static final int THREAD_POOL_SIZE = 2;
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_DELAY = 100; // ms

    @BeforeEach
    void setUp() throws Exception {
        eventQueue = new EventQueue(100);
        minioBridge = mock(MinIOBridge.class);

        // Create handler with test parameters
        handler = new MinioEventHandler(
                eventQueue,
                minioBridge,
                THREAD_POOL_SIZE,
                MAX_RETRIES,
                RETRY_DELAY
        );

        // Initialize handler without starting
        // We'll manually control start/stop in tests
    }

    @AfterEach
    void tearDown() {
        if (handler != null && handler.isRunning()) {
            handler.stop();
        }
    }

    @Test
    void testInitialState() {
        assertFalse(handler.isRunning());

        MinioEventHandler.HandlerStats stats = handler.getStats();
        assertEquals(0, stats.getProcessed());
        assertEquals(0, stats.getFailed());
        assertEquals(0, stats.getRetried());
        assertEquals(0, stats.getDeadLetterQueueSize());
        assertFalse(stats.isRunning());
    }

    @Test
    void testStartHandler() {
        handler.start();

        assertTrue(handler.isRunning());

        MinioEventHandler.HandlerStats stats = handler.getStats();
        assertTrue(stats.isRunning());
        assertEquals(THREAD_POOL_SIZE, stats.getThreadPoolSize());
    }

    @Test
    void testStopHandler() throws InterruptedException {
        handler.start();
        assertTrue(handler.isRunning());

        handler.stop();

        assertFalse(handler.isRunning());
    }

    @Test
    void testProcessObjectCreatedEvent() throws Exception {
        // Setup
        handler.start();
        doNothing().when(minioBridge).importObjectToAtlas(any(MinioObject.class));

        // Create and enqueue event
        MinioEvent event = createTestEvent("s3:ObjectCreated:Put", "test-bucket", "test-object.txt");
        eventQueue.enqueue(event);

        // Wait for processing
        Thread.sleep(1000);

        // Verify
        ArgumentCaptor<MinioObject> captor = ArgumentCaptor.forClass(MinioObject.class);
        verify(minioBridge, timeout(5000).atLeastOnce()).importObjectToAtlas(captor.capture());

        MinioObject importedObject = captor.getValue();
        assertEquals("test-bucket", importedObject.getBucketName());
        assertEquals("test-object.txt", importedObject.getPath());

        MinioEventHandler.HandlerStats stats = handler.getStats();
        assertTrue(stats.getProcessed() > 0);
        assertEquals(0, stats.getFailed());
    }

    @Test
    void testProcessObjectUpdatedEvent() throws Exception {
        // Setup
        handler.start();
        doNothing().when(minioBridge).importObjectToAtlas(any(MinioObject.class));

        // Create and enqueue event
        MinioEvent event = createTestEvent("s3:ObjectUpdated:Post", "test-bucket", "test-object.txt");
        eventQueue.enqueue(event);

        // Wait for processing
        Thread.sleep(1000);

        // Verify
        ArgumentCaptor<MinioObject> captor = ArgumentCaptor.forClass(MinioObject.class);
        verify(minioBridge, timeout(5000).atLeastOnce()).importObjectToAtlas(captor.capture());

        MinioEventHandler.HandlerStats stats = handler.getStats();
        assertTrue(stats.getProcessed() > 0);
    }

    @Test
    void testProcessObjectRemovedEvent() throws Exception {
        // Setup
        handler.start();

        // Create and enqueue event
        MinioEvent event = createTestEvent("s3:ObjectRemoved:Delete", "test-bucket", "test-object.txt");
        eventQueue.enqueue(event);

        // Wait for processing
        Thread.sleep(1000);

        // Object removal is logged but not yet implemented
        // So we should see the event processed but no delete call
        MinioEventHandler.HandlerStats stats = handler.getStats();
        assertTrue(stats.getProcessed() > 0);
    }

    @Test
    void testRetryOnFailure() throws Exception {
        // Setup
        handler.start();
        AtomicInteger attemptCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt < MAX_RETRIES) {
                throw new RuntimeException("Simulated failure");
            }
            return null;
        }).when(minioBridge).importObjectToAtlas(any(MinioObject.class));

        // Create and enqueue event
        MinioEvent event = createTestEvent("s3:ObjectCreated:Put", "test-bucket", "test-object.txt");
        eventQueue.enqueue(event);

        // Wait for processing (including retries)
        Thread.sleep((MAX_RETRIES + 1) * RETRY_DELAY + 1000);

        // Verify retries occurred
        verify(minioBridge, atLeast(MAX_RETRIES)).importObjectToAtlas(any(MinioObject.class));

        MinioEventHandler.HandlerStats stats = handler.getStats();
        assertTrue(stats.getProcessed() > 0);
        assertTrue(stats.getRetried() > 0);
    }

    @Test
    void testMaxRetriesExceeded() throws Exception {
        // Setup
        handler.start();
        doThrow(new RuntimeException("Persistent failure"))
                .when(minioBridge).importObjectToAtlas(any(MinioObject.class));

        // Create and enqueue event
        MinioEvent event = createTestEvent("s3:ObjectCreated:Put", "test-bucket", "test-object.txt");
        eventQueue.enqueue(event);

        // Wait for processing (including retries)
        Thread.sleep((MAX_RETRIES + 2) * RETRY_DELAY + 1000);

        // Verify event moved to dead letter queue
        MinioEventHandler.HandlerStats stats = handler.getStats();
        assertTrue(stats.getFailed() > 0);
        assertTrue(stats.getDeadLetterQueueSize() > 0);

        List<MinioEvent> deadLetterEvents = handler.getDeadLetterQueueItems();
        assertFalse(deadLetterEvents.isEmpty());
        assertEquals(event.getBucketName(), deadLetterEvents.get(0).getBucketName());
    }

    @Test
    void testProcessMultipleEvents() throws Exception {
        // Setup
        handler.start();
        doNothing().when(minioBridge).importObjectToAtlas(any(MinioObject.class));

        // Create and enqueue multiple events
        int eventCount = 10;
        for (int i = 0; i < eventCount; i++) {
            MinioEvent event = createTestEvent(
                    "s3:ObjectCreated:Put",
                    "test-bucket",
                    "test-object-" + i + ".txt"
            );
            eventQueue.enqueue(event);
        }

        // Wait for processing
        Thread.sleep(3000);

        // Verify all events processed
        MinioEventHandler.HandlerStats stats = handler.getStats();
        assertTrue(stats.getProcessed() >= eventCount);
        assertEquals(0, stats.getFailed());
    }

    @Test
    void testConcurrentEventProcessing() throws Exception {
        // Setup
        handler.start();
        AtomicInteger processedCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            processedCount.incrementAndGet();
            Thread.sleep(50); // Simulate processing time
            return null;
        }).when(minioBridge).importObjectToAtlas(any(MinioObject.class));

        // Create and enqueue multiple events concurrently
        int eventCount = 20;
        for (int i = 0; i < eventCount; i++) {
            MinioEvent event = createTestEvent(
                    "s3:ObjectCreated:Put",
                    "test-bucket",
                    "test-object-" + i + ".txt"
            );
            eventQueue.enqueue(event);
        }

        // Wait for processing
        Thread.sleep(5000);

        // Verify events were processed concurrently
        MinioEventHandler.HandlerStats stats = handler.getStats();
        assertTrue(stats.getProcessed() >= eventCount);
    }

    @Test
    void testDeadLetterQueue() throws Exception {
        // Setup
        handler.start();
        doThrow(new RuntimeException("Persistent failure"))
                .when(minioBridge).importObjectToAtlas(any(MinioObject.class));

        // Create events that will fail
        int failCount = 3;
        for (int i = 0; i < failCount; i++) {
            MinioEvent event = createTestEvent(
                    "s3:ObjectCreated:Put",
                    "test-bucket",
                    "failed-object-" + i + ".txt"
            );
            eventQueue.enqueue(event);
        }

        // Wait for processing and retries
        Thread.sleep((MAX_RETRIES + 2) * RETRY_DELAY + 2000);

        // Check dead letter queue
        List<MinioEvent> deadLetterEvents = handler.getDeadLetterQueueItems();
        assertTrue(deadLetterEvents.size() >= failCount);

        MinioEventHandler.HandlerStats stats = handler.getStats();
        assertTrue(stats.getDeadLetterQueueSize() >= failCount);
    }

    @Test
    void testClearDeadLetterQueue() throws Exception {
        // Setup
        handler.start();
        doThrow(new RuntimeException("Persistent failure"))
                .when(minioBridge).importObjectToAtlas(any(MinioObject.class));

        // Create event that will fail
        MinioEvent event = createTestEvent("s3:ObjectCreated:Put", "test-bucket", "failed-object.txt");
        eventQueue.enqueue(event);

        // Wait for processing and retries
        Thread.sleep((MAX_RETRIES + 2) * RETRY_DELAY + 1000);

        // Verify dead letter queue has items
        assertTrue(handler.getStats().getDeadLetterQueueSize() > 0);

        // Clear dead letter queue
        handler.clearDeadLetterQueue();

        // Verify cleared
        assertEquals(0, handler.getStats().getDeadLetterQueueSize());
        assertTrue(handler.getDeadLetterQueueItems().isEmpty());
    }

    @Test
    void testProcessNullEvent() throws Exception {
        handler.start();

        assertThrows(IllegalArgumentException.class, () -> {
            handler.processEvent(null);
        });
    }

    @Test
    void testUnknownEventType() throws Exception {
        handler.start();
        doNothing().when(minioBridge).importObjectToAtlas(any(MinioObject.class));

        // Create event with unknown type
        MinioEvent event = createTestEvent("s3:UnknownEvent", "test-bucket", "test-object.txt");
        eventQueue.enqueue(event);

        // Wait for processing
        Thread.sleep(1000);

        // Event should be processed (no exception) but no import should occur
        verify(minioBridge, never()).importObjectToAtlas(any(MinioObject.class));

        MinioEventHandler.HandlerStats stats = handler.getStats();
        assertTrue(stats.getProcessed() > 0);
    }

    @Test
    void testGracefulShutdown() throws InterruptedException {
        handler.start();

        // Enqueue some events
        for (int i = 0; i < 5; i++) {
            eventQueue.enqueue(createTestEvent("s3:ObjectCreated:Put", "bucket", "object" + i));
        }

        // Stop handler
        handler.stop();

        assertFalse(handler.isRunning());

        // Verify stats are accessible after shutdown
        MinioEventHandler.HandlerStats stats = handler.getStats();
        assertNotNull(stats);
        assertFalse(stats.isRunning());
    }

    @Test
    void testStatsConsistency() throws Exception {
        handler.start();
        doNothing().when(minioBridge).importObjectToAtlas(any(MinioObject.class));

        // Enqueue successful events
        for (int i = 0; i < 3; i++) {
            eventQueue.enqueue(createTestEvent("s3:ObjectCreated:Put", "bucket", "object" + i));
        }

        // Enqueue failing event
        doThrow(new RuntimeException("Failure"))
                .when(minioBridge).importObjectToAtlas(any(MinioObject.class));
        eventQueue.enqueue(createTestEvent("s3:ObjectCreated:Put", "bucket", "failed-object"));

        // Wait for processing
        Thread.sleep((MAX_RETRIES + 2) * RETRY_DELAY + 2000);

        MinioEventHandler.HandlerStats stats = handler.getStats();
        assertEquals(stats.getProcessed() + stats.getFailed(), stats.getTotal());
    }

    // Helper methods

    private MinioEvent createTestEvent(String eventType, String bucketName, String objectPath) {
        MinioEvent event = new MinioEvent();
        event.setEventType(eventType);
        event.setEventTime(new Date());
        event.setBucketName(bucketName);
        event.setObjectPath(objectPath);
        event.setEventName("TestEvent");
        event.setSourceIPAddress("127.0.0.1");
        return event;
    }
}
