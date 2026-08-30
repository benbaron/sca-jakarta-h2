package org.nonprofitbookkeeping.service;

/** Fixed runtime permissions derived only from authenticated reserved roles. */
public enum ApplicationPermission
{
    BOOKKEEPING_WRITE,
    COMPANY_ADMIN,
    SECURITY_ADMIN,
    DATABASE_ADMIN,
    EXPORT,
    UI_PREFERENCE_WRITE
}
