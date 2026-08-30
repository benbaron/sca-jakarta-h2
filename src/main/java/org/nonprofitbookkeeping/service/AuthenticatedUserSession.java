package org.nonprofitbookkeeping.service;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** In-memory authenticated identity and effective reserved roles for one company. */
public record AuthenticatedUserSession(
        long userId,
        String username,
        String displayName,
        String companyCode,
        Set<ReservedSecurityRole> effectiveRoles,
        Instant authenticatedAt,
        Instant lastActivityAt)
{
    public AuthenticatedUserSession
    {
        username = requireText(username, "username");
        displayName = requireText(displayName, "displayName");
        companyCode = requireText(companyCode, "companyCode");
        effectiveRoles = effectiveRoles == null || effectiveRoles.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(effectiveRoles));
        authenticatedAt = Objects.requireNonNull(authenticatedAt, "authenticatedAt");
        lastActivityAt = Objects.requireNonNull(lastActivityAt, "lastActivityAt");
    }

    public boolean hasRole(ReservedSecurityRole role)
    {
        return effectiveRoles.contains(Objects.requireNonNull(role, "role"));
    }

    public AuthenticatedUserSession withCompany(
            String nextCompanyCode,
            Set<ReservedSecurityRole> nextRoles,
            Instant activityAt)
    {
        return new AuthenticatedUserSession(
                userId,
                username,
                displayName,
                nextCompanyCode,
                nextRoles,
                authenticatedAt,
                activityAt);
    }

    public AuthenticatedUserSession withActivity(Instant activityAt)
    {
        return new AuthenticatedUserSession(
                userId,
                username,
                displayName,
                companyCode,
                effectiveRoles,
                authenticatedAt,
                Objects.requireNonNull(activityAt, "activityAt"));
    }

    private static String requireText(String value, String label)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.strip();
    }
}
