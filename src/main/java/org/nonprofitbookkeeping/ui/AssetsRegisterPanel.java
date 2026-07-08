package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.FixedAsset;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.service.FixedAssetCommand;
import org.nonprofitbookkeeping.service.FixedAssetView;

import java.math.BigDecimal;
import java.time.LocalDate;

/** H2-backed fixed asset register panel. */
public class AssetsRegisterPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<FixedAssetView> table = new TableView<>();
    private final ComboBox<Account> assetAccount = new ComboBox<>();
    private final ComboBox<Account> accumulatedDepreciationAccount = new ComboBox<>();
    private final ComboBox<Account> depreciationExpenseAccount = new ComboBox<>();
    private final ComboBox<Fund> fund = new ComboBox<>();
    private final ComboBox<Integer> usefulLifeMonths = new ComboBox<>();
    private final ComboBox<FixedAsset.Status> statusChoice = new ComboBox<>();
    private final TextField name = new TextField();
    private final DatePicker acquisitionDate = new DatePicker(LocalDate.now());
    private final TextField acquisitionCost = new TextField("0.00");
    private final TextField salvageValue = new TextField("0.00");
    private final TextField openingAccumulatedDepreciation = new TextField("0.00");
    private final TextArea notes = new TextArea();
    private final Label status = new Label();
    private Long selectedAssetId;

    public AssetsRegisterPanel()
    {
        root.setPadding(new Insets(8));
        Label title = new Label("Asset Register");
        title.getStyleClass().add("panel-title");

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> reload());
        Button newAsset = new Button("New Asset");
        newAsset.setOnAction(e -> clearForm());
        Button save = new Button("Save Asset");
        save.setOnAction(e -> saveAsset());
        HBox actions = new HBox(8, refresh, newAsset, save);

        root.setTop(new VBox(6, title, actions, status, new Separator()));
        root.setCenter(new VBox(8, buildForm(), table));
        configureTable();
        configureChoices();
        reload();
    }

    private GridPane buildForm()
    {
        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(6);
        int row = 0;
        form.add(new Label("Name"), 0, row);
        form.add(name, 1, row++);
        form.add(new Label("Asset account"), 0, row);
        form.add(assetAccount, 1, row++);
        form.add(new Label("Accumulated depreciation account"), 0, row);
        form.add(accumulatedDepreciationAccount, 1, row++);
        form.add(new Label("Depreciation expense account"), 0, row);
        form.add(depreciationExpenseAccount, 1, row++);
        form.add(new Label("Fund"), 0, row);
        form.add(fund, 1, row++);
        form.add(new Label("Acquisition date"), 0, row);
        form.add(acquisitionDate, 1, row++);
        form.add(new Label("Acquisition cost"), 0, row);
        form.add(acquisitionCost, 1, row++);
        form.add(new Label("Salvage value"), 0, row);
        form.add(salvageValue, 1, row++);
        form.add(new Label("Useful life"), 0, row);
        form.add(usefulLifeMonths, 1, row++);
        form.add(new Label("Opening accumulated depreciation"), 0, row);
        form.add(openingAccumulatedDepreciation, 1, row++);
        form.add(new Label("Status"), 0, row);
        form.add(statusChoice, 1, row++);
        notes.setPrefRowCount(2);
        form.add(new Label("Notes"), 0, row);
        form.add(notes, 1, row);
        return form;
    }

    private void configureTable()
    {
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No fixed assets have been recorded."));
        addColumn("Name", "name", 180);
        addColumn("Asset Account", "assetAccountCode", 120);
        addColumn("Fund", "fundCode", 100);
        addColumn("Acquired", "acquisitionDate", 110);
        addColumn("Cost", "acquisitionCost", 110);
        addColumn("Accum. Dep.", "accumulatedDepreciation", 110);
        addColumn("Book Value", "currentBookValue", 110);
        addColumn("Next Dep.", "nextDepreciationAmount", 110);
        addColumn("Status", "status", 100);
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> fillForm(selected));
    }

    private void addColumn(String title, String property, double width)
    {
        TableColumn<FixedAssetView, String> column = new TableColumn<>(title);
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        column.setPrefWidth(width);
        column.setMinWidth(72);
        column.setSortable(true);
        column.setResizable(true);
        column.setReorderable(true);
        table.getColumns().add(column);
    }

    private void configureChoices()
    {
        StringConverter<Account> accountConverter = new StringConverter<>()
        {
            @Override
            public String toString(Account account)
            {
                return account == null ? "" : accountLabel(account);
            }

            @Override
            public Account fromString(String string)
            {
                return null;
            }
        };
        assetAccount.setConverter(accountConverter);
        accumulatedDepreciationAccount.setConverter(accountConverter);
        depreciationExpenseAccount.setConverter(accountConverter);
        fund.setConverter(new StringConverter<>()
        {
            @Override
            public String toString(Fund selectedFund)
            {
                return selectedFund == null ? "" : fundLabel(selectedFund);
            }

            @Override
            public Fund fromString(String string)
            {
                return null;
            }
        });
        usefulLifeMonths.setItems(FXCollections.observableArrayList(36, 60, 84));
        usefulLifeMonths.getSelectionModel().select(Integer.valueOf(60));
        statusChoice.setItems(FXCollections.observableArrayList(FixedAsset.Status.values()));
        statusChoice.getSelectionModel().select(FixedAsset.Status.ACTIVE);
    }

    private void reload()
    {
        status.setText("Loading fixed assets...");
        UiAsync.run("asset-register-load",
                () -> new AssetPanelData(
                        UiServiceRegistry.accountLookup().listActivePostingAccounts(),
                        UiServiceRegistry.fundLookup().listActiveFunds(),
                        UiServiceRegistry.fixedAssets().listAssets(activeCompanyCode())),
                data -> {
                    assetAccount.setItems(FXCollections.observableArrayList(data.accounts().stream()
                            .filter(a -> a.getAccountType() == AccountType.ASSET && a.getSubtype() == AccountSubtype.FIXED_ASSET)
                            .toList()));
                    accumulatedDepreciationAccount.setItems(FXCollections.observableArrayList(data.accounts().stream()
                            .filter(a -> a.getAccountType() == AccountType.ASSET)
                            .toList()));
                    depreciationExpenseAccount.setItems(FXCollections.observableArrayList(data.accounts().stream()
                            .filter(a -> a.getAccountType() == AccountType.EXPENSE)
                            .toList()));
                    fund.setItems(FXCollections.observableArrayList(data.funds()));
                    table.getItems().setAll(data.assets());
                    status.setText("Loaded " + data.assets().size() + " fixed asset(s) from H2.");
                },
                ex -> status.setText("Could not load fixed assets: " + UiErrors.safeMessage(ex)));
    }

    private void saveAsset()
    {
        try
        {
            FixedAssetCommand command = commandFromForm();
            FixedAssetView saved = selectedAssetId == null
                    ? UiServiceRegistry.fixedAssets().create(command)
                    : UiServiceRegistry.fixedAssets().update(selectedAssetId, command);
            selectedAssetId = saved.id();
            reload();
            status.setText("Saved fixed asset: " + saved.name());
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not save fixed asset: " + UiErrors.safeMessage(ex));
        }
    }

    private FixedAssetCommand commandFromForm()
    {
        return new FixedAssetCommand(
                activeCompanyCode(),
                requireSelected(assetAccount, "asset account").getId(),
                requireSelected(accumulatedDepreciationAccount, "accumulated depreciation account").getId(),
                requireSelected(depreciationExpenseAccount, "depreciation expense account").getId(),
                requireSelected(fund, "fund").getId(),
                name.getText(),
                acquisitionDate.getValue(),
                money(acquisitionCost.getText()),
                money(salvageValue.getText()),
                requireSelected(usefulLifeMonths, "useful life"),
                FixedAsset.DepreciationMethod.STRAIGHT_LINE,
                money(openingAccumulatedDepreciation.getText()),
                requireSelected(statusChoice, "status"),
                notes.getText());
    }

    private void fillForm(FixedAssetView asset)
    {
        if (asset == null)
        {
            return;
        }
        selectedAssetId = asset.id();
        name.setText(asset.name());
        acquisitionDate.setValue(asset.acquisitionDate());
        acquisitionCost.setText(asset.acquisitionCost().toPlainString());
        salvageValue.setText(asset.salvageValue().toPlainString());
        openingAccumulatedDepreciation.setText(asset.openingAccumulatedDepreciation().toPlainString());
        usefulLifeMonths.getSelectionModel().select(Integer.valueOf(asset.usefulLifeMonths()));
        statusChoice.getSelectionModel().select(asset.status());
        notes.setText(asset.notes());
        selectAccountById(assetAccount, asset.assetAccountId());
        selectAccountById(accumulatedDepreciationAccount, asset.accumulatedDepreciationAccountId());
        selectAccountById(depreciationExpenseAccount, asset.depreciationExpenseAccountId());
        selectFundById(fund, asset.fundId());
    }

    private void clearForm()
    {
        selectedAssetId = null;
        name.clear();
        acquisitionDate.setValue(LocalDate.now());
        acquisitionCost.setText("0.00");
        salvageValue.setText("0.00");
        openingAccumulatedDepreciation.setText("0.00");
        notes.clear();
        statusChoice.getSelectionModel().select(FixedAsset.Status.ACTIVE);
        usefulLifeMonths.getSelectionModel().select(Integer.valueOf(60));
        table.getSelectionModel().clearSelection();
        status.setText("Ready to enter a new fixed asset.");
    }

    private static <T> T requireSelected(ComboBox<T> comboBox, String label)
    {
        T selected = comboBox.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            throw new IllegalArgumentException("Select " + label);
        }
        return selected;
    }

    private static BigDecimal money(String raw)
    {
        return new BigDecimal(raw == null || raw.isBlank() ? "0" : raw.trim());
    }

    private static void selectAccountById(ComboBox<Account> comboBox, Long id)
    {
        comboBox.getItems().stream().filter(a -> a.getId().equals(id)).findFirst().ifPresent(comboBox.getSelectionModel()::select);
    }

    private static void selectFundById(ComboBox<Fund> comboBox, Long id)
    {
        comboBox.getItems().stream().filter(f -> f.getId().equals(id)).findFirst().ifPresent(comboBox.getSelectionModel()::select);
    }

    static String accountLabel(Account account)
    {
        String code = account.getCode() == null ? "" : account.getCode();
        String name = account.getName() == null ? "" : account.getName();
        return code.isBlank() ? name : code + " — " + name;
    }

    static String fundLabel(Fund selectedFund)
    {
        String code = selectedFund.getCode() == null ? "" : selectedFund.getCode();
        String name = selectedFund.getName() == null ? "" : selectedFund.getName();
        return code.isBlank() ? name : code + " — " + name;
    }

    private static String activeCompanyCode()
    {
        return MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
    }

    private record AssetPanelData(java.util.List<Account> accounts, java.util.List<Fund> funds, java.util.List<FixedAssetView> assets)
    {
    }

    @Override public String title() { return "Asset Register"; }
    @Override public Node root() { return root; }
}
