package org.nonprofitbookkeeping.model;

import jakarta.persistence.*;

@Entity
@Table(name = "account_subtype_schedule_default",
       uniqueConstraints = @UniqueConstraint(name = "uq_subtype_sched", columnNames = {"subtype", "schedule_kind_id"}))
public class AccountSubtypeScheduleDefault
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String subtype;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_kind_id", nullable = false)
    private ScheduleKind scheduleKind;

    public Long getId() { return id; }

    public String getSubtype() { return subtype; }
    public void setSubtype(String subtype) { this.subtype = subtype; }

    public ScheduleKind getScheduleKind() { return scheduleKind; }
    public void setScheduleKind(ScheduleKind scheduleKind) { this.scheduleKind = scheduleKind; }
}
