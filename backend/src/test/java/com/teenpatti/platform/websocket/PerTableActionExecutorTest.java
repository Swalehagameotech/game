package com.teenpatti.platform.websocket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerTableActionExecutorTest {

    @Test
    @DisplayName("Rapid-fire actions for the SAME table are executed in strict serial order")
    void executeTableAction_SameTable_SerialExecution() throws Exception {
        PerTableActionExecutor executor = new PerTableActionExecutor();
        String tableId = "table_serial_1";

        List<Integer> executionOrder = Collections.synchronizedList(new ArrayList<>());
        int totalTasks = 50;

        CountDownLatch latch = new CountDownLatch(totalTasks);

        for (int i = 0; i < totalTasks; i++) {
            final int index = i;
            executor.submitTableAction(tableId, () -> {
                executionOrder.add(index);
                latch.countDown();
            });
        }

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue(completed, "All tasks must complete within timeout");
        assertEquals(totalTasks, executionOrder.size());

        for (int i = 0; i < totalTasks; i++) {
            assertEquals(i, executionOrder.get(i), "Tasks for the same table must execute in exact arrival order");
        }
    }

    @Test
    @DisplayName("Simultaneous actions across DIFFERENT tables process in parallel")
    void executeTableAction_DifferentTables_ParallelExecution() throws Exception {
        PerTableActionExecutor executor = new PerTableActionExecutor();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(2);

        List<String> log = Collections.synchronizedList(new ArrayList<>());

        executor.submitTableAction("table_A", () -> {
            try {
                startLatch.await();
                Thread.sleep(100);
                log.add("table_A_done");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                finishLatch.countDown();
            }
        });

        executor.submitTableAction("table_B", () -> {
            try {
                startLatch.await();
                Thread.sleep(100);
                log.add("table_B_done");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                finishLatch.countDown();
            }
        });

        long start = System.currentTimeMillis();
        startLatch.countDown(); // Start both table tasks simultaneously
        boolean completed = finishLatch.await(5, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(completed);
        assertEquals(2, log.size());
        assertTrue(elapsed < 180, "Parallel execution across different tables should complete in ~100ms, not 200ms");
    }
}
