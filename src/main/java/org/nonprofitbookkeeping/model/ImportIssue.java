package org.nonprofitbookkeeping.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Durable validation or review issue for an import batch or statement line. */
@Entity
@Table(name = "import_issue",
       indexes = {
           @Index(name = "ix_import_issue_batch", columnList = "batch_id"),
           @Index(name = "ix_import_issue_line", columnList = "statement_line_id"),
           @Index(name = "ix_import_issue_severity", columnList = "severity")
       })
public class ImportIssue
{
    public enum Severity { INFO, WARNING, ERROR }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portable_id", nullable = false, unique = true, updatable = false)
    private UUID portableId = UUID.randomUUID();

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private BankImportBatch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "statement_line_id")
    private BankStatementLine statementLine;

    @Column(name = "source_row_number")
    private Integer sourceRowNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity = Severity.INFO;

    @Column(nullable = false, length = 80)
    private String code;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public UUID getPortableId() { return portableId; }
    public BankImportBatch getBatch() { return batch; }
    public void setBatch(BankImportBatch batch) { this.batch = batch; }
    public BankStatementLine getStatementLine() { return statementLine; }
    public void setStatementLine(BankStatementLine statementLine) { this.statementLine = statementLine; }
    public Integer getSourceRowNumber() { return sourceRowNumber; }
    public void setSourceRowNumber(Integer sourceRowNumber) { this.sourceRowNumber = sourceRowNumber; }
    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Instant getCreatedAt() { return createdAt; }
}
