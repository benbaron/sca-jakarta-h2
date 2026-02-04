package org.nonprofitbookkeeping.model;

import jakarta.persistence.*;
import java.time.*;
import java.math.*;


@Entity
@Table(name = "fund",
       uniqueConstraints = @UniqueConstraint(name = "uq_fund_code", columnNames = {"code"}),
       indexes = {
           @Index(name = "ix_fund_parent", columnList = "parent_id"),
           @Index(name = "ix_fund_active", columnList = "is_active")
       })
public class Fund
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "fund_type", nullable = false, length = 30)
    private FundType fundType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Fund parent;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Lob
    @Column(name = "restriction_text")
    private String restrictionText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public FundType getFundType() { return fundType; }
    public void setFundType(FundType fundType) { this.fundType = fundType; }

    public Fund getParent() { return parent; }
    public void setParent(Fund parent) { this.parent = parent; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }

    public String getRestrictionText() { return restrictionText; }
    public void setRestrictionText(String restrictionText) { this.restrictionText = restrictionText; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void touchUpdatedAt() { this.updatedAt = Instant.now(); }
}
