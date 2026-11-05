package com.jmeterxray.report;

import java.util.List;

public record JMeterMetrics(

        String threadGroup,
        String testKey,

        int samples,
        int failed,

        double errorPercentage,

        double average,
        long min,
        long max,
        double median,

        double p90,
        double p95,
        double p99,

        double throughput,

        double receivedKbSec,
        double sentKbSec,

        double apdex,

        List<ErrorMetric> errors

) {

    public boolean passed() {
        return failed == 0;
    }

    public record ErrorMetric(
            String message,
            int count
    ) {
    }
}