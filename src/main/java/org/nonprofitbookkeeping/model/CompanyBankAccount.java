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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Bank account owned by a company/branch. */
@Entity
@Table(name = "company_bank_account",
       indexes = {
           @Index(name = "ix_company_bank_account_company", columnList = "company_id"),
           @Index(name = "ix_company_bank_account_active", columnList = "is_active"),
           @Index(name = "ix_company_bank_account_bank", columnList = "bank_id"),
           @Index(name = "ix_company_bank_account_account", columnList = "account_id")
       })
public class CompanyBankAccount
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portable_id", nullable = false, unique = true, updatable = false)
    private UUID portableId = UUID.randomUUID();

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id")
    private Bank bank;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "nickname", length = 160)
    private String nickname;

    @Column(name = "institution_name", length = 200)
    private String institutionName;

    @Column(name = "account_type", length = 80)
    private String accountType;

    @Column(name = "last_four", length = 8)
    private String lastFour;

    @Column(name = "masked_account_number", length = 80)
    private String maskedAccountNumber;

    @Column(name = "opening_date")
    private LocalDate openingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "statement_import_format", length = 20)
    private BankingDataFormat statementImportFormat;

    @Column(name = "ofx_bank_id", length = 80)
    private String ofxBankId;

    @Column(name = "ofx_account_id", length = 120)
    private String ofxAccountId;

    @Column(name = "opening_balance", precision = 19, scale = 4)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(length = 1000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = nowAtDatabasePrecision();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = nowAtDatabasePrecision();

    public Long getId() { return id; }
    public UUID getPortableId() { return portableId; }
    public Bank getBank() { return bank; }
    public void setBank(Bank bank) { this.bank = bank; }
    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getInstitutionName() { return institutionName; }
    public void setInstitutionName(String institutionName) { this.institutionName = institutionName; }
    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }
    public String getLastFour() { return lastFour; }
    public void setLastFour(String lastFour) { this.lastFour = lastFour; }
    public String getMaskedAccountNumber() { return maskedAccountNumber; }
    public void setMaskedAccountNumber(String maskedAccountNumber) { this.maskedAccountNumber = maskedAccountNumber; }
    public LocalDate getOpeningDate() { return openingDate; }
    public void setOpeningDate(LocalDate openingDate) { this.openingDate = openingDate; }
    public BankingDataFormat getStatementImportFormat() { return statementImportFormat; }
    public void setStatementImportFormat(BankingDataFormat statementImportFormat) { this.statementImportFormat = statementImportFormat; }
    public String getOfxBankId() { return ofxBankId; }
    public void setOfxBankId(String ofxBankId) { this.ofxBankId = ofxBankId; }
    public String getOfxAccountId() { return ofxAccountId; }
    public void setOfxAccountId(String ofxAccountId) { this.ofxAccountId = ofxAccountId; }
    public BigDecimal getOpeningBalance() { return openingBalance; }
    public void setOpeningBalance(BigDecimal openingBalance) { this.openingBalance = openingBalance; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void touchUpdatedAt() { this.updatedAt = nowAtDatabasePrecision(); }

    /** Initializes immutable source identity before a governed interchange import persists this account. */
    public void initializeImportMetadata(UUID portableId)
    {
        if (id != null)
        {
            throw new IllegalStateException("Bank-account import metadata must be initialized before persistence");
        }
        this.portableId = java.util.Objects.requireNonNull(portableId, "portableId");
    }

    private static Instant nowAtDatabasePrecision()
    {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
