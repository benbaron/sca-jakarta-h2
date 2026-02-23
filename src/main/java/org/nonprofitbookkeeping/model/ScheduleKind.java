package org.nonprofitbookkeeping.model;

import jakarta.persistence.*;
import java.time.*;


@Entity
@Table(name = "schedule_kind",
       uniqueConstraints = @UniqueConstraint(name = "uq_schedule_kind_code", columnNames = {"code"}))
/**
 * Represents the ScheduleKind component in the nonprofit bookkeeping application.
 */
public class ScheduleKind
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
