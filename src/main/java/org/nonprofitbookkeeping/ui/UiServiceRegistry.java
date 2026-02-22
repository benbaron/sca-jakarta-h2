package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.AccountLookupService;
import org.nonprofitbookkeeping.service.FundBalanceService;
import org.nonprofitbookkeeping.service.FundLookupService;
import org.nonprofitbookkeeping.service.ScheduleEligibilityService;

/**
 * Lightweight service wiring for JavaFX runtime (without CDI bootstrap).
 */
public final class UiServiceRegistry
{
    private static final Jpa JPA = new Jpa();
    private static final AccountLookupService ACCOUNT_LOOKUP = new AccountLookupService(JPA);
    private static final FundLookupService FUND_LOOKUP = new FundLookupService(JPA);
    private static final FundBalanceService FUND_BALANCE = new FundBalanceService(JPA);
    private static final ScheduleEligibilityService SCHEDULES = new ScheduleEligibilityService(JPA);

    private UiServiceRegistry() {}

    public static AccountLookupService accountLookup() { return ACCOUNT_LOOKUP; }
    public static FundLookupService fundLookup() { return FUND_LOOKUP; }
    public static FundBalanceService fundBalance() { return FUND_BALANCE; }
    public static ScheduleEligibilityService schedules() { return SCHEDULES; }
}
