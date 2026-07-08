package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source-level guardrail against disabled Delete placeholder buttons. */
class DeletePlaceholderSourceTest
{
    @Test
    void productionSourcesDoNotContainDeleteUnavailablePlaceholderButtons() throws IOException
    {
        Path sourceRoot = Path.of("src/main/java/org/nonprofitbookkeeping/ui");
        List<String> offenders;
        try (var paths = Files.walk(sourceRoot))
        {
            offenders = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(DeletePlaceholderSourceTest::containsDeleteUnavailablePlaceholderButton)
                    .map(sourceRoot::relativize)
                    .map(Path::toString)
                    .toList();
        }

        assertTrue(offenders.isEmpty(), "Disabled Delete placeholder button remains in: " + offenders);
    }

    private static boolean containsDeleteUnavailablePlaceholderButton(Path path)
    {
        try
        {
            return Files.readAllLines(path).stream().anyMatch(DeletePlaceholderSourceTest::isDeletePlaceholderButtonLine);
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Could not inspect " + path, ex);
        }
    }

    private static boolean isDeletePlaceholderButtonLine(String line)
    {
        String compact = line.replace(" ", "");
        return compact.contains("newButton(\"Deleteunavailable")
                || compact.contains("newButton(\"DeleteBankunavailable")
                || compact.contains("deleteUnavailable=");
    }
}
