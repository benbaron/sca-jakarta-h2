package org.nonprofitbookkeeping.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BankingPortableIdentityTest
{
    @Test
    void newJpaBankingRecordsReceiveDistinctPortableIdentities()
    {
        assertDistinct(new Bank().getPortableId(), new Bank().getPortableId());
        assertDistinct(new CompanyBankAccount().getPortableId(), new CompanyBankAccount().getPortableId());
        assertDistinct(new BankImportBatch().getPortableId(), new BankImportBatch().getPortableId());
        assertDistinct(new BankStatementLine().getPortableId(), new BankStatementLine().getPortableId());
        assertDistinct(new ImportIssue().getPortableId(), new ImportIssue().getPortableId());
    }

    private static void assertDistinct(java.util.UUID first, java.util.UUID second)
    {
        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(first, second);
    }
}
