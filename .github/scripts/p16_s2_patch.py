from pathlib import Path


def replace_once(path, before, after):
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(before)
    if count != 1:
        raise SystemExit(f"{path}: expected one occurrence, found {count}: {before[:100]!r}")
    p.write_text(text.replace(before, after), encoding="utf-8")


registry = "src/main/java/org/nonprofitbookkeeping/ui/UiServiceRegistry.java"
replace_once(
    registry,
    "import org.nonprofitbookkeeping.service.CompanyUiPreferencesService;\n",
    "import org.nonprofitbookkeeping.service.CompanyUiPreferencesService;\n"
    "import org.nonprofitbookkeeping.service.CoaCsvImportService;\n",
)
replace_once(
    registry,
    "    public static AccountAdminService accountAdmin() { return services().accountAdmin(); }\n",
    "    public static AccountAdminService accountAdmin() { return services().accountAdmin(); }\n"
    "    public static CoaCsvImportService coaCsvImport()\n"
    "    {\n"
    "        return new CoaCsvImportService(services().jpa(), UiServiceRegistry::activeCompanyCode);\n"
    "    }\n",
)

panel = "src/main/java/org/nonprofitbookkeeping/ui/ImportPreviewPanel.java"
replace_once(
    panel,
    "import org.nonprofitbookkeeping.service.CoaCsvMapper;\n",
    "import org.nonprofitbookkeeping.service.CoaCsvMapper;\n"
    "import org.nonprofitbookkeeping.service.CoaCsvImportService;\n",
)
replace_once(
    panel,
    "    private ImportPreviewService.CoaPreviewResult lastCoaPreview;\n",
    "    private CoaCsvImportService.CoaCsvBatchPreview lastCoaPreview;\n",
)

before_commit = '''    private void commitAcceptedCoaRows()
    {
        ImportPreviewService.CoaPreviewResult preview = lastCoaPreview;
        if (preview == null)
        {
            status.setText("Commit unavailable: preview a COA CSV first.");
            return;
        }
        if (preview.acceptedRows().isEmpty())
        {
            status.setText("Commit skipped: there are no accepted COA rows to commit.");
            return;
        }

        status.setText("Committing accepted COA rows...");
        runCommitOperation("import-preview-commit-coa", "Committing accepted COA rows",
                () -> previewService.commitAcceptedCoaRows(
                preview.acceptedRows(),
                row -> UiServiceRegistry.accountAdmin().upsert(
                        row.code(),
                        row.name(),
                        parseAccountTypeToken(row.accountType()),
                        parseNormalBalanceToken(row.normalBalance()),
                        null,
                        row.parentCode(),
                        true)),
                result -> {
                    status.setText("Committed " + result.committedCount() + " of " + result.totalAccepted()
                            + " accepted COA row(s); failed=" + result.failedCount() + ".");
                    warnings.getItems().setAll(result.errors());
                },
                ex -> status.setText("Could not commit accepted COA rows: " + UiErrors.safeMessage(ex)));
    }
'''
after_commit = '''    private void commitAcceptedCoaRows()
    {
        CoaCsvImportService.CoaCsvBatchPreview preview = lastCoaPreview;
        String actor = sclxActor.getText().strip();
        if (preview == null)
        {
            status.setText("Commit unavailable: preview a COA CSV first.");
            return;
        }
        if (preview.acceptedRows().isEmpty())
        {
            status.setText("Commit skipped: there are no accepted COA rows to commit.");
            return;
        }
        if (preview.hasBlockingErrors())
        {
            status.setText("Commit blocked: resolve the COA CSV preview errors and preview again.");
            commitAccepted.setDisable(true);
            return;
        }
        if (actor.isBlank())
        {
            status.setText("Commit blocked: enter an import actor for factual audit history.");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Commit " + preview.acceptedCount() + " accepted COA row(s) into "
                        + preview.companyCode() + " / " + preview.targetChartLabel()
                        + "?\\n\\nSHA-256: " + preview.sourceSha256()
                        + "\\nRejected rows remain excluded. The accepted batch, external identities, and audit fact "
                        + "commit in one transaction; any failure rolls everything back.",
                ButtonType.OK, ButtonType.CANCEL);
        confirmation.setTitle("Confirm Atomic COA CSV Commit");
        confirmation.setHeaderText("Commit the exact previewed accepted rows");
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK)
        {
            status.setText("COA CSV commit cancelled; no data was changed.");
            return;
        }

        CoaCsvImportService.CoaCsvBatchPreview confirmed = preview.confirmedCopy();
        commitAccepted.setDisable(true);
        status.setText("Committing the exact accepted COA CSV batch atomically...");
        runCommitOperation("import-preview-commit-coa", "Committing accepted COA rows atomically",
                () -> UiServiceRegistry.coaCsvImport().commit(confirmed, actor),
                result ->
                {
                    warnings.getItems().setAll(result.errors());
                    if (result.committed())
                    {
                        status.setText("Committed accepted COA batch: created=" + result.createdCount()
                                + ", updated=" + result.updatedCount()
                                + ", skipped=" + result.skippedCount() + ".");
                    }
                    else
                    {
                        status.setText("COA CSV commit did not change data"
                                + (result.rolledBack() ? "; the batch was rolled back" : "")
                                + ". Preview again before retrying. "
                                + String.join("; ", result.errors()));
                    }
                },
                ex ->
                {
                    status.setText("Could not commit accepted COA rows; no successful batch was reported: "
                            + UiErrors.safeMessage(ex));
                });
    }
'''
replace_once(panel, before_commit, after_commit)

