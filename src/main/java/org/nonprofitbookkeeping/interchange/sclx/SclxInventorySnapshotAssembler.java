package org.nonprofitbookkeeping.interchange.sclx;

import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.InventoryItem;
import org.nonprofitbookkeeping.model.InventoryMovement;
import org.nonprofitbookkeeping.model.Txn;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Maps the selected-company inventory graph into the governed extension value. */
final class SclxInventorySnapshotAssembler
{
    Map<String, Object> assemble(String companyCode, Company company, ChartOfAccounts activeChart,
            SclxInventorySnapshot snapshot, Set<Txn> includedTransactions, Map<Txn, String> transactionIds)
    {
        Set<InventoryItem> includedItems = identitySet(snapshot.items());
        List<Map<String, Object>> items = snapshot.items().stream()
                .peek(item -> requireItemOwnership(item, company, activeChart))
                .sorted(Comparator.comparing(item -> item.getPortableId().toString()))
                .map(item -> SclxInventoryExtension.itemEntry(
                        SclxPortableIdentity.inventoryItem(companyCode, item.getPortableId().toString()),
                        item.getName(), item.getItemType(), item.getQuantity(), item.getUnit(), item.getUnitValue(),
                        item.getAcquisitionDate(), item.getCustodian(), item.getStorageLocation(),
                        Objects.requireNonNull(item.getCondition(), "inventory condition").name(),
                        Objects.requireNonNull(item.getStatus(), "inventory status").name(), item.getNotes(),
                        accountId(companyCode, item.getInventoryAccount()), fundId(companyCode, item.getFund()),
                        item.getCreatedAt(), item.getUpdatedAt()))
                .toList();
        List<Map<String, Object>> movements = snapshot.movements().stream()
                .peek(movement -> requireMovementOwnership(
                        movement, company, includedItems, includedTransactions, transactionIds))
                .sorted(Comparator.comparing(movement -> movement.getPortableId().toString()))
                .map(movement -> SclxInventoryExtension.movementEntry(
                        SclxPortableIdentity.inventoryMovement(companyCode, movement.getPortableId().toString()),
                        SclxPortableIdentity.inventoryItem(
                                companyCode, movement.getInventoryItem().getPortableId().toString()),
                        movement.getMovementDate(),
                        Objects.requireNonNull(movement.getMovementType(), "inventory movement type").name(),
                        movement.getQuantityChange(), movement.getResultingQuantity(), movement.getUnitValue(),
                        movement.getTransaction() == null ? null : transactionIds.get(movement.getTransaction()),
                        movement.getNotes(), movement.getCreatedAt()))
                .toList();
        return SclxInventoryExtension.value(items, movements);
    }

    private static void requireItemOwnership(InventoryItem item, Company company, ChartOfAccounts activeChart)
    {
        Objects.requireNonNull(item, "inventory item");
        Objects.requireNonNull(item.getPortableId(), "inventory item portableId");
        if (item.getCompany() != company)
        {
            throw new IllegalArgumentException("inventory item is outside the selected company");
        }
        Account account = Objects.requireNonNull(item.getInventoryAccount(), "inventory account");
        if (account.getChart() != activeChart)
        {
            throw new IllegalArgumentException("inventory account is outside the selected company's active chart");
        }
        Fund fund = Objects.requireNonNull(item.getFund(), "inventory fund");
        if (fund.getCompany() != company)
        {
            throw new IllegalArgumentException("inventory fund is outside the selected company");
        }
    }

    private static void requireMovementOwnership(InventoryMovement movement, Company company,
            Set<InventoryItem> items, Set<Txn> transactions, Map<Txn, String> transactionIds)
    {
        Objects.requireNonNull(movement, "inventory movement");
        Objects.requireNonNull(movement.getPortableId(), "inventory movement portableId");
        InventoryItem item = Objects.requireNonNull(movement.getInventoryItem(), "inventory movement item");
        if (!items.contains(item) || item.getCompany() != company)
        {
            throw new IllegalArgumentException("inventory movement references an item outside the exported snapshot");
        }
        Txn transaction = movement.getTransaction();
        if (transaction != null && (!transactions.contains(transaction) || transaction.getCompany() != company
                || !transactionIds.containsKey(transaction)))
        {
            throw new IllegalArgumentException(
                    "inventory movement references a transaction outside the exported snapshot");
        }
    }

    private static String accountId(String companyCode, Account account)
    {
        return SclxPortableIdentity.account(companyCode, Objects.requireNonNull(account, "account").getCode());
    }

    private static String fundId(String companyCode, Fund fund)
    {
        return SclxPortableIdentity.fund(companyCode, Objects.requireNonNull(fund, "fund").getCode());
    }

    private static <T> Set<T> identitySet(List<T> values)
    {
        Set<T> result = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        result.addAll(values);
        return result;
    }
}
