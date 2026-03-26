package org.apache.atlas.minio.event;

import org.apache.atlas.minio.model.MinioEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EventQueue
 */
class EventQueueTest {

    private EventQueue eventQueue;
    private static final int TEST_CAPACITY = 100;

    @BeforeEach
    void setUp() {
        eventQueue = new EventQueue(TEST_CAPACITY);
    }

    @Test
    void testInitialCapacity() {
        assertEquals(TEST_CAPACITY, eventQueue.getCapacity());
        assertEquals(0, eventQueue.size());
        assertTrue(eventQueue.isEmpty());
        assertFalse(eventQueue.isFull());
    }

    @Test
    void testEnqueueSingleEvent() {
        MinioEvent event = createTestEvent("bucket1", "object1");
        boolean result = eventQueue.enqueue(event);

        assertTrue(result);
        assertEquals(1, eventQueue.size());
        assertFalse(eventQueue.isEmpty());
    }

    @Test
    void testEnqueueNullEvent() {
        boolean result = eventQueue.enqueue(null);

        assertFalse(result);
        assertEquals(0, eventQueue.size());
    }

    @Test
    void testEnqueueMultipleEvents() {
        int count = 10;
        for (int i = 0; i < count; i++) {
            MinioEvent event = createTestEvent("bucket" + i, "object" + i);
            boolean result = eventQueue.enqueue(event);
            assertTrue(result);
        }

        assertEquals(count, eventQueue.size());
    }

    @Test
    void testDequeueSingleEvent() {
        MinioEvent event = createTestEvent("bucket1", "object1");
        eventQueue.enqueue(event);

        MinioEvent dequeued = eventQueue.dequeue();

        assertNotNull(dequeued);
        assertEquals(event, dequeued);
        assertEquals(0, eventQueue.size());
    }

    @Test
    void testDequeueFromEmptyQueue() {
        MinioEvent dequeued = eventQueue.dequeue();

        assertNull(dequeued);
    }

    @Test
    void testDequeueWithTimeout() throws InterruptedException {
        MinioEvent event = createTestEvent("bucket1", "object1");
        eventQueue.enqueue(event);

        MinioEvent dequeued = eventQueue.dequeue(1, TimeUnit.SECONDS);

        assertNotNull(dequeued);
        assertEquals(event, dequeued);
    }

    @Test
    void testDequeueWithTimeoutEmptyQueue() throws InterruptedException {
        MinioEvent dequeued = eventQueue.dequeue(100, TimeUnit.MILLISECONDS);

        assertNull(dequeued);
    }

    @Test
    void testFifoOrder() {
        MinioEvent event1 = createTestEvent("bucket1", "object1");
        MinioEvent event2 = createTestEvent("bucket2", "object2");
        MinioEvent event3 = createTestEvent("bucket3", "object3");

        eventQueue.enqueue(event1);
        eventQueue.enqueue(event2);
        eventQueue.enqueue(event3);

        assertEquals(event1, eventQueue.dequeue());
        assertEquals(event2, eventQueue.dequeue());
        assertEquals(event3, eventQueue.dequeue());
    }

    @Test
    void testQueueFull() {
        // Fill the queue to capacity
        for (int i = 0; i < TEST_CAPACITY; i++) {
            MinioEvent event = createTestEvent("bucket" + i, "object" + i);
            assertTrue(eventQueue.enqueue(event));
        }

        assertTrue(eventQueue.isFull());
        assertEquals(0, eventQueue.getRemainingCapacity());
    }

    @Test
    void testQueueFullDropsOldest() {
        // Fill the queue to capacity
        for (int i = 0; i < TEST_CAPACITY; i++) {
            MinioEvent event = createTestEvent("bucket" + i, "object" + i);
            eventQueue.enqueue(event);
        }

        assertTrue(eventQueue.isFull());

        // Add one more event - should drop oldest
        MinioEvent newEvent = createTestEvent("new-bucket", "new-object");
        boolean result = eventQueue.enqueue(newEvent);

        // Should succeed
        assertTrue(result);
        assertEquals(TEST_CAPACITY, eventQueue.size());

        // First event should be dropped
        MinioEvent dequeued = eventQueue.dequeue();
        assertNotEquals("bucket0", dequeued.getBucketName());
    }

