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
    void productionSourcesDoNotContainDeleteUnavailablePlaceholders() throws IOException
    {
        Path sourceRoot = Path.of("src/main/java/org/nonprofitbookkeeping/ui");
        List<String> offenders;
        try (var paths = Files.walk(sourceRoot))
        {
            offenders = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(DeletePlaceholderSourceTest::containsDeleteUnavailable)
                    .map(sourceRoot::relativize)
                    .map(Path::toString)
                    .toList();
        }

        assertTrue(offenders.isEmpty(), "Disabled Delete placeholder text remains in: " + offenders);
    }

    private static boolean containsDeleteUnavailable(Path path)
    {
        try
        {
            String source = Files.readString(path);
            return source.contains("Delete unavailable")
                    || source.contains("deleteUnavailable")
                    || source.contains("Delete Bank unavailable");
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Could not inspect " + path, ex);
        }
    }
}
