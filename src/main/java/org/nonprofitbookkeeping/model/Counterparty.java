package org.nonprofitbookkeeping.model;

import jakarta.persistence.*;
import java.time.*;
import java.math.*;


@Entity
@Table(name = "counterparty",
       indexes = @Index(name = "ix_counterparty_name", columnList = "display_name"))
/**
 * Represents the Counterparty component in the nonprofit bookkeeping application.
 */
public class Counterparty
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CounterpartyKind kind = CounterpartyKind.OTHER;

    @Column(length = 200)
    private String email;

    @Column(length = 40)
    private String phone;

    @Lob
    private String notes;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public Long getId() { return id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public CounterpartyKind getKind() { return kind; }
    public void setKind(CounterpartyKind kind) { this.kind = kind; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
