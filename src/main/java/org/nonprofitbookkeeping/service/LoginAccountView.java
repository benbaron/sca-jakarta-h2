package org.nonprofitbookkeeping.service;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Login selector projection for an account in the active company. */
public record LoginAccountView(
        long userId,
        String username,
        String displayName,
        boolean passwordConfigured,
        Set<ReservedSecurityRole> effectiveRoles)
{
    public LoginAccountView
    {
        effectiveRoles = effectiveRoles == null || effectiveRoles.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(effectiveRoles));
    }

    @Override
    public String toString()
    {
        return username + " — " + displayName;
    }
}
