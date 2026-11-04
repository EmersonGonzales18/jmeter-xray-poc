package com.jmeterxray.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Properties;

public final class JiraConfig {

    private static final String DEFAULT_XRAY_BASE_URL =
            "https://xray.cloud.getxray.app";

    private final String xrayBaseUrl;
    private final String projectKey;

    private final String jiraBaseUrl;
    private final String jiraEmail;
    private final String jiraApiToken;
    private final String jiraUserStoryKey;

    private final String clientId;
    private final String clientSecret;

    private final Path jmxPath;

    private JiraConfig(
            String xrayBaseUrl,
            String projectKey,
            String jiraBaseUrl,
            String jiraEmail,
            String jiraApiToken,
            String jiraUserStoryKey,
            String clientId,
            String clientSecret,
            Path jmxPath) {

        this.xrayBaseUrl = xrayBaseUrl;
        this.projectKey = projectKey;

        this.jiraBaseUrl = jiraBaseUrl;
        this.jiraEmail = jiraEmail;
        this.jiraApiToken = jiraApiToken;
        this.jiraUserStoryKey = jiraUserStoryKey;

        this.clientId = clientId;
        this.clientSecret = clientSecret;

        this.jmxPath = jmxPath;
    }

    public static JiraConfig fromFile() {

        Properties properties = new Properties();

        try (InputStream input =
                     JiraConfig.class.getClassLoader()
                             .getResourceAsStream("jira.properties")) {

            if (input == null) {
                throw new IllegalStateException(
                        "No se encontro jira.properties"
                );
            }

            properties.load(input);

        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo leer jira.properties",
                    e
            );
        }

        String xrayBaseUrl =
                property(
                        properties,
                        "xray.baseUrl",
                        DEFAULT_XRAY_BASE_URL
                ).replaceAll("/+$", "");

        String projectKey =
                requiredProperty(
                        properties,
                        "xray.projectKey"
                );

        String jiraBaseUrl =
                requiredProperty(
                        properties,
                        "jira.baseUrl"
                ).replaceAll("/+$", "");

        String jiraUserStoryKey =
                requiredProperty(
                        properties,
                        "jira.userStoryKey"
                );

        String jmxPath =
                requiredProperty(
                        properties,
                        "jmeter.jmxPath"
                );

        /*
         * SECRETOS
         *
         * Estos NO vienen del properties.
         * GitHub Actions los proporciona
         * mediante variables de entorno.
         */

        String jiraEmail =
                requiredEnvironment("JIRA_EMAIL");

        String jiraApiToken =
                requiredEnvironment("JIRA_API_TOKEN");

        String clientId =
                requiredEnvironment("XRAY_CLIENT_ID");

        String clientSecret =
                requiredEnvironment("XRAY_CLIENT_SECRET");

        /*
         * Validamos URLs.
         */

        URI.create(xrayBaseUrl);
        URI.create(jiraBaseUrl);

        return new JiraConfig(
                xrayBaseUrl,
                projectKey,
                jiraBaseUrl,
                jiraEmail,
                jiraApiToken,
                jiraUserStoryKey,
                clientId,
                clientSecret,
                Path.of(jmxPath)
        );
    }

    private static String property(
            Properties properties,
            String name,
            String defaultValue) {

        String value = properties.getProperty(name);

        return value == null || value.isBlank()
                ? defaultValue
                : value.trim();
    }

    private static String requiredProperty(
            Properties properties,
            String name) {

        String value = properties.getProperty(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Falta la propiedad: " + name
            );
        }

        return value.trim();
    }

    private static String requiredEnvironment(
            String name) {

        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Falta la variable de entorno: " + name
            );
        }

        return value.trim();
    }

    public String getXrayBaseUrl() {
        return xrayBaseUrl;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public String getJiraBaseUrl() {
        return jiraBaseUrl;
    }

    public String getJiraEmail() {
        return jiraEmail;
    }

    public String getJiraApiToken() {
        return jiraApiToken;
    }

    public String getJiraUserStoryKey() {
        return jiraUserStoryKey;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public Path getJmxPath() {
        return jmxPath;
    }
}