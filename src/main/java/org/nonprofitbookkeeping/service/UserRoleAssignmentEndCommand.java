package org.nonprofitbookkeeping.service;

import java.time.LocalDate;

/** Ends or revokes one active-company assignment without deleting its history. */
public record UserRoleAssignmentEndCommand(
        Long assignmentId,
        LocalDate endDate,
        boolean revoked,
        String reason,
        String actor)
{
}
