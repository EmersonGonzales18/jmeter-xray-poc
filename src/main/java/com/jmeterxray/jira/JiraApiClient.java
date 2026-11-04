package com.jmeterxray.jira;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jmeterxray.config.JiraConfig;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class JiraApiClient {

    private final JiraConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public JiraApiClient(JiraConfig config) {
        this.config = config;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();

        this.objectMapper = new ObjectMapper();
    }

    // ============================================================
    // XRAY AUTHENTICATION
    // ============================================================

    public String authenticate()
            throws IOException, InterruptedException {

        String requestBody = objectMapper.writeValueAsString(
                Map.of(
                        "client_id", config.getClientId(),
                        "client_secret", config.getClientSecret()
                )
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        config.getXrayBaseUrl()
                                + "/api/v2/authenticate"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        ensureSuccessful(response);

        JsonNode tokenResponse =
                objectMapper.readTree(response.body());

        if (tokenResponse.isTextual()) {
            return tokenResponse.asText();
        }

        if (tokenResponse.hasNonNull("token")) {
            return tokenResponse.get("token").asText();
        }

        throw new IOException(
                "Xray no devolvio un token."
        );
    }

    // ============================================================
    // TEST
    // ============================================================

    public JsonNode upsertTest(
            String token,
            String summary)
            throws IOException, InterruptedException {

        JsonNode existing =
                findTestBySummary(token, summary);

        if (existing != null) {

            System.out.println(
                    "Test existente: "
                            + existing.path("jira")
                            .path("key").asText()
                            + " - "
                            + summary
            );

            return existing;
        }

        JsonNode created =
                createTest(token, summary);

        System.out.println(
                "Test creado: "
                        + created.path("jira")
                        .path("key").asText()
                        + " - "
                        + summary
        );

        return created;
    }

    private JsonNode createTest(
            String token,
            String summary)
            throws IOException, InterruptedException {

        String query = """
                mutation CreateTest($jira: JSON!) {
                    createTest(
                        testType: { name: "Generic" }
                        jira: $jira
                    ) {
                        test {
                            issueId
                            jira(fields: ["key", "summary"])
                        }
                        warnings
                    }
                }
                """;

        Map<String, Object> jira =
                Map.of(
                        "fields",
                        Map.of(
                                "summary", summary,
                                "project",
                                Map.of(
                                        "key",
                                        config.getProjectKey()
                                )
                        )
                );

        JsonNode data =
                executeGraphql(
                        token,
                        query,
                        Map.of("jira", jira)
                );

        JsonNode test =
                data.path("createTest")
                        .path("test");

        if (test.path("issueId").asText().isBlank()) {
            throw new IOException(
                    "Xray no devolvio issueId al crear Test: "
                            + data
            );
        }

        return test;
    }

    private JsonNode findTestBySummary(
            String token,
            String summary)
            throws IOException, InterruptedException {

        String project = config.getProjectKey();

        String phrase =
                "\\\"" + escapeJqlValue(summary) + "\\\"";

        String jql =
                "project = "
                        + project
                        + " AND summary ~ \""
                        + phrase
                        + "\" ORDER BY created DESC";

        String query = """
                query FindTests($jql: String!) {
                    getTests(
                        jql: $jql,
                        limit: 100
                    ) {
                        results {
                            issueId
                            jira(fields: ["key", "summary"])
                        }
                    }
                }
                """;

        JsonNode results =
                executeGraphql(
                        token,
                        query,
                        Map.of("jql", jql)
                )
                        .path("getTests")
                        .path("results");

        if (!results.isArray()
                || results.isEmpty()) {
            return null;
        }

        /*
         * JQL usa búsqueda textual.
         * Verificamos igualdad exacta para evitar
         * reutilizar un Test incorrecto.
         */
        for (JsonNode test : results) {

            String existingSummary =
                    test.path("jira")
                            .path("summary")
                            .asText();

            if (existingSummary.equals(summary)) {
                return test;
            }
        }

        return null;
    }

    // ============================================================
    // TEST PLAN
    // ============================================================

    public JsonNode createTestPlan(
            String token,
            String summary)
            throws IOException, InterruptedException {

        JsonNode testPlan =
                createXrayIssue(
                        token,
                        "createTestPlan",
                        "testPlan",
                        summary,
                        null
                );

        System.out.println(
                "Test Plan creado: "
                        + testPlan.path("jira")
                        .path("key").asText()
                        + " - "
                        + summary
        );

        return testPlan;
    }

    // ============================================================
    // TEST SET UPSERT
    // ============================================================

    public JsonNode upsertTestSet(
            String token,
            String summary,
            List<String> testIssueIds)
            throws IOException, InterruptedException {

        JsonNode existing =
                findTestSetBySummary(token, summary);

        if (existing == null) {

            JsonNode created =
                    createXrayIssue(
                            token,
                            "createTestSet",
                            "testSet",
                            summary,
                            testIssueIds
                    );

            System.out.println(
                    "Test Set creado: "
                            + created.path("jira")
                            .path("key").asText()
            );

            return created;
        }

        String issueId =
                existing.path("issueId").asText();

        Set<String> existingTests =
                getTestSetTestIds(token, issueId);

        Set<String> desiredTests =
                new LinkedHashSet<>(testIssueIds);

        List<String> testsToRemove =
                existingTests.stream()
                        .filter(id ->
                                !desiredTests.contains(id))
                        .toList();

        List<String> testsToAdd =
                desiredTests.stream()
                        .filter(id ->
                                !existingTests.contains(id))
                        .toList();

        if (!testsToRemove.isEmpty()) {

            updateTestSetTests(
                    token,
                    "removeTestsFromTestSet",
                    issueId,
                    testsToRemove
            );
        }

        if (!testsToAdd.isEmpty()) {

            updateTestSetTests(
                    token,
                    "addTestsToTestSet",
                    issueId,
                    testsToAdd
            );
        }

        System.out.println(
                "Test Set existente sincronizado: "
                        + existing.path("jira")
                        .path("key").asText()
                        + " | Tests: "
                        + desiredTests.size()
        );

        return existing;
    }

    // ============================================================
    // LINKS
    // ============================================================

    public void linkTestSetToTestPlan(
            String testSetKey,
            String testPlanKey)
            throws IOException, InterruptedException {

        linkJiraIssues(
                "Relates",
                testSetKey,
                testPlanKey
        );

        System.out.println(
                "Test Set "
                        + testSetKey
                        + " vinculado con Test Plan "
                        + testPlanKey
        );
    }

    public void linkTestPlanToUserStory(
            String testPlanKey)
            throws IOException, InterruptedException {

        String linkType =
                findUserStoryLinkType();

        linkJiraIssues(
                linkType,
                testPlanKey,
                config.getJiraUserStoryKey()
        );

        System.out.println(
                "Test Plan "
                        + testPlanKey
                        + " vinculado con HU "
                        + config.getJiraUserStoryKey()
        );
    }

    // ============================================================
    // TEST SET HELPERS
    // ============================================================

    private JsonNode findTestSetBySummary(
            String token,
            String summary)
            throws IOException, InterruptedException {

        String phrase =
                "\\\"" + escapeJqlValue(summary) + "\\\"";

        String jql =
                "project = "
                        + config.getProjectKey()
                        + " AND summary ~ \""
                        + phrase
                        + "\" ORDER BY created DESC";

        String query = """
                query FindTestSets($jql: String!) {
                    getTestSets(
                        jql: $jql,
                        limit: 100,
                        start: 0
                    ) {
                        results {
                            issueId
                            jira(fields: ["key", "summary"])
                        }
                    }
                }
                """;

        JsonNode results =
                executeGraphql(
                        token,
                        query,
                        Map.of("jql", jql)
                )
                        .path("getTestSets")
                        .path("results");

        if (!results.isArray()
                || results.isEmpty()) {
            return null;
        }

        for (JsonNode testSet : results) {

            if (testSet.path("jira")
                    .path("summary")
                    .asText()
                    .equals(summary)) {

                return testSet;
            }
        }

        return null;
    }

    private Set<String> getTestSetTestIds(
            String token,
            String issueId)
            throws IOException, InterruptedException {

        String query = """
                query GetTestSetTests(
                    $issueId: String!,
                    $start: Int
                ) {
                    getTestSet(issueId: $issueId) {
                        tests(
                            limit: 100,
                            start: $start
                        ) {
                            total
                            results {
                                issueId
                            }
                        }
                    }
                }
                """;

        Set<String> ids =
                new LinkedHashSet<>();

        int start = 0;
        int total;

        do {

            JsonNode tests =
                    executeGraphql(
                            token,
                            query,
                            Map.of(
                                    "issueId",
                                    issueId,
                                    "start",
                                    start
                            )
                    )
                            .path("getTestSet")
                            .path("tests");

            total = tests.path("total").asInt();

            JsonNode results =
                    tests.path("results");

            if (!results.isArray()
                    || results.isEmpty()) {
                break;
            }

            for (JsonNode test : results) {

                String id =
                        test.path("issueId")
                                .asText();

                if (!id.isBlank()) {
                    ids.add(id);
                }
            }

            start += results.size();

        } while (start < total);

        return ids;
    }

    private void updateTestSetTests(
            String token,
            String mutation,
            String issueId,
            List<String> testIssueIds)
            throws IOException, InterruptedException {

        String query;

        if (mutation.equals(
                "removeTestsFromTestSet")) {

            query = """
                    mutation RemoveTests(
                        $issueId: String!,
                        $testIssueIds: [String]!
                    ) {
                        removeTestsFromTestSet(
                            issueId: $issueId,
                            testIssueIds: $testIssueIds
                        )
                    }
                    """;

        } else {

            query = """
                    mutation AddTests(
                        $issueId: String!,
                        $testIssueIds: [String]!
                    ) {
                        addTestsToTestSet(
                            issueId: $issueId,
                            testIssueIds: $testIssueIds
                        ) {
                            addedTests
                            warning
                        }
                    }
                    """;
        }

        executeGraphql(
                token,
                query,
                Map.of(
                        "issueId",
                        issueId,
                        "testIssueIds",
                        testIssueIds
                )
        );
    }

    // ============================================================
    // USER STORY LINK TYPE
    // ============================================================

    private String findUserStoryLinkType()
            throws IOException, InterruptedException {

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        config.getJiraBaseUrl()
                                                + "/rest/api/3/issueLinkType"
                                )
                        )
                        .timeout(Duration.ofSeconds(30))
                        .header(
                                "Authorization",
                                jiraAuthorizationHeader()
                        )
                        .header(
                                "Accept",
                                "application/json"
                        )
                        .GET()
                        .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        ensureJiraSuccessful(response);

        JsonNode linkTypes =
                objectMapper.readTree(
                        response.body()
                ).path("issueLinkTypes");

        for (JsonNode linkType : linkTypes) {

            if (linkType.path("inward")
                    .asText()
                    .equalsIgnoreCase(
                            "is tested by")) {

                return linkType.path("name")
                        .asText();
            }
        }

        throw new IOException(
                "No se encontro el tipo de enlace "
                        + "'is tested by' en Jira."
        );
    }

    // ============================================================
    // JIRA ISSUE LINK
    // ============================================================

    private void linkJiraIssues(
            String linkType,
            String inwardIssueKey,
            String outwardIssueKey)
            throws IOException, InterruptedException {

        String body =
                objectMapper.writeValueAsString(
                        Map.of(
                                "type",
                                Map.of(
                                        "name",
                                        linkType
                                ),
                                "inwardIssue",
                                Map.of(
                                        "key",
                                        inwardIssueKey
                                ),
                                "outwardIssue",
                                Map.of(
                                        "key",
                                        outwardIssueKey
                                )
                        )
                );

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        config.getJiraBaseUrl()
                                                + "/rest/api/3/issueLink"
                                )
                        )
                        .timeout(Duration.ofSeconds(30))
                        .header(
                                "Authorization",
                                jiraAuthorizationHeader()
                        )
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .POST(
                                HttpRequest.BodyPublishers
                                        .ofString(body)
                        )
                        .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        ensureJiraSuccessful(response);
    }

    // ============================================================
    // XRAY GRAPHQL
    // ============================================================

    private JsonNode createXrayIssue(
            String token,
            String mutation,
            String resultField,
            String summary,
            List<String> testIssueIds)
            throws IOException, InterruptedException {

        String query = """
                mutation CreateXrayIssue(
                    $testIssueIds: [String],
                    $jira: JSON!
                ) {
                    %s(
                        testIssueIds: $testIssueIds,
                        jira: $jira
                    ) {
                        %s {
                            issueId
                            jira(fields: ["key"])
                        }
                        warnings
                    }
                }
                """.formatted(
                        mutation,
                        resultField
                );

        Map<String, Object> jira =
                Map.of(
                        "fields",
                        Map.of(
                                "summary", summary,
                                "project",
                                Map.of(
                                        "key",
                                        config.getProjectKey()
                                )
                        )
                );

        Map<String, Object> variables =
                new HashMap<>();

        variables.put(
                "jira",
                jira
        );

        if (testIssueIds != null) {
            variables.put(
                    "testIssueIds",
                    testIssueIds
            );
        }

        JsonNode data =
                executeGraphql(
                        token,
                        query,
                        variables
                );

        JsonNode issue =
                data.path(mutation)
                        .path(resultField);

        if (issue.path("issueId")
                .asText()
                .isBlank()) {

            throw new IOException(
                    "Xray no devolvio issueId para "
                            + resultField
                            + ": "
                            + data
            );
        }

        return issue;
    }

    private JsonNode executeGraphql(
            String token,
            String query,
            Map<String, Object> variables)
            throws IOException, InterruptedException {

        String body =
                objectMapper.writeValueAsString(
                        Map.of(
                                "query", query,
                                "variables", variables
                        )
                );

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        config.getXrayBaseUrl()
                                                + "/api/v2/graphql"
                                )
                        )
                        .timeout(Duration.ofSeconds(60))
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .header(
                                "Accept",
                                "application/json"
                        )
                        .POST(
                                HttpRequest.BodyPublishers
                                        .ofString(body)
                        )
                        .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        ensureSuccessful(response);

        JsonNode json =
                objectMapper.readTree(
                        response.body()
                );

        JsonNode errors =
                json.path("errors");

        if (errors.isArray()
                && !errors.isEmpty()) {

            throw new IOException(
                    "Error Xray GraphQL: "
                            + errors
            );
        }

        return json.path("data");
    }

    // ============================================================
    // UTILITIES
    // ============================================================

    private String jiraAuthorizationHeader() {

        String credentials =
                config.getJiraEmail()
                        + ":"
                        + config.getJiraApiToken();

        return "Basic "
                + Base64.getEncoder()
                .encodeToString(
                        credentials.getBytes(
                                StandardCharsets.UTF_8
                        )
                );
    }

    private String escapeJqlValue(
            String value) {

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private void ensureSuccessful(
            HttpResponse<String> response)
            throws IOException {

        if (response.statusCode() < 200
                || response.statusCode() >= 300) {

            throw new IOException(
                    "Xray HTTP "
                            + response.statusCode()
                            + ": "
                            + response.body()
            );
        }
    }

    private void ensureJiraSuccessful(
            HttpResponse<String> response)
            throws IOException {

        if (response.statusCode() < 200
                || response.statusCode() >= 300) {

            throw new IOException(
                    "Jira HTTP "
                            + response.statusCode()
                            + ": "
                            + response.body()
            );
        }
    }
}