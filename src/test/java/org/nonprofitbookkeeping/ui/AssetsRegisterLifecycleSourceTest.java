package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetsRegisterLifecycleSourceTest
{
    @Test
    void statusIsReadOnlyAndLifecycleActionsAreExplicit() throws Exception
    {
        String source = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/AssetsRegisterPanel.java"));

        assertTrue(source.contains("Deactivate Asset"));
        assertTrue(source.contains("Reactivate Asset"));
        assertTrue(source.contains("fixedAssets().changeStatus("));
        assertTrue(source.contains("Fixed assets are retained and never physically deleted"));
        assertTrue(source.contains("lifecycleStatus"));
        assertFalse(source.contains("ComboBox<FixedAsset.Status>"));
        assertFalse(source.contains("statusChoice"));
    }
}
