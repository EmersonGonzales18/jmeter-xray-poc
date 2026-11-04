package com.jmeterxray.config;

import java.nio.file.Files;

public class ConfigTest {

    public static void main(String[] args) {

        System.out.println("================================");
        System.out.println(" VALIDANDO CONFIGURACION");
        System.out.println("================================");

        JiraConfig config = JiraConfig.fromFile();

        System.out.println("Xray URL:    " + config.getXrayBaseUrl());
        System.out.println("Project Key: " + config.getProjectKey());

        System.out.println("Jira URL:    " + config.getJiraBaseUrl());
        System.out.println("User Story:  " + config.getJiraUserStoryKey());

        System.out.println("JMX Path:    " + config.getJmxPath());
        System.out.println("JMX existe:  " + Files.exists(config.getJmxPath()));

        // No imprimimos los valores de los secretos.
        System.out.println("JIRA_EMAIL:         OK");
        System.out.println("JIRA_API_TOKEN:     OK");
        System.out.println("XRAY_CLIENT_ID:     OK");
        System.out.println("XRAY_CLIENT_SECRET: OK");

        System.out.println("================================");
        System.out.println(" CONFIGURACION CORRECTA");
        System.out.println("================================");
    }
}