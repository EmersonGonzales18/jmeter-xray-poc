package com.jmeterxray;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.File;

public class JMeterThreadGroupSplitter {

    private static final String DEFAULT_JMX =
            "jmeter/test.jmx";

    private static final String DEFAULT_OUTPUT =
            "temp";

    public static void main(String[] args) throws Exception {

        String jmxPath =
                args.length > 0 ? args[0] : DEFAULT_JMX;

        String outputPath =
                args.length > 1 ? args[1] : DEFAULT_OUTPUT;

        File sourceFile = new File(jmxPath);
        File outputDirectory = new File(outputPath);

        System.out.println("====================================");
        System.out.println(" JMeter Thread Group Splitter");
        System.out.println("====================================");

        System.out.println("JMX: " + sourceFile.getAbsolutePath());

        if (!sourceFile.exists()) {
            throw new IllegalArgumentException(
                    "No existe el archivo JMX: "
                            + sourceFile.getAbsolutePath()
            );
        }

        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }

        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();

        DocumentBuilder builder =
                factory.newDocumentBuilder();

        Document original =
                builder.parse(sourceFile);

        NodeList threadGroups =
                original.getElementsByTagName("ThreadGroup");

        if (threadGroups.getLength() == 0) {
            throw new IllegalStateException(
                    "No se encontraron Thread Groups en el JMX."
            );
        }

        System.out.println(
                "Thread Groups encontrados: "
                        + threadGroups.getLength()
        );

        System.out.println();

        for (int selectedIndex = 0;
             selectedIndex < threadGroups.getLength();
             selectedIndex++) {

            /*
             * Volvemos a cargar el JMX original para que
             * cada archivo generado sea independiente.
             */
            Document copy =
                    builder.parse(sourceFile);

            NodeList copyGroups =
                    copy.getElementsByTagName("ThreadGroup");

            Element selectedGroup =
                    (Element) copyGroups.item(selectedIndex);

            String originalName =
                    selectedGroup.getAttribute("testname");

            if (originalName == null ||
                    originalName.isBlank()) {

                originalName =
                        "ThreadGroup-" + (selectedIndex + 1);
            }

            String safeName =
                    sanitizeFileName(originalName);

            /*
             * Solo dejamos habilitado el Thread Group
             * correspondiente a esta ejecución.
             */
            for (int i = 0;
                 i < copyGroups.getLength();
                 i++) {

                Element group =
                        (Element) copyGroups.item(i);

                group.setAttribute(
                        "enabled",
                        i == selectedIndex
                                ? "true"
                                : "false"
                );
            }

            String fileName =
                    String.format(
                            "%02d-%s.jmx",
                            selectedIndex + 1,
                            safeName
                    );

            File outputFile =
                    new File(
                            outputDirectory,
                            fileName
                    );

            saveDocument(copy, outputFile);

            System.out.println(
                    "[" + (selectedIndex + 1) + "] "
                            + originalName
            );

            System.out.println(
                    "    -> " + outputFile.getPath()
            );
        }

        System.out.println();
        System.out.println(
                "JMX generados correctamente."
        );
    }

    private static String sanitizeFileName(String name) {

        String sanitized =
                name.replaceAll(
                        "[^a-zA-Z0-9._-]+",
                        "-"
                );

        sanitized =
                sanitized.replaceAll(
                        "^-+|-+$",
                        ""
                );

        if (sanitized.isBlank()) {
            return "ThreadGroup";
        }

        return sanitized;
    }

    private static void saveDocument(
            Document document,
            File outputFile
    ) throws Exception {

        TransformerFactory transformerFactory =
                TransformerFactory.newInstance();

        Transformer transformer =
                transformerFactory.newTransformer();

        transformer.setOutputProperty(
                OutputKeys.INDENT,
                "yes"
        );

        transformer.setOutputProperty(
                OutputKeys.ENCODING,
                "UTF-8"
        );

        transformer.transform(
                new DOMSource(document),
                new StreamResult(outputFile)
        );
    }
}
