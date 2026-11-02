package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.interchange.sclx.SclxVersion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prevents the final operating and release guides from drifting from production labels and formats. */
class ReleaseOperatingGuideTest
{
    @Test
    void operatingGuideUsesProductionNavigationAndTransferLabels() throws Exception
    {
        String guide = Files.readString(Path.of("doc/operating-guide.md"));
        String navigation = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/NavigationPane.java"));
        String administration = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/AdministrationPanel.java"));
        String transfer = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/DatabaseTransferPanel.java"));
        String coordinator = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/DatabaseTransferCoordinator.java"));

        for (String label : Set.of("Journal", "Merchant Settlement Assistant", "Event Accounting",
                "Bank Reconciliation", "Budget Editor", "Budget vs Actual", "Recognition Assistant",
                "Asset Register", "Inventory", "Property Custody", "Import Preview",
                "Opening Conversion", "Period Close", "Report Library", "Fund Transfers",
                "Events / Activities", "Administration"))
        {
            assertTrue(navigation.contains("\"" + label + "\""), label);
            assertTrue(guide.contains("**" + label + "**"), label);
        }
        assertTrue(administration.contains("tab(\"Company Admin\""));
        assertTrue(administration.contains("tab(\"Database Transfer\""));
        assertTrue(guide.contains("**Company Admin**"));
        assertTrue(guide.contains("**Database Transfer**"));
        assertTrue(transfer.contains("new Button(\"Backup Database…\")"));
        assertTrue(transfer.contains("new Button(\"Restore Database Copy…\")"));
        assertTrue(coordinator.contains("\"Back Up Database\""));
        assertTrue(coordinator.contains("\"Switch Database\""));
        assertTrue(guide.contains("**Backup Database…**"));
        assertTrue(guide.contains("**Restore Database Copy…**"));
        assertTrue(guide.contains("**Back Up Database**"));
        assertTrue(guide.contains("**Switch Database**"));
    }

    @Test
    void releaseNotesMatchSupportedSclxVersions() throws Exception
    {
        String notes = Files.readString(Path.of("doc/release-notes-P23-P28.md"));
        Set<String> readable = Arrays.stream(SclxVersion.values())
                .filter(SclxVersion::readable)
                .map(SclxVersion::externalValue)
                .collect(Collectors.toSet());
        assertEquals(Set.of("1.0", "1.2", "1.3"), readable);
        assertEquals("1.3", SclxVersion.writerVersion().externalValue());
        assertTrue(notes.contains("SCLX readers: 1.0, 1.2 and 1.3"));
        assertTrue(notes.contains("SCLX writer: deterministic 1.3"));
    }
}
