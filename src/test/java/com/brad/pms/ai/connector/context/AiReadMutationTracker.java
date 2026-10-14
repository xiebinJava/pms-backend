package com.brad.pms.ai.connector.context;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-thread write/lock tracking for the isolated readonly proof. Captures MyBatis
 * executor calls and direct JDBC activity; a start()/stop() pair forms one window
 * against which no business write or lock may appear.
 */
public class AiReadMutationTracker {

    /** Win: classified SQL captured for the working thread between start() and stop(). */
    public record Capture(List<String> writes, List<String> locks, List<String> reads) {
        public Capture {
            writes = writes == null ? List.of() : List.copyOf(writes);
            locks = locks == null ? List.of() : List.copyOf(locks);
            reads = reads == null ? List.of() : List.copyOf(reads);
        }
    }

    private static final List<String> LOCK_MARKERS =
            List.of("for update", "lock in share mode", "for share");

    private final List<String> sessionWrites = new ArrayList<>();
    private final List<String> sessionLocks = new ArrayList<>();
    private final List<String> sessionReads = new ArrayList<>();
    private boolean windowOpen = false;
    /** Opens a clean tracking window for the current thread. */
    public void start() {
        synchronized (this) {
            sessionWrites.clear();
            sessionLocks.clear();
            sessionReads.clear();
            windowOpen = true;
        }
    }

    /** Ends the current window and clears the captured states. */
    public void stop() {
        synchronized (this) {
            sessionWrites.clear();
            sessionLocks.clear();
            sessionReads.clear();
            windowOpen = false;
        }
    }

    public void recordSql(String sql) {
        if (!windowOpen) return;
        if (sql == null || sql.isBlank()) return;
        String normalized = sql.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
        for (String marker : LOCK_MARKERS) {
            if (normalized.startsWith("select") && normalized.contains(marker)) {
                sessionLocks.add(normalized);
                return;
            }
        }
        if (normalized.startsWith("insert") || normalized.startsWith("update")
                || normalized.startsWith("delete") || normalized.startsWith("merge")
                || normalized.startsWith("replace")) {
            sessionWrites.add(normalized);
            return;
        }
        if (normalized.startsWith("select")) sessionReads.add(normalized);
    }

    /** Snapshot of the current window for the current thread. */
    public Capture capture() {
        List<String> writes = new ArrayList<>(sessionWrites);
        List<String> locks = new ArrayList<>(sessionLocks);
        List<String> reads = new ArrayList<>(sessionReads);
        return new Capture(writes, locks, reads);
    }
}
