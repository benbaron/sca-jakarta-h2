package org.nonprofitbookkeeping.service;

import java.time.LocalDate;

/** Creates one dated role-assignment history interval for the active company. */
public record UserRoleAssignmentCommand(
        Long userId,
        Long roleId,
        LocalDate startDate,
        String actor)
{
}
