package org.nonprofitbookkeeping.model;

import jakarta.persistence.*;
import java.time.*;


@Entity
@Table(name = "account_alias",
       indexes = {
           @Index(name = "ix_account_alias_account", columnList = "account_id"),
           @Index(name = "ix_account_alias_text", columnList = "alias_text")
       })
/**
 * Represents the AccountAlias component in the nonprofit bookkeeping application.
 */
public class AccountAlias
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "alias_text", nullable = false, length = 400)
    private String aliasText;

    @Column(length = 80)
    private String source;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public Long getId() { return id; }
    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }
    public String getAliasText() { return aliasText; }
    public void setAliasText(String aliasText) { this.aliasText = aliasText; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