before_parse = '''    static AccountType parseAccountTypeToken(String token)
    {
        String normalized = normalizeEnumToken(token);
        if ("REVENUE".equals(normalized))
        {
            return AccountType.INCOME;
        }
        return AccountType.valueOf(normalized);
    }

    static NormalBalance parseNormalBalanceToken(String token)
    {
        String normalized = normalizeEnumToken(token);
        if ("DR".equals(normalized))
        {
            return NormalBalance.DEBIT;
        }
        if ("CR".equals(normalized))
        {
            return NormalBalance.CREDIT;
        }
        return NormalBalance.valueOf(normalized);
    }
'''
after_parse = '''    static AccountType parseAccountTypeToken(String token)
    {
        return CoaCsvImportService.parseAccountTypeToken(token);
    }

    static NormalBalance parseNormalBalanceToken(String token)
    {
        return CoaCsvImportService.parseNormalBalanceToken(token);
    }
'''
replace_once(panel, before_parse, after_parse)

before_preview = '''    private void previewCoa(Path file)
    {
        clearCoaPreview();
        clearSclxPreview();
        clearBankPreview();
        status.setText("Previewing COA CSV without changing the active company...");
        runPreviewOperation(
                "import-preview-coa",
                "Reading and validating COA CSV",
                "COA CSV preview cancelled before commit; no data was changed.",
                () -> previewService.previewCoaCsv(file), result -> {
            clearSclxPreview();
            clearBankPreview();
            lastCoaPreview = result;
            acceptedCoaRows.getItems().setAll(result.acceptedRows());
            rejectedCoaRows.getItems().setAll(result.rejectedRows());
            warnings.getItems().setAll(result.warnings());
            commitAccepted.setDisable(result.acceptedRows().isEmpty());
            previewTabs.getSelectionModel().select(0);
            status.setText("Previewed " + result.totalRowCount()
                    + " COA row(s) from " + result.sourceName()
                    + ": accepted " + result.acceptedCount() + ", rejected " + result.rejectedCount() + ".");
        }, ex -> {
            clearBankPreview();
            warnings.getItems().clear();
            acceptedCoaRows.getItems().clear();
            rejectedCoaRows.getItems().clear();
            commitAccepted.setDisable(true);
            status.setText("Could not preview COA CSV: " + UiErrors.safeMessage(ex));
        });
    }
'''
after_preview = '''    private void previewCoa(Path file)
    {
        clearCoaPreview();
        clearSclxPreview();
        clearBankPreview();
        status.setText("Previewing COA CSV without changing the active company...");
        runPreviewOperation(
                "import-preview-coa",
                "Reading and validating COA CSV",
                "COA CSV preview cancelled before commit; no data was changed.",
                () -> UiServiceRegistry.coaCsvImport().preview(file), result ->
                {
                    clearSclxPreview();
                    clearBankPreview();
                    lastCoaPreview = result;
                    acceptedCoaRows.getItems().setAll(result.acceptedRows());
                    rejectedCoaRows.getItems().setAll(result.rejectedRows());
                    ArrayList<String> messages = new ArrayList<>(result.warnings());
                    result.blockingErrors().forEach(message -> messages.add("BLOCKING: " + message));
                    warnings.getItems().setAll(messages);
                    commitAccepted.setDisable(result.acceptedRows().isEmpty() || result.hasBlockingErrors());
                    previewTabs.getSelectionModel().select(0);
                    status.setText("Previewed " + result.totalRowCount()
                            + " COA row(s) from " + result.sourceName()
                            + " for " + result.companyCode() + " / " + result.targetChartLabel()
                            + ": accepted " + result.acceptedCount() + ", rejected " + result.rejectedCount()
                            + ", blocking " + result.blockingErrors().size()
                            + ". SHA-256 " + result.sourceSha256() + ". No data was changed.");
                }, ex ->
                {
                    clearBankPreview();
                    warnings.getItems().clear();
                    acceptedCoaRows.getItems().clear();
                    rejectedCoaRows.getItems().clear();
                    commitAccepted.setDisable(true);
                    status.setText("Could not preview COA CSV: " + UiErrors.safeMessage(ex));
                });
    }
'''
replace_once(panel, before_preview, after_preview)
