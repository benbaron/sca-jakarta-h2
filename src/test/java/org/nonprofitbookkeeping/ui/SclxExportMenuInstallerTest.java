package org.nonprofitbookkeeping.ui;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.interchange.sclx.SclxExportResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SclxExportMenuInstallerTest
{
    @Test
    void fileMenuRoutesSelectedCompanyExportAndInstallsOnlyOnce()
    {
        FxTestSupport.onFx(() ->
        {
            FakeActions actions = new FakeActions();
            Menu file = new Menu("File");
            MenuItem switchDatabase = new MenuItem("Switch to Validated Copy");
            switchDatabase.setId(DatabaseTransferMenuInstaller.SWITCH_MENU_ID);
            file.getItems().setAll(
                    new MenuItem("Select Database File…"),
                    new MenuItem("Retry / Repair Current Database"),
                    switchDatabase,
                    new SeparatorMenuItem(),
                    new MenuItem("Save"),
                    new MenuItem("Exit"));
            MenuBar menuBar = new MenuBar(file);

            SclxExportMenuInstaller.install(menuBar, actions);
            SclxExportMenuInstaller.install(menuBar, actions);

            MenuItem export = byId(file, SclxExportMenuInstaller.EXPORT_MENU_ID);
            assertNotNull(export);
            assertEquals(SclxExportMenuInstaller.EXPORT_MENU_TEXT, export.getText());
            assertEquals(1, file.getItems().stream()
                    .filter(item -> SclxExportMenuInstaller.EXPORT_MENU_ID.equals(item.getId()))
                    .count());
            assertTrue(file.getItems().indexOf(export) > file.getItems().indexOf(switchDatabase));
            assertFalse(export.isDisable());

            export.fire();
            assertEquals(1, actions.exportRequests);

            actions.available.set(false);
            assertTrue(export.isDisable());
            actions.available.set(true);
            actions.busy.set(true);
            assertTrue(export.isDisable());
            return null;
        });
    }

    private static MenuItem byId(Menu menu, String id)
    {
        return menu.getItems().stream()
                .filter(item -> id.equals(item.getId()))
                .findFirst()
                .orElse(null);
    }

    private static final class FakeActions implements SclxExportActions
    {
        private final SimpleBooleanProperty busy = new SimpleBooleanProperty(false);
        private final SimpleBooleanProperty available = new SimpleBooleanProperty(true);
        private final SimpleStringProperty status = new SimpleStringProperty("Ready");
        private final SimpleObjectProperty<SclxExportResult> lastResult = new SimpleObjectProperty<>();
        private int exportRequests;

        @Override
        public ReadOnlyBooleanProperty busyProperty()
        {
            return busy;
        }

        @Override
        public ReadOnlyBooleanProperty availableProperty()
        {
            return available;
        }

        @Override
        public ReadOnlyStringProperty statusProperty()
        {
            return status;
        }

        @Override
        public ReadOnlyObjectProperty<SclxExportResult> lastResultProperty()
        {
            return lastResult;
        }

        @Override
        public void requestExport()
        {
            exportRequests++;
        }
    }
}
