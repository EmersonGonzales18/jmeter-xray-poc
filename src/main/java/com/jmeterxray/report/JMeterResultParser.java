package com.jmeterxray.report;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JMeterResultParser {

    public JMeterMetrics parse(
            Path jtlFile,
            String threadGroup,
            String testKey)
            throws IOException {

        if (!Files.isRegularFile(jtlFile)) {

            throw new IOException(
                    "No existe JTL: "
                            + jtlFile.toAbsolutePath()
            );
        }

        List<String> lines =
                Files.readAllLines(jtlFile);

        if (lines.size() < 2) {

            throw new IOException(
                    "JTL sin resultados: "
                            + jtlFile
            );
        }

        String[] headers =
                lines.get(0)
                        .split(",", -1);

        int elapsedIndex =
                findColumn(headers, "elapsed");

        int successIndex =
                findColumn(headers, "success");

        int responseMessageIndex =
                findOptionalColumn(
                        headers,
                        "responseMessage"
                );

        int bytesIndex =
                findOptionalColumn(
                        headers,
                        "bytes"
                );

        int sentBytesIndex =
                findOptionalColumn(
                        headers,
                        "sentBytes"
                );

        int timestampIndex =
                findColumn(
                        headers,
                        "timeStamp"
                );

        List<Long> responseTimes =
                new ArrayList<>();

        int failed = 0;

        long receivedBytes = 0;
        long sentBytes = 0;

        long firstTimestamp =
                Long.MAX_VALUE;

        long lastTimestamp =
                Long.MIN_VALUE;

        Map<String, Integer> errors =
                new HashMap<>();

        for (int i = 1;
             i < lines.size();
             i++) {

            String line =
                    lines.get(i).trim();

            if (line.isBlank()) {
                continue;
            }

            String[] values =
                    line.split(",", -1);

            if (values.length <= successIndex
                    || values.length <= elapsedIndex) {
                continue;
            }

            long elapsed =
                    Long.parseLong(
                            values[elapsedIndex]
                                    .trim()
                    );

            responseTimes.add(elapsed);

            boolean success =
                    Boolean.parseBoolean(
                            values[successIndex]
                                    .trim()
                    );

            if (!success) {

                failed++;

                String message =
                        responseMessageIndex >= 0
                                && values.length
                                > responseMessageIndex
                                ? values[
                                    responseMessageIndex
                                ].trim()
                                : "Unknown error";

                errors.merge(
                        message,
                        1,
                        Integer::sum
                );
            }

            if (bytesIndex >= 0
                    && values.length > bytesIndex) {

                receivedBytes +=
                        parseLongSafe(
                                values[bytesIndex]
                        );
            }

            if (sentBytesIndex >= 0
                    && values.length > sentBytesIndex) {

                sentBytes +=
                        parseLongSafe(
                                values[sentBytesIndex]
                        );
            }

            if (values.length > timestampIndex) {

                long timestamp =
                        parseLongSafe(
                                values[timestampIndex]
                        );

                firstTimestamp =
                        Math.min(
                                firstTimestamp,
                                timestamp
                        );

                lastTimestamp =
                        Math.max(
                                lastTimestamp,
                                timestamp + elapsed
                        );
            }
        }

        if (responseTimes.isEmpty()) {

            throw new IOException(
                    "No se encontraron samples en "
                            + jtlFile
            );
        }

        Collections.sort(
                responseTimes
        );

        int samples =
                responseTimes.size();

        double average =
                responseTimes.stream()
                        .mapToLong(Long::longValue)
                        .average()
                        .orElse(0);

        long min =
                responseTimes.get(0);

        long max =
                responseTimes.get(
                        responseTimes.size() - 1
                );

        double median =
                percentile(
                        responseTimes,
                        50
                );

        double p90 =
                percentile(
                        responseTimes,
                        90
                );

        double p95 =
                percentile(
                        responseTimes,
                        95
                );

        double p99 =
                percentile(
                        responseTimes,
                        99
                );

        double errorPercentage =
                samples == 0
                        ? 0
                        : (failed * 100.0)
                        / samples;

        double durationSeconds =
                firstTimestamp == Long.MAX_VALUE
                        || lastTimestamp
                        == Long.MIN_VALUE
                        ? 0
                        : (lastTimestamp
                        - firstTimestamp)
                        / 1000.0;

        double throughput =
                durationSeconds <= 0
                        ? 0
                        : samples
                        / durationSeconds;

        double receivedKbSec =
                durationSeconds <= 0
                        ? 0
                        : (receivedBytes / 1024.0)
                        / durationSeconds;

        double sentKbSec =
                durationSeconds <= 0
                        ? 0
                        : (sentBytes / 1024.0)
                        / durationSeconds;

        // Primera versión.
        // Después podemos igualar exactamente
        // el cálculo APDEX de JMeter.
        double apdex =
                calculateApdex(
                        responseTimes,
                        500
                );

        List<JMeterMetrics.ErrorMetric>
                errorMetrics =
                errors.entrySet()
                        .stream()
                        .sorted(
                                (a, b) ->
                                        Integer.compare(
                                                b.getValue(),
                                                a.getValue()
                                        )
                        )
                        .limit(5)
                        .map(
                                entry ->
                                        new JMeterMetrics.ErrorMetric(
                                                entry.getKey(),
                                                entry.getValue()
                                        )
                        )
                        .toList();

        return new JMeterMetrics(
                threadGroup,
                testKey,

                samples,
                failed,

                errorPercentage,

                average,
                min,
                max,
                median,

                p90,
                p95,
                p99,

                throughput,

                receivedKbSec,
                sentKbSec,

                apdex,

                errorMetrics
        );
    }

    private int findColumn(
            String[] headers,
            String name)
            throws IOException {

        int index =
                findOptionalColumn(
                        headers,
                        name
                );

        if (index < 0) {

            throw new IOException(
                    "JTL no contiene columna: "
                            + name
            );
        }

        return index;
    }

    private int findOptionalColumn(
            String[] headers,
            String name) {

        for (int i = 0;
             i < headers.length;
             i++) {

            if (name.equalsIgnoreCase(
                    headers[i].trim()
            )) {

                return i;
            }
        }

        return -1;
    }

    private long parseLongSafe(
            String value) {

        try {

            return Long.parseLong(
                    value.trim()
            );

        } catch (Exception exception) {

            return 0;
        }
    }

    private double percentile(
            List<Long> values,
            double percentile) {

        if (values.isEmpty()) {
            return 0;
        }

        double position =
                percentile
                        / 100.0
                        * (values.size() - 1);

        int lower =
                (int) Math.floor(position);

        int upper =
                (int) Math.ceil(position);

        if (lower == upper) {
            return values.get(lower);
        }

        double weight =
                position - lower;

        return values.get(lower)
                * (1 - weight)
                + values.get(upper)
                * weight;
    }

    private double calculateApdex(
            List<Long> values,
            long threshold) {

        long satisfied = 0;
        long tolerating = 0;

        long frustrationThreshold =
                threshold * 3;

        for (long elapsed : values) {

            if (elapsed <= threshold) {

                satisfied++;

            } else if (
                    elapsed
                            <= frustrationThreshold) {

                tolerating++;
            }
        }

        return (
                satisfied
                        + tolerating / 2.0
        ) / values.size();
    }
}