package org.nonprofitbookkeeping.interchange.sclx;

/** Governed selected-company SCLX section classification. */
public enum SclxExportSection
{
    COMPANY(Support.INCLUDED, "organization", "Selected company identity and fiscal settings"),
    CHART_OF_ACCOUNTS(Support.INCLUDED, "chartOfAccounts", "Active company chart and accounts"),
    FUNDS(Support.INCLUDED, "funds", "Company funds and hierarchy"),
    BUDGETS(Support.INCLUDED, "budgets", "Budget categories, plans, and lines"),
    ACTIVITIES(Support.EXTENSION, true, "extensions.scaJakartaH2.activities", "Company activities"),
    COUNTERPARTIES(Support.EXTENSION, true, "extensions.scaJakartaH2.counterparties", "Company counterparties, merchants, and transaction references"),
    TRANSACTIONS(Support.INCLUDED, "transactions", "Canonical balanced transactions and splits"),
    SUPPLEMENTAL_DETAILS(Support.EXTENSION, true, "extensions.scaJakartaH2.supplementalDetails", "Canonical transaction supplemental details"),
    BANK_CONFIGURATION(Support.EXTENSION, "extensions.scaJakartaH2.bankConfiguration", "Configured bank accounts without credentials"),
    BANK_STATEMENT_FACTS(Support.EXTENSION, "extensions.scaJakartaH2.bankStatementFacts", "Reviewed statement and import facts"),
    RECONCILIATION(Support.EXTENSION, "extensions.scaJakartaH2.reconciliation", "Matching and reconciliation facts"),
    FIXED_ASSETS(Support.EXTENSION, "extensions.scaJakartaH2.fixedAssets", "Fixed assets and completed depreciation"),
    INVENTORY(Support.EXTENSION, "extensions.scaJakartaH2.inventory", "Inventory items and movements"),
    PERIOD_CLOSE(Support.EXTENSION, "extensions.scaJakartaH2.periodClose", "Close ranges and factual close history"),
    AUDIT_HISTORY(Support.EXTENSION, "extensions.scaJakartaH2.auditHistory", "Company-owned factual audit events"),

    USERS_AND_AUTHENTICATION(Support.EXCLUDED, null, "Users, roles, credentials, password hashes, and login state"),
    UI_STATE(Support.EXCLUDED, null, "JavaFX layout, table, divider, and recent-company state"),
    DATABASE_INTERNALS(Support.EXCLUDED, null, "H2, Flyway, JDBC, database paths, and credentials"),
    FILESYSTEM_PATHS(Support.EXCLUDED, null, "Source-machine absolute paths"),
    RAW_ATTACHMENTS(Support.EXCLUDED, null, "Raw documents and arbitrary executable content"),
    COMPATIBILITY_LEDGER(Support.EXCLUDED, null, "JournalTransaction and PostingLine compatibility authority"),
    OPEN_ITEM_COMPATIBILITY(Support.EXCLUDED, null, "Compatibility open-item and former Schedules authority"),
    GENERIC_JOB_HISTORY(Support.EXCLUDED, null, "Eliminated generic import/export job history"),
    OTHER_COMPANY_RECORDS(Support.EXCLUDED, null, "Records owned by another company");

    private final Support support;
    private final boolean includedByCurrentSnapshot;
    private final String outputPath;
    private final String description;

    SclxExportSection(Support support, String outputPath, String description)
    {
        this(support, support == Support.INCLUDED, outputPath, description);
    }

    SclxExportSection(
            Support support,
            boolean includedByCurrentSnapshot,
            String outputPath,
            String description)
    {
        this.support = support;
        this.includedByCurrentSnapshot = includedByCurrentSnapshot;
        this.outputPath = outputPath;
        this.description = description;
    }

    public Support support()
    {
        return support;
    }

    public String outputPath()
    {
        return outputPath;
    }

    public String description()
    {
        return description;
    }

    public boolean exported()
    {
        return support != Support.EXCLUDED;
    }

    public boolean includedByCurrentSnapshot()
    {
        return includedByCurrentSnapshot;
    }

    public boolean deferred()
    {
        return support == Support.EXTENSION && !includedByCurrentSnapshot;
    }

    public enum Support
    {
        INCLUDED,
        EXTENSION,
        EXCLUDED
    }
}
