package com.yinfires.moonspire;

import java.util.Locale;
import java.util.function.Supplier;

public final class MoonSpirePerfDiagnostics {
    public static final String PREFIX = "[MoonSpirePerf]";
    public static final long SEGMENT_THRESHOLD_NANOS = 8_000_000L;
    public static final long OPERATION_THRESHOLD_NANOS = 16_000_000L;
    private static final boolean ENABLED = Boolean.getBoolean("moonspire.perfDiag");

    private MoonSpirePerfDiagnostics() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static long now() {
        return System.nanoTime();
    }

    public static void time(String label, Runnable runnable) {
        if (!ENABLED) {
            runnable.run();
            return;
        }
        long start = now();
        try {
            runnable.run();
        } finally {
            mark(label, now() - start);
        }
    }

    public static <T> T time(String label, Supplier<T> supplier) {
        if (!ENABLED) {
            return supplier.get();
        }
        long start = now();
        try {
            return supplier.get();
        } finally {
            mark(label, now() - start);
        }
    }

    public static void mark(String label, long nanos) {
        mark(label, nanos, SEGMENT_THRESHOLD_NANOS, "");
    }

    public static void markOperation(String label, long nanos, String details) {
        mark(label, nanos, OPERATION_THRESHOLD_NANOS, details);
    }

    public static void mark(String label, long nanos, long thresholdNanos, String details) {
        if (!ENABLED || nanos < thresholdNanos) {
            return;
        }
        log(label, "durationMs=" + millis(nanos) + suffix(details));
    }

    public static void log(String label, String details) {
        if (!ENABLED) {
            return;
        }
        MoonSpire.LOGGER.info("{} {} {}", PREFIX, label, details == null ? "" : details);
    }

    public static String millis(long nanos) {
        return String.format(Locale.ROOT, "%.2f", nanos / 1_000_000.0D);
    }

    private static String suffix(String details) {
        return details == null || details.isBlank() ? "" : " " + details;
    }
}
