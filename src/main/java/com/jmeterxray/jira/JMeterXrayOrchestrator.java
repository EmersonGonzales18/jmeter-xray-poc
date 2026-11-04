package com.jmeterxray;

import com.fasterxml.jackson.databind.JsonNode;
import com.jmeterxray.config.JiraConfig;
import com.jmeterxray.jira.JiraApiClient;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class JMeterXrayOrchestrator {


    private static final Path CONTEXT_PATH =
        Path.of(
                "target",
                "jmeter-xray-context.properties"
        );

    private static final String TEST_SET_NAME =
            "JMeter Performance Tests";

    public static void main(String[] args) {

        try {

            String phase =
                    args.length == 0
                            ? "prepare"
                            : args[0];

            JMeterXrayOrchestrator orchestrator =
                    new JMeterXrayOrchestrator();

            switch (phase) {

                case "prepare" ->
                        orchestrator.prepare();

                case "publish-results" ->
                        orchestrator.publishResults();

                default ->
                        throw new IllegalArgumentException(
                                "Fase desconocida: "
                                        + phase
                                        + ". Usa prepare o publish-results."
                        );
            }

        } catch (Exception exception) {

            System.err.println();
            System.err.println("========================================");
            System.err.println(" ERROR EN INTEGRACION JMETER -> XRAY");
            System.err.println("========================================");

            exception.printStackTrace();

            System.exit(1);
        }
    }

    public void prepare() throws Exception {

        System.out.println();
        System.out.println("========================================");
        System.out.println(" JMETER -> JIRA / XRAY");
        System.out.println("========================================");

        // --------------------------------------------------------
        // 1. Cargar configuracion
        // --------------------------------------------------------

        JiraConfig config = JiraConfig.fromFile();

        System.out.println();
        System.out.println("Configuracion cargada:");
        System.out.println("Project: " + config.getProjectKey());
        System.out.println("Historia: " + config.getJiraUserStoryKey());
        System.out.println("JMX: " + config.getJmxPath());

        // --------------------------------------------------------
        // 2. Leer Thread Groups del JMX
        // --------------------------------------------------------

        List<String> threadGroups =
                readThreadGroups(config.getJmxPath());

        if (threadGroups.isEmpty()) {

            throw new IllegalStateException(
                    "No se encontraron Thread Groups en "
                            + config.getJmxPath()
            );
        }

        System.out.println();
        System.out.println(
                "Thread Groups encontrados: "
                        + threadGroups.size()
        );

        for (int i = 0; i < threadGroups.size(); i++) {

            System.out.println(
                    "  [" + (i + 1) + "] "
                            + threadGroups.get(i)
            );
        }

        // --------------------------------------------------------
        // 3. Autenticar contra Xray
        // --------------------------------------------------------

        JiraApiClient apiClient =
                new JiraApiClient(config);

        System.out.println();
        System.out.println("Autenticando contra Xray...");

        String token =
                apiClient.authenticate();

        if (token == null || token.isBlank()) {

            throw new IOException(
                    "Xray no devolvio un token valido."
            );
        }

        System.out.println("Autenticacion Xray: OK");

        // --------------------------------------------------------
        // 4. Crear o reutilizar Test por Thread Group
        // --------------------------------------------------------

        System.out.println();
        System.out.println("Sincronizando Tests...");

        List<String> testIssueIds =
                new ArrayList<>();

        List<String> testIssueKeys =
        new ArrayList<>();

        for (String threadGroup : threadGroups) {

            JsonNode test =
                    apiClient.upsertTest(
                            token,
                            threadGroup
                    );

            String issueId =
                    test.path("issueId").asText();

            String issueKey =
                    test.path("jira")
                            .path("key")
                            .asText();

            if (issueId.isBlank()) {

                throw new IOException(
                        "Xray no devolvio issueId para Test: "
                                + threadGroup
                );
            }

            testIssueIds.add(issueId);
            testIssueKeys.add(issueKey);

            System.out.println(
                    "  OK -> "
                            + issueKey
                            + " | "
                            + threadGroup
            );
        }

        // --------------------------------------------------------
        // 5. Crear o actualizar Test Set
        // --------------------------------------------------------

        System.out.println();
        System.out.println(
                "Sincronizando Test Set: "
                        + TEST_SET_NAME
        );

        JsonNode testSet =
                apiClient.upsertTestSet(
                        token,
                        TEST_SET_NAME,
                        testIssueIds
                );

        String testSetKey =
                testSet.path("jira")
                        .path("key")
                        .asText();

        if (testSetKey.isBlank()) {

            throw new IOException(
                    "Xray no devolvio key para el Test Set."
            );
        }

        System.out.println(
                "Test Set: " + testSetKey
        );

        // --------------------------------------------------------
        // 6. Crear Test Plan NUEVO para esta corrida
        // --------------------------------------------------------

        String runId =
                LocalDateTime.now()
                        .format(
                                DateTimeFormatter.ofPattern(
                                        "yyyyMMdd-HHmmss"
                                )
                        );

        String testPlanSummary =
                "JMeter Performance - " + runId;

        System.out.println();
        System.out.println(
                "Creando Test Plan: "
                        + testPlanSummary
        );

        JsonNode testPlan =
                apiClient.createTestPlan(
                        token,
                        testPlanSummary
                );

        String testPlanKey =
                testPlan.path("jira")
                        .path("key")
                        .asText();

        String testPlanIssueId =
        testPlan.path("issueId")
                .asText();

        if (testPlanKey.isBlank()
                || testPlanIssueId.isBlank()) {

            throw new IOException(
                    "Xray no devolvio key/issueId para el Test Plan."
            );
        }

        // --------------------------------------------------------
        // 7. Vincular Test Set -> Test Plan
        // --------------------------------------------------------

        System.out.println();
        System.out.println(
                "Vinculando Test Set con Test Plan..."
        );

        apiClient.linkTestSetToTestPlan(
                testSetKey,
                testPlanKey
        );

        // --------------------------------------------------------
        // 8. Vincular Test Plan -> Historia
        // --------------------------------------------------------

        System.out.println(
                "Vinculando Test Plan con Historia..."
        );

        apiClient.linkTestPlanToUserStory(
                testPlanKey
        );

        saveContext(
        testPlanKey,
        testPlanIssueId,
        testSetKey,
        threadGroups,
        testIssueIds,
        testIssueKeys
        );

        // --------------------------------------------------------
        // Resultado
        // --------------------------------------------------------

        System.out.println();
        System.out.println("========================================");
        System.out.println(" INTEGRACION COMPLETADA");
        System.out.println("========================================");

        System.out.println(
                "Historia:  "
                        + config.getJiraUserStoryKey()
        );

        System.out.println(
                "Test Plan: "
                        + testPlanKey
        );

        System.out.println(
                "Test Set:  "
                        + testSetKey
        );

        System.out.println(
                "Tests:     "
                        + testIssueIds.size()
        );

        System.out.println("========================================");
    }

    // ============================================================
    // LEER THREAD GROUPS DEL JMX
    // ============================================================

    private List<String> readThreadGroups(
            Path jmxPath) throws Exception {

        if (!Files.isRegularFile(jmxPath)) {

            throw new IOException(
                    "No existe el JMX: "
                            + jmxPath.toAbsolutePath()
            );
        }

        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();

        DocumentBuilder builder =
                factory.newDocumentBuilder();

        Document document =
                builder.parse(
                        jmxPath.toFile()
                );

        NodeList threadGroupNodes =
                document.getElementsByTagName(
                        "ThreadGroup"
                );

        /*
         * LinkedHashSet:
         *
         * - conserva el orden del JMX
         * - evita nombres duplicados
         */
        Set<String> names =
                new LinkedHashSet<>();

        for (int i = 0;
             i < threadGroupNodes.getLength();
             i++) {

            Element threadGroup =
                    (Element) threadGroupNodes.item(i);

            /*
             * Ignoramos TG deshabilitados explícitamente.
             */
            String enabled =
                    threadGroup.getAttribute(
                            "enabled"
                    );

            if ("false".equalsIgnoreCase(enabled)) {
                continue;
            }

            String name =
                    threadGroup.getAttribute(
                            "testname"
                    );

            if (name != null
                    && !name.isBlank()) {

                names.add(
                        name.trim()
                );
            }
        }

        return new ArrayList<>(names);
    }

    private void saveContext(
        String testPlanKey,
        String testPlanIssueId,
        String testSetKey,
        List<String> threadGroups,
        List<String> testIssueIds,
        List<String> testIssueKeys)
        throws IOException {

        java.util.Properties properties =
                new java.util.Properties();

        // ---------------------------------------------
        // Test Plan
        // ---------------------------------------------

        properties.setProperty(
                "testPlan.key",
                testPlanKey
        );

        properties.setProperty(
                "testPlan.issueId",
                testPlanIssueId
        );

        // ---------------------------------------------
        // Test Set
        // ---------------------------------------------

        properties.setProperty(
                "testSet.key",
                testSetKey
        );

        // ---------------------------------------------
        // Thread Groups
        // ---------------------------------------------

        properties.setProperty(
                "threadGroup.count",
                String.valueOf(threadGroups.size())
        );

        for (int i = 0;
            i < threadGroups.size();
            i++) {

            properties.setProperty(
                    "threadGroup."
                            + i
                            + ".name",
                    threadGroups.get(i)
            );

            properties.setProperty(
                    "threadGroup."
                            + i
                            + ".testIssueId",
                    testIssueIds.get(i)
            );

            properties.setProperty(
                    "threadGroup."
                            + i
                            + ".testKey",
                    testIssueKeys.get(i)
            );
        }

        // ---------------------------------------------
        // Crear target si no existe
        // ---------------------------------------------

        Files.createDirectories(
                CONTEXT_PATH.getParent()
        );

        // ---------------------------------------------
        // Guardar archivo
        // ---------------------------------------------

        try (var output =
                    Files.newOutputStream(
                            CONTEXT_PATH
                    )) {

            properties.store(
                    output,
                    "JMeter Xray execution context"
            );
        }

        System.out.println();
        System.out.println(
                "Contexto guardado:"
        );

        System.out.println(
                CONTEXT_PATH.toAbsolutePath()
        );
    }

    private boolean jmeterExecutionPassed(
        Path jtlFile)
        throws Exception {

    if (!Files.isRegularFile(jtlFile)) {

        throw new IOException(
                "No existe el resultado JMeter: "
                        + jtlFile.toAbsolutePath()
        );
    }

        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();

        DocumentBuilder builder =
                factory.newDocumentBuilder();

        Document document =
                builder.parse(
                        jtlFile.toFile()
                );

        NodeList httpSamples =
                document.getElementsByTagName(
                        "httpSample"
                );

        NodeList samples =
                document.getElementsByTagName(
                        "sample"
                );

        int totalSamples =
                httpSamples.getLength()
                        + samples.getLength();

        if (totalSamples == 0) {

            throw new IOException(
                    "El JTL no contiene samples: "
                            + jtlFile
            );
        }

        // HTTP Samples
        for (int i = 0;
            i < httpSamples.getLength();
            i++) {

            Element sample =
                    (Element) httpSamples.item(i);

            String success =
                    sample.getAttribute("s");

            if (!Boolean.parseBoolean(success)) {
                return false;
            }
        }

        // Samples genericos
        for (int i = 0;
            i < samples.getLength();
            i++) {

            Element sample =
                    (Element) samples.item(i);

            String success =
                    sample.getAttribute("s");

            if (!Boolean.parseBoolean(success)) {
                return false;
            }
        }

        return true;
    }



    public void publishResults()
        throws Exception {

    System.out.println();
    System.out.println(
            "========================================"
    );

    System.out.println(
            " PUBLICANDO RESULTADOS JMETER -> XRAY"
    );

    System.out.println(
            "========================================"
    );

    // --------------------------------------------------------
    // 1. Comprobar contexto
    // --------------------------------------------------------

        if (!Files.isRegularFile(CONTEXT_PATH)) {

            throw new IOException(
                    "No existe el contexto: "
                            + CONTEXT_PATH.toAbsolutePath()
            );
        }

        // --------------------------------------------------------
        // 2. Cargar configuracion
        // --------------------------------------------------------

        JiraConfig config =
                JiraConfig.fromFile();

        JiraApiClient apiClient =
                new JiraApiClient(config);

        // --------------------------------------------------------
        // 3. Autenticar Xray
        // --------------------------------------------------------

        System.out.println();
        System.out.println(
                "Autenticando contra Xray..."
        );

        String token =
                apiClient.authenticate();

        System.out.println(
                "Autenticacion Xray: OK"
        );

        // --------------------------------------------------------
        // 4. Leer contexto generado por prepare
        // --------------------------------------------------------

        java.util.Properties context =
                new java.util.Properties();

        try (var input =
                    Files.newInputStream(
                            CONTEXT_PATH
                    )) {

            context.load(input);
        }

        String testPlanKey =
                context.getProperty(
                        "testPlan.key"
                );

        String testPlanIssueId =
                context.getProperty(
                        "testPlan.issueId"
                );

        String testSetKey =
                context.getProperty(
                        "testSet.key"
                );

        int threadGroupCount =
                Integer.parseInt(
                        context.getProperty(
                                "threadGroup.count"
                        )
                );

        System.out.println();
        System.out.println(
                "Test Plan: "
                        + testPlanKey
        );

        System.out.println(
                "Test Set: "
                        + testSetKey
        );

        System.out.println(
                "Thread Groups: "
                        + threadGroupCount
        );

        // --------------------------------------------------------
        // 5. Procesar cada Thread Group
        // --------------------------------------------------------

        for (int i = 0;
            i < threadGroupCount;
            i++) {

            String threadGroupName =
                    context.getProperty(
                            "threadGroup."
                                    + i
                                    + ".name"
                    );

            String testIssueId =
                    context.getProperty(
                            "threadGroup."
                                    + i
                                    + ".testIssueId"
                    );

            String testKey =
                    context.getProperty(
                            "threadGroup."
                                    + i
                                    + ".testKey"
                    );

            // ----------------------------------------------------
            // Construir el mismo nombre usado por el splitter
            // ----------------------------------------------------

            String safeName =
                    threadGroupName
                            .replaceAll(
                                    "[^a-zA-Z0-9._-]+",
                                    "-"
                            )
                            .replaceAll(
                                    "^-+|-+$",
                                    ""
                            );

            String executionName =
                    String.format(
                            "%02d-%s",
                            i + 1,
                            safeName
                    );

            Path jtlFile =
                    Path.of(
                            "results",
                            executionName + ".jtl"
                    );

            System.out.println();
            System.out.println(
                    "----------------------------------------"
            );

            System.out.println(
                    "Thread Group: "
                            + threadGroupName
            );

            System.out.println(
                    "Test: "
                            + testKey
            );

            System.out.println(
                    "JTL: "
                            + jtlFile
            );

            // ----------------------------------------------------
            // 6. Determinar PASSED / FAILED
            // ----------------------------------------------------

            boolean passed =
                    jmeterExecutionPassed(
                            jtlFile
                    );

            String status =
                    passed
                            ? "PASSED"
                            : "FAILED";

            System.out.println(
                    "Resultado JMeter: "
                            + status
            );

            // ----------------------------------------------------
            // 7. Crear Test Execution
            // ----------------------------------------------------

            String summary =
                    "JMeter Execution - "
                            + threadGroupName
                            + " - "
                            + testPlanKey;

            JsonNode execution =
                    apiClient.createTestExecution(
                            token,
                            summary,
                            testIssueId
                    );

            String executionKey =
                    execution.path("jira")
                            .path("key")
                            .asText();

            String executionIssueId =
                    execution.path("issueId")
                            .asText();

            if (executionKey.isBlank()
                    || executionIssueId.isBlank()) {

                throw new IOException(
                        "Xray no devolvio key/issueId "
                                + "del Test Execution."
                );
            }

            System.out.println(
                    "Test Execution creado: "
                            + executionKey
            );

            // ----------------------------------------------------
            // 8. Obtener Test Run creado automáticamente
            // ----------------------------------------------------

            JsonNode testRuns =
                    execution.path("testRuns")
                            .path("results");

            if (!testRuns.isArray()
                    || testRuns.isEmpty()) {

                throw new IOException(
                        "Xray no devolvio Test Run para "
                                + executionKey
                );
            }

            String testRunId =
                    testRuns.get(0)
                            .path("id")
                            .asText();

            if (testRunId.isBlank()) {

                throw new IOException(
                        "Xray no devolvio Test Run ID para "
                                + executionKey
                );
            }

            // ----------------------------------------------------
            // 9. Actualizar estado
            // ----------------------------------------------------

            apiClient.updateTestRunStatus(
                    token,
                    testRunId,
                    status
            );

            // ----------------------------------------------------
            // 10. Asociar Test Execution al Test Plan
            // ----------------------------------------------------

            apiClient.addTestExecutionToTestPlan(
                    token,
                    testPlanIssueId,
                    executionIssueId
            );

            System.out.println(
                    "OK -> "
                            + executionKey
                            + " | "
                            + testKey
                            + " | "
                            + status
            );
        }

        // --------------------------------------------------------
        // FINAL
        // --------------------------------------------------------

        System.out.println();
        System.out.println(
                "========================================"
        );

        System.out.println(
                " RESULTADOS PUBLICADOS EN XRAY"
        );

        System.out.println(
                "========================================"
        );
    }
}