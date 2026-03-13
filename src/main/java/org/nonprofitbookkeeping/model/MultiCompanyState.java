package org.nonprofitbookkeeping.model;

import java.util.List;

/**
 * Persistable multi-company context for user sessions.
 */
public record MultiCompanyState(String activeCompanyCode, List<String> recentCompanyCodes)
{
}
