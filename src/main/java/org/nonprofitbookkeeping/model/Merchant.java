package org.nonprofitbookkeeping.model;

import jakarta.persistence.*;
import java.time.*;
import java.math.*;
import java.util.UUID;


@Entity
@Table(name = "merchant",
       uniqueConstraints = @UniqueConstraint(name = "uq_merchant_company_name", columnNames = {"company_id", "name"}),
       indexes = @Index(name = "ix_merchant_company_name", columnList = "company_id, name"))
/**
 * Represents the Merchant component in the nonprofit bookkeeping application.
 */
public class Merchant
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portable_id", nullable = false, unique = true)
    private UUID portableId = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(nullable = false, length = 200)
    private String name;

    @Lob
    private String notes;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public Long getId() { return id; }
    public UUID getPortableId() { return portableId; }
    public void setPortableId(UUID portableId) { this.portableId = portableId; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