    @Test
    void testClear() {
        for (int i = 0; i < 10; i++) {
            eventQueue.enqueue(createTestEvent("bucket" + i, "object" + i));
        }

        assertEquals(10, eventQueue.size());

        eventQueue.clear();

        assertEquals(0, eventQueue.size());
        assertTrue(eventQueue.isEmpty());
    }

    @Test
    void testGetStats() {
        for (int i = 0; i < 5; i++) {
            eventQueue.enqueue(createTestEvent("bucket" + i, "object" + i));
        }

        eventQueue.dequeue();
        eventQueue.dequeue();

        EventQueue.QueueStats stats = eventQueue.getStats();

        assertEquals(5, stats.getEnqueued());
        assertEquals(2, stats.getDequeued());
        assertEquals(3, stats.getPending());
        assertEquals(3, stats.getCurrentSize());
        assertEquals(TEST_CAPACITY, stats.getCapacity());
    }

    @Test
    void testConcurrentEnqueue() throws InterruptedException {
        int threadCount = 10;
        int eventsPerThread = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    for (int j = 0; j < eventsPerThread; j++) {
                        MinioEvent event = createTestEvent("bucket", "object" + j);
                        if (eventQueue.enqueue(event)) {
                            successCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads
        assertTrue(endLatch.await(10, TimeUnit.SECONDS));

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Some events might be dropped due to capacity
        assertTrue(successCount.get() > 0);
        assertTrue(eventQueue.size() <= TEST_CAPACITY);
    }

    @Test
    void testConcurrentEnqueueDequeue() throws InterruptedException {
        int producerCount = 5;
        int consumerCount = 3;
        int iterations = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(producerCount + consumerCount);

        ExecutorService executor = Executors.newFixedThreadPool(producerCount + consumerCount);

        // Producers
        for (int i = 0; i < producerCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < iterations; j++) {
                        MinioEvent event = createTestEvent("bucket", "object" + j);
                        eventQueue.enqueue(event);
                        Thread.sleep(1); // Small delay
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Consumers
        for (int i = 0; i < consumerCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < iterations; j++) {
                        eventQueue.dequeue();
                        Thread.sleep(1); // Small delay
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(30, TimeUnit.SECONDS));

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Verify stats are consistent
        EventQueue.QueueStats stats = eventQueue.getStats();
        assertTrue(stats.getEnqueued() > 0);
        assertTrue(stats.getDequeued() >= 0);
    }

    @Test
    void testShutdown() {
        eventQueue.enqueue(createTestEvent("bucket1", "object1"));

        // Simulate shutdown
        ReflectionTestUtils.invokeMethod(eventQueue, "destroy");

        assertFalse(eventQueue.isRunning());

        // Should reject new events after shutdown
        MinioEvent newEvent = createTestEvent("bucket2", "object2");
        boolean result = eventQueue.enqueue(newEvent);
        assertFalse(result);
    }

    @Test
    void testRemainingCapacity() {
        assertEquals(TEST_CAPACITY, eventQueue.getRemainingCapacity());

        for (int i = 0; i < 10; i++) {
            eventQueue.enqueue(createTestEvent("bucket" + i, "object" + i));
        }

        assertEquals(TEST_CAPACITY - 10, eventQueue.getRemainingCapacity());
    }

    // Helper methods

    private MinioEvent createTestEvent(String bucketName, String objectPath) {
        MinioEvent event = new MinioEvent();
        event.setEventType("s3:ObjectCreated:Put");
        event.setEventTime(new Date());
        event.setBucketName(bucketName);
        event.setObjectPath(objectPath);
        event.setEventName("ObjectCreated:Put");
        event.setSourceIPAddress("127.0.0.1");
        return event;
    }
}
