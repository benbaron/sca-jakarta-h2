package org.nonprofitbookkeeping.workflow.state;

import org.nonprofitbookkeeping.domain.state.*;

import java.util.Map;
import java.util.Set;

/**
 * Explicit lifecycle transition rules for Phase 2 open-item and event state machines.
 */
public final class OpenItemStatePolicies
{
    private OpenItemStatePolicies()
    {
    }

    public static StateTransitionPolicy<OutstandingBankItemState> outstandingBankItemPolicy()
    {
        Map<OutstandingBankItemState, Set<OutstandingBankItemState>> transitions = Map.of(
                OutstandingBankItemState.RECORDED, Set.of(OutstandingBankItemState.UNCLEARED, OutstandingBankItemState.VOIDED),
                OutstandingBankItemState.UNCLEARED, Set.of(OutstandingBankItemState.CARRIED_FORWARD, OutstandingBankItemState.CLEARED, OutstandingBankItemState.STALE_WRITTEN_OFF, OutstandingBankItemState.VOIDED),
                OutstandingBankItemState.CARRIED_FORWARD, Set.of(OutstandingBankItemState.CARRIED_FORWARD, OutstandingBankItemState.CLEARED, OutstandingBankItemState.STALE_WRITTEN_OFF, OutstandingBankItemState.VOIDED),
                OutstandingBankItemState.CLEARED, Set.of(),
                OutstandingBankItemState.VOIDED, Set.of(),
                OutstandingBankItemState.STALE_WRITTEN_OFF, Set.of()
        );
        return () -> transitions;
    }

    public static StateTransitionPolicy<ReceivableItemState> receivablePolicy()
    {
        Map<ReceivableItemState, Set<ReceivableItemState>> transitions = Map.of(
                ReceivableItemState.OPEN, Set.of(ReceivableItemState.PARTIALLY_APPLIED, ReceivableItemState.SETTLED_BY_CASH, ReceivableItemState.SETTLED_BY_EXPENSE_APPLICATION, ReceivableItemState.WRITTEN_OFF),
                ReceivableItemState.PARTIALLY_APPLIED, Set.of(ReceivableItemState.PARTIALLY_APPLIED, ReceivableItemState.SETTLED_BY_CASH, ReceivableItemState.SETTLED_BY_EXPENSE_APPLICATION, ReceivableItemState.WRITTEN_OFF),
                ReceivableItemState.SETTLED_BY_CASH, Set.of(),
                ReceivableItemState.SETTLED_BY_EXPENSE_APPLICATION, Set.of(),
                ReceivableItemState.WRITTEN_OFF, Set.of()
        );
        return () -> transitions;
    }

    public static StateTransitionPolicy<PrepaidExpenseItemState> prepaidExpensePolicy()
    {
        Map<PrepaidExpenseItemState, Set<PrepaidExpenseItemState>> transitions = Map.of(
                PrepaidExpenseItemState.OPEN, Set.of(PrepaidExpenseItemState.PARTIALLY_RECOGNIZED, PrepaidExpenseItemState.FULLY_RECOGNIZED),
                PrepaidExpenseItemState.PARTIALLY_RECOGNIZED, Set.of(PrepaidExpenseItemState.PARTIALLY_RECOGNIZED, PrepaidExpenseItemState.FULLY_RECOGNIZED),
                PrepaidExpenseItemState.FULLY_RECOGNIZED, Set.of()
        );
        return () -> transitions;
    }

    public static StateTransitionPolicy<DeferredRevenueItemState> deferredRevenuePolicy()
    {
        Map<DeferredRevenueItemState, Set<DeferredRevenueItemState>> transitions = Map.of(
                DeferredRevenueItemState.OPEN, Set.of(DeferredRevenueItemState.PARTIALLY_RECOGNIZED, DeferredRevenueItemState.FULLY_RECOGNIZED, DeferredRevenueItemState.REFUNDED),
                DeferredRevenueItemState.PARTIALLY_RECOGNIZED, Set.of(DeferredRevenueItemState.PARTIALLY_RECOGNIZED, DeferredRevenueItemState.FULLY_RECOGNIZED, DeferredRevenueItemState.REFUNDED),
                DeferredRevenueItemState.FULLY_RECOGNIZED, Set.of(),
                DeferredRevenueItemState.REFUNDED, Set.of()
        );
        return () -> transitions;
    }

    public static StateTransitionPolicy<PayableItemState> payablePolicy()
    {
        Map<PayableItemState, Set<PayableItemState>> transitions = Map.of(
                PayableItemState.OPEN, Set.of(PayableItemState.PARTIALLY_PAID, PayableItemState.PAID, PayableItemState.ADJUSTED, PayableItemState.REVERSED),
                PayableItemState.PARTIALLY_PAID, Set.of(PayableItemState.PARTIALLY_PAID, PayableItemState.PAID, PayableItemState.ADJUSTED, PayableItemState.REVERSED),
                PayableItemState.PAID, Set.of(),
                PayableItemState.ADJUSTED, Set.of(PayableItemState.PAID, PayableItemState.REVERSED),
                PayableItemState.REVERSED, Set.of()
        );
        return () -> transitions;
    }

    public static StateTransitionPolicy<AssetItemState> assetPolicy()
    {
        Map<AssetItemState, Set<AssetItemState>> transitions = Map.of(
                AssetItemState.ACTIVE, Set.of(AssetItemState.ON_LOAN, AssetItemState.LOST, AssetItemState.SOLD, AssetItemState.DISPOSED),
                AssetItemState.ON_LOAN, Set.of(AssetItemState.ACTIVE, AssetItemState.LOST, AssetItemState.SOLD, AssetItemState.DISPOSED),
                AssetItemState.LOST, Set.of(),
                AssetItemState.SOLD, Set.of(),
                AssetItemState.DISPOSED, Set.of()
        );
        return () -> transitions;
    }

    public static StateTransitionPolicy<EventState> eventPolicy()
    {
        Map<EventState, Set<EventState>> transitions = Map.of(
                EventState.PLANNING, Set.of(EventState.OPEN_FOR_PREREG),
                EventState.OPEN_FOR_PREREG, Set.of(EventState.ACTIVE),
                EventState.ACTIVE, Set.of(EventState.SETTLING),
                EventState.SETTLING, Set.of(EventState.CLOSED),
                EventState.CLOSED, Set.of()
        );
        return () -> transitions;
    }
}
