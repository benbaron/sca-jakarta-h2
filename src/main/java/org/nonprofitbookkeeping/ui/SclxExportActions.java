package org.nonprofitbookkeeping.ui;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import org.nonprofitbookkeeping.interchange.sclx.SclxExportResult;

/** Shell-owned action contract for selected-company SCLX export. */
interface SclxExportActions
{
    ReadOnlyBooleanProperty busyProperty();

    ReadOnlyBooleanProperty availableProperty();

    ReadOnlyStringProperty statusProperty();

    ReadOnlyObjectProperty<SclxExportResult> lastResultProperty();

    void requestExport();

    static SclxExportActions unavailable()
    {
        return new SclxExportActions()
        {
            private final SimpleBooleanProperty busy = new SimpleBooleanProperty(false);
            private final SimpleBooleanProperty available = new SimpleBooleanProperty(false);
            private final SimpleStringProperty status = new SimpleStringProperty(
                    "SCLX export requires an active database and selected company.");
            private final SimpleObjectProperty<SclxExportResult> lastResult = new SimpleObjectProperty<>();

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
                status.set("SCLX export requires the production workspace window.");
            }
        };
    }
}
