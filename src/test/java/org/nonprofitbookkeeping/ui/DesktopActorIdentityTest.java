package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DesktopActorIdentityTest
{
    @Test
    void usesTrimmedOperatingSystemUserWhenAvailable()
    {
        assertEquals("alex", DesktopActorIdentity.fromUserName("  alex  "));
    }

    @Test
    void usesNonSecurityFallbackWhenOperatingSystemUserIsUnavailable()
    {
        assertEquals("local-desktop", DesktopActorIdentity.fromUserName("  "));
        assertEquals("local-desktop", DesktopActorIdentity.fromUserName(null));
    }
}
