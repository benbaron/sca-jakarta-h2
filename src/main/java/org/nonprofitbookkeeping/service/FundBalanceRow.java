package org.nonprofitbookkeeping.service;

import java.math.BigDecimal;

/**
 * A simple “as of” fund balance result row.
 * The meaning of this balance depends on which accounts you include in the query.
 */
public class FundBalanceRow
{
    private final String fundCode;
    private final String fundName;
    private final BigDecimal balance;

    public FundBalanceRow(String fundCode, String fundName, BigDecimal balance)
    {
        this.fundCode = fundCode;
        this.fundName = fundName;
        this.balance = balance;
    }

    public String getFundCode() { return fundCode; }
    public String getFundName() { return fundName; }
    public BigDecimal getBalance() { return balance; }
}
