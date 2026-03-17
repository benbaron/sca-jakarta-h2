package org.nonprofitbookkeeping.service;

/**
 * Supported report export formats.
 */
public enum FinancialReportExportFormat
{
    TEXT("txt", "Text", "text/plain"),
    CSV("csv", "CSV", "text/csv"),
    PDF("pdf", "PDF", "application/pdf"),
    XLSX("xlsx", "Excel (.xlsx)", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final String extension;
    private final String label;
    private final String contentType;

    FinancialReportExportFormat(String extension, String label, String contentType)
    {
        this.extension = extension;
        this.label = label;
        this.contentType = contentType;
    }

    public String extension()
    {
        return extension;
    }

    public String label()
    {
        return label;
    }

    public String contentType()
    {
        return contentType;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
