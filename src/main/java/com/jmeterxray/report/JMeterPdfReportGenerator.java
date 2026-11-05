package com.jmeterxray.report;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;

import java.awt.Color;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class JMeterPdfReportGenerator {

    private static final Color BLUE =
            new Color(148, 188, 225);

    private static final Color LIGHT_BLUE =
            new Color(237, 243, 248);

    private static final Color TITLE =
            new Color(36, 75, 90);

    private static final Color PASS_GREEN =
            new Color(154, 205, 50);

    private static final Color FAIL_RED =
            new Color(220, 70, 70);

    public Path generate(
            Path output,
            String testPlanKey,
            String testExecutionKey,
            String testSetKey,
            List<JMeterMetrics> metrics)
            throws Exception {

        Files.createDirectories(
                output.getParent()
        );

        Document document =
                new Document(
                        PageSize.A4.rotate(),
                        28,
                        28,
                        30,
                        30
                );

        PdfWriter.getInstance(
                document,
                new FileOutputStream(
                        output.toFile()
                )
        );

        document.open();

        addSummaryPage(
                document,
                testPlanKey,
                testExecutionKey,
                testSetKey,
                metrics
        );

        for (JMeterMetrics metric : metrics) {

            document.newPage();

            addThreadGroupPage(
                    document,
                    metric
            );
        }

        document.close();

        return output;
    }

    // ============================================================
    // RESUMEN
    // ============================================================

    private void addSummaryPage(
            Document document,
            String testPlanKey,
            String testExecutionKey,
            String testSetKey,
            List<JMeterMetrics> metrics)
            throws Exception {

        Paragraph title =
                new Paragraph(
                        "JMeter Performance Test Report",
                        font(22, Font.BOLD, TITLE)
                );

        title.setAlignment(
                Element.ALIGN_CENTER
        );

        document.add(title);

        document.add(
                new Paragraph(" ")
        );

        PdfPTable metadata =
                new PdfPTable(6);

        metadata.setWidthPercentage(90);

        metadata.setWidths(
                new float[]{
                        1.2f, 1.5f,
                        1.5f, 1.5f,
                        1.2f, 1.5f
                }
        );

        addHeaderCell(metadata, "Test Plan");
        addValueCell(metadata, testPlanKey);

        addHeaderCell(
                metadata,
                "Test Execution"
        );

        addValueCell(
                metadata,
                testExecutionKey
        );

        addHeaderCell(metadata, "Test Set");
        addValueCell(metadata, testSetKey);

        document.add(metadata);

        document.add(
                new Paragraph(" ")
        );

        Paragraph section =
                new Paragraph(
                        "Execution Summary",
                        font(15, Font.BOLD, TITLE)
                );

        document.add(section);

        document.add(
                new Paragraph(" ")
        );

        PdfPTable table =
                new PdfPTable(6);

        table.setWidthPercentage(90);

        table.setWidths(
                new float[]{
                        1.4f,
                        1.4f,
                        1.2f,
                        1.0f,
                        1.2f,
                        1.4f
                }
        );

        addHeaderCell(
                table,
                "Thread Group"
        );

        addHeaderCell(
                table,
                "Test Xray"
        );

        addHeaderCell(
                table,
                "Samples"
        );

        addHeaderCell(
                table,
                "FAIL"
        );

        addHeaderCell(
                table,
                "Error %"
        );

        addHeaderCell(
                table,
                "Status"
        );

        for (JMeterMetrics metric : metrics) {

            addValueCell(
                    table,
                    metric.threadGroup()
            );

            addValueCell(
                    table,
                    metric.testKey()
            );

            addValueCell(
                    table,
                    String.valueOf(
                            metric.samples()
                    )
            );

            addValueCell(
                    table,
                    String.valueOf(
                            metric.failed()
                    )
            );

            addValueCell(
                    table,
                    format(
                            metric.errorPercentage()
                    ) + "%"
            );

            PdfPCell status =
                    new PdfPCell(
                            new Phrase(
                                    metric.passed()
                                            ? "PASSED"
                                            : "FAILED",
                                    font(
                                            9,
                                            Font.BOLD,
                                            Color.WHITE
                                    )
                            )
                    );

            status.setHorizontalAlignment(
                    Element.ALIGN_CENTER
            );

            status.setVerticalAlignment(
                    Element.ALIGN_MIDDLE
            );

            status.setPadding(7);

            status.setBackgroundColor(
                    metric.passed()
                            ? PASS_GREEN
                            : FAIL_RED
            );

            table.addCell(status);
        }

        document.add(table);
    }

    // ============================================================
    // THREAD GROUP
    // ============================================================

    private void addThreadGroupPage(
            Document document,
            JMeterMetrics metric)
            throws Exception {

        Paragraph title =
                new Paragraph(
                        "Thread Group "
                                + metric.threadGroup()
                                + " - "
                                + metric.testKey(),
                        font(
                                20,
                                Font.BOLD,
                                TITLE
                        )
                );

        title.setAlignment(
                Element.ALIGN_CENTER
        );

        document.add(title);

        Paragraph result =
                new Paragraph(
                        "Result: "
                                + (
                                metric.passed()
                                        ? "PASSED"
                                        : "FAILED"
                        )
                                + "   |   Samples: "
                                + metric.samples()
                                + "   |   Failed: "
                                + metric.failed(),
                        font(
                                9,
                                Font.BOLD,
                                Color.DARK_GRAY
                        )
                );

        document.add(result);

        document.add(
                new Paragraph(" ")
        );

        // ========================================================
        // APDEX + REQUEST SUMMARY
        // ========================================================

        PdfPTable top =
                new PdfPTable(2);

        top.setWidthPercentage(100);

        top.setWidths(
                new float[]{
                        2.1f,
                        1.0f
                }
        );

        top.addCell(
                createApdexPanel(metric)
        );

        top.addCell(
                createRequestSummary(metric)
        );

        document.add(top);

        document.add(
                new Paragraph(" ")
        );

        // ========================================================
        // STATISTICS
        // ========================================================

        document.add(
                sectionTitle("Statistics")
        );

        document.add(
                createStatisticsTable(
                        metric
                )
        );

        document.add(
                new Paragraph(" ")
        );

        // ========================================================
        // BOTTOM
        // ========================================================

        PdfPTable bottom =
                new PdfPTable(2);

        bottom.setWidthPercentage(100);

        bottom.setWidths(
                new float[]{
                        1.9f,
                        1.0f
                }
        );

        PdfPCell errorsCell =
                new PdfPCell();

        errorsCell.setBorder(
                Rectangle.NO_BORDER
        );

        errorsCell.addElement(
                sectionTitle("Errors")
        );

        errorsCell.addElement(
                createErrorsTable(
                        metric
                )
        );

        errorsCell.addElement(
                sectionTitle(
                        "Top 5 Errors by sampler"
                )
        );

        errorsCell.addElement(
                createTopErrorsTable(
                        metric
                )
        );

        bottom.addCell(errorsCell);

        bottom.addCell(
                createResponseTimesPanel(
                        metric
                )
        );

        document.add(bottom);
    }

    // ============================================================
    // APDEX
    // ============================================================

    private PdfPCell createApdexPanel(
            JMeterMetrics metric)
            throws Exception {

        PdfPCell container =
                new PdfPCell();

        container.setBorder(
                Rectangle.NO_BORDER
        );

        container.addElement(
                sectionTitle(
                        "APDEX (Application Performance Index)"
                )
        );

        PdfPTable table =
                new PdfPTable(4);

        table.setWidthPercentage(100);

        addHeaderCell(table, "Apdex");
        addHeaderCell(
                table,
                "T (Toleration threshold)"
        );

        addHeaderCell(
                table,
                "F (Frustration threshold)"
        );

        addHeaderCell(table, "Label");

        addValueCell(
                table,
                format(metric.apdex())
        );

        addValueCell(
                table,
                "500 ms"
        );

        addValueCell(
                table,
                "1 sec 500 ms"
        );

        addValueCell(
                table,
                "Total"
        );

        container.addElement(table);

        return container;
    }

    // ============================================================
    // REQUEST SUMMARY
    // ============================================================

    private PdfPCell createRequestSummary(
            JMeterMetrics metric) {

        PdfPCell container =
                new PdfPCell();

        container.setBorder(
                Rectangle.NO_BORDER
        );

        Paragraph title =
                sectionTitle(
                        "Requests Summary"
                );

        title.setAlignment(
                Element.ALIGN_CENTER
        );

        container.addElement(title);

        int passed =
                metric.samples()
                        - metric.failed();

        double passPercentage =
                metric.samples() == 0
                        ? 0
                        : passed
                        * 100.0
                        / metric.samples();

        double failPercentage =
                metric.samples() == 0
                        ? 0
                        : metric.failed()
                        * 100.0
                        / metric.samples();

        PdfPTable summary =
                new PdfPTable(1);

        summary.setWidthPercentage(65);

        PdfPCell passCell =
                new PdfPCell(
                        new Phrase(
                                "PASS "
                                        + format(
                                        passPercentage
                                )
                                        + "%",
                                font(
                                        13,
                                        Font.BOLD,
                                        Color.WHITE
                                )
                        )
                );

        passCell.setBackgroundColor(
                PASS_GREEN
        );

        passCell.setHorizontalAlignment(
                Element.ALIGN_CENTER
        );

        passCell.setPadding(14);

        summary.addCell(passCell);

        if (metric.failed() > 0) {

            PdfPCell failCell =
                    new PdfPCell(
                            new Phrase(
                                    "FAIL "
                                            + format(
                                            failPercentage
                                    )
                                            + "%",
                                    font(
                                            11,
                                            Font.BOLD,
                                            Color.WHITE
                                    )
                            )
                    );

            failCell.setBackgroundColor(
                    FAIL_RED
            );

            failCell.setHorizontalAlignment(
                    Element.ALIGN_CENTER
            );

            failCell.setPadding(8);

            summary.addCell(failCell);
        }

        container.addElement(summary);

        return container;
    }

    // ============================================================
    // STATISTICS
    // ============================================================

    private PdfPTable createStatisticsTable(
            JMeterMetrics metric)
            throws Exception {

        String[] headers = {
                "Label",
                "#Samples",
                "FAIL",
                "Error %",
                "Average",
                "Min",
                "Max",
                "Median",
                "90th pct",
                "95th pct",
                "99th pct",
                "Trans./s",
                "Recv KB/s",
                "Sent KB/s"
        };

        PdfPTable table =
                new PdfPTable(
                        headers.length
                );

        table.setWidthPercentage(100);

        for (String header : headers) {
            addHeaderCell(
                    table,
                    header
            );
        }

        String[] values = {
                "Total",
                String.valueOf(
                        metric.samples()
                ),
                String.valueOf(
                        metric.failed()
                ),
                format(
                        metric.errorPercentage()
                ) + "%",
                format(metric.average()),
                String.valueOf(metric.min()),
                String.valueOf(metric.max()),
                format(metric.median()),
                format(metric.p90()),
                format(metric.p95()),
                format(metric.p99()),
                format(metric.throughput()),
                format(
                        metric.receivedKbSec()
                ),
                format(
                        metric.sentKbSec()
                )
        };

        for (String value : values) {
            addValueCell(
                    table,
                    value
            );
        }

        return table;
    }

    // ============================================================
    // ERRORS
    // ============================================================

    private PdfPTable createErrorsTable(
            JMeterMetrics metric) {

        PdfPTable table =
                new PdfPTable(4);

        table.setWidthPercentage(100);

        addHeaderCell(
                table,
                "Type of error"
        );

        addHeaderCell(
                table,
                "Number of errors"
        );

        addHeaderCell(
                table,
                "% in errors"
        );

        addHeaderCell(
                table,
                "% in all samples"
        );

        if (metric.errors().isEmpty()) {

            addValueCell(table, "-");
            addValueCell(table, "0");
            addValueCell(table, "0.00%");
            addValueCell(table, "0.00%");

            return table;
        }

        int totalErrors =
                metric.failed();

        for (
                JMeterMetrics.ErrorMetric error
                : metric.errors()) {

            addValueCell(
                    table,
                    error.message()
            );

            addValueCell(
                    table,
                    String.valueOf(
                            error.count()
                    )
            );

            addValueCell(
                    table,
                    format(
                            error.count()
                                    * 100.0
                                    / totalErrors
                    ) + "%"
            );

            addValueCell(
                    table,
                    format(
                            error.count()
                                    * 100.0
                                    / metric.samples()
                    ) + "%"
            );
        }

        return table;
    }

    private PdfPTable createTopErrorsTable(
            JMeterMetrics metric) {

        PdfPTable table =
                new PdfPTable(4);

        table.setWidthPercentage(100);

        addHeaderCell(table, "Sample");
        addHeaderCell(table, "#Samples");
        addHeaderCell(table, "#Errors");
        addHeaderCell(table, "Error");

        if (metric.errors().isEmpty()) {

            addValueCell(table, "Total");

            addValueCell(
                    table,
                    String.valueOf(
                            metric.samples()
                    )
            );

            addValueCell(table, "0");
            addValueCell(table, "-");

            return table;
        }

        for (
                JMeterMetrics.ErrorMetric error
                : metric.errors()) {

            addValueCell(
                    table,
                    "HTTP Request"
            );

            addValueCell(
                    table,
                    String.valueOf(
                            metric.samples()
                    )
            );

            addValueCell(
                    table,
                    String.valueOf(
                            error.count()
                    )
            );

            addValueCell(
                    table,
                    error.message()
            );
        }

        return table;
    }

    // ============================================================
    // RESPONSE TIMES
    // ============================================================

    private PdfPCell createResponseTimesPanel(
            JMeterMetrics metric) {

        PdfPCell container =
                new PdfPCell();

        container.setBorder(
                Rectangle.NO_BORDER
        );

        Paragraph title =
                sectionTitle(
                        "Response Times (ms)"
                );

        title.setAlignment(
                Element.ALIGN_CENTER
        );

        container.addElement(title);

        PdfPTable table =
                new PdfPTable(2);

        table.setWidthPercentage(85);

        addHeaderCell(table, "Metric");
        addHeaderCell(table, "Value");

        addMetric(
                table,
                "Average",
                metric.average()
        );

        addMetric(
                table,
                "Median",
                metric.median()
        );

        addMetric(
                table,
                "P90",
                metric.p90()
        );

        addMetric(
                table,
                "P95",
                metric.p95()
        );

        addMetric(
                table,
                "P99",
                metric.p99()
        );

        addMetric(
                table,
                "Max",
                metric.max()
        );

        container.addElement(table);

        return container;
    }

    private void addMetric(
            PdfPTable table,
            String label,
            double value) {

        addValueCell(
                table,
                label
        );

        addValueCell(
                table,
                format(value)
        );
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private Paragraph sectionTitle(
            String text) {

        return new Paragraph(
                text,
                font(
                        13,
                        Font.BOLD,
                        TITLE
                )
        );
    }

    private void addHeaderCell(
            PdfPTable table,
            String text) {

        PdfPCell cell =
                new PdfPCell(
                        new Phrase(
                                text,
                                font(
                                        7,
                                        Font.BOLD,
                                        Color.BLACK
                                )
                        )
                );

        cell.setBackgroundColor(BLUE);

        cell.setHorizontalAlignment(
                Element.ALIGN_CENTER
        );

        cell.setVerticalAlignment(
                Element.ALIGN_MIDDLE
        );

        cell.setPadding(5);

        table.addCell(cell);
    }

    private void addValueCell(
            PdfPTable table,
            String text) {

        PdfPCell cell =
                new PdfPCell(
                        new Phrase(
                                text,
                                font(
                                        7,
                                        Font.NORMAL,
                                        Color.DARK_GRAY
                                )
                        )
                );

        cell.setBackgroundColor(
                LIGHT_BLUE
        );

        cell.setVerticalAlignment(
                Element.ALIGN_MIDDLE
        );

        cell.setPadding(5);

        table.addCell(cell);
    }

    private Font font(
            float size,
            int style,
            Color color) {

        return new Font(
                Font.HELVETICA,
                size,
                style,
                color
        );
    }

    private String format(
            double value) {

        return String.format(
                java.util.Locale.US,
                "%.2f",
                value
        );
    }
}