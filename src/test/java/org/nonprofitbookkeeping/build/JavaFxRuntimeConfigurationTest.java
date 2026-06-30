package org.nonprofitbookkeeping.build;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JavaFxRuntimeConfigurationTest
{
    private static final Set<String> REQUIRED_JAVAFX_ARTIFACTS = Set.of(
            "javafx-base",
            "javafx-graphics",
            "javafx-controls");

    @Test
    public void javaFxDependenciesAreAvailableAtRuntime() throws Exception
    {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        Document document = factory.newDocumentBuilder().parse(Path.of("pom.xml").toFile());

        NodeList dependencies = document.getElementsByTagName("dependency");
        int matched = 0;

        for (int index = 0; index < dependencies.getLength(); index++)
        {
            Node node = dependencies.item(index);
            if (!(node instanceof Element dependency))
            {
                continue;
            }

            String groupId = childText(dependency, "groupId");
            String artifactId = childText(dependency, "artifactId");
            if (!"org.openjfx".equals(groupId) || !REQUIRED_JAVAFX_ARTIFACTS.contains(artifactId))
            {
                continue;
            }

            String scope = childText(dependency, "scope");
            assertNotEquals("provided", scope,
                    artifactId + " must not use provided scope because javafx:run needs it at runtime");
            assertNotEquals("test", scope,
                    artifactId + " must not use test scope because javafx:run needs it at runtime");
            matched++;
        }

        assertTrue(matched == REQUIRED_JAVAFX_ARTIFACTS.size(),
                "All required JavaFX runtime dependencies must be declared");
    }

    private static String childText(Element parent, String tagName)
    {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0)
        {
            return "";
        }
        return nodes.item(0).getTextContent().trim();
    }
}
