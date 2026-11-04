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

    private static final String TEST_SET_NAME =
            "JMeter Performance Tests";

    public static void main(String[] args) {

        try {

            new JMeterXrayOrchestrator().run();

        } catch (Exception exception) {

            System.err.println();
            System.err.println("========================================");
            System.err.println(" ERROR EN INTEGRACION JMETER -> XRAY");
            System.err.println("========================================");

            exception.printStackTrace();

            System.exit(1);
        }
    }

    public void run() throws Exception {

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

        if (testPlanKey.isBlank()) {

            throw new IOException(
                    "Xray no devolvio key para el Test Plan."
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
}