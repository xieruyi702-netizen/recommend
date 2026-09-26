package com.rs.gateway.engine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ParallelGroupTest {

    private Operator op(String name, Runnable action) {
        return new Operator() {
            @Override public String name() { return name; }
            @Override public void execute(FlowContext ctx) { action.run(); }
        };
    }

    @Test
    void allOperatorsInGroupShouldExecute() {
        Set<String> executed = ConcurrentHashMap.newKeySet();
        var group = new ParallelGroup("g", Executors.newFixedThreadPool(2),
                op("a", () -> executed.add("a")),
                op("b", () -> executed.add("b")));

        group.execute(new FlowContext(1, 10));

        assertEquals(Set.of("a", "b"), executed);
    }

    @Test
    void operatorsShouldRunInParallel() {
        AtomicBoolean firstStarted = new AtomicBoolean(false);
        var group = new ParallelGroup("g", Executors.newFixedThreadPool(2),
                op("slow", () -> { 
                    while (!firstStarted.get()) Thread.onSpinWait();
                }),
                op("fast", () -> firstStarted.set(true)));

        group.execute(new FlowContext(1, 10));   // slow 等 fast 启动才结束 → 必然真并行，否则死锁超时
    }

    @Test
    void failureShouldPropagate() {
        var group = new ParallelGroup("g", Executors.newFixedThreadPool(2),
                op("bad", () -> { throw new IllegalStateException("boom"); }),
                op("ok", () -> { }));

        var e = assertThrows(IllegalStateException.class, () -> group.execute(new FlowContext(1, 10)));
        assertEquals("boom", e.getMessage());
    }

    @Test
    void emptyGroupShouldBeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ParallelGroup("g", Executors.newFixedThreadPool(1)));
    }
}
