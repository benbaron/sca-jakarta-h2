package org.nonprofitbookkeeping.workflow.state;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.domain.state.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenItemStatePoliciesTest component.
 */
public class OpenItemStatePoliciesTest
{
    @Test
    public void eventPolicy_allowsLinearProgression_only()
    {
        StateTransitionPolicy<EventState> policy = OpenItemStatePolicies.eventPolicy();

        assertTrue(policy.canTransition(EventState.PLANNING, EventState.OPEN_FOR_PREREG));
        assertTrue(policy.canTransition(EventState.OPEN_FOR_PREREG, EventState.ACTIVE));
        assertTrue(policy.canTransition(EventState.ACTIVE, EventState.SETTLING));
        assertTrue(policy.canTransition(EventState.SETTLING, EventState.CLOSED));

        assertFalse(policy.canTransition(EventState.ACTIVE, EventState.CLOSED));
        assertFalse(policy.canTransition(EventState.CLOSED, EventState.ACTIVE));
    }

    @Test
    public void outstandingBankItem_policy_supportsCarryForward()
    {
        StateTransitionPolicy<OutstandingBankItemState> policy = OpenItemStatePolicies.outstandingBankItemPolicy();

        assertTrue(policy.canTransition(OutstandingBankItemState.UNCLEARED, OutstandingBankItemState.CARRIED_FORWARD));
        assertTrue(policy.canTransition(OutstandingBankItemState.CARRIED_FORWARD, OutstandingBankItemState.CARRIED_FORWARD));
        assertTrue(policy.canTransition(OutstandingBankItemState.CARRIED_FORWARD, OutstandingBankItemState.CLEARED));
        assertFalse(policy.canTransition(OutstandingBankItemState.CLEARED, OutstandingBankItemState.UNCLEARED));
    }

    @Test
    public void illegalTransition_throws()
    {
        StateTransitionPolicy<PayableItemState> policy = OpenItemStatePolicies.payablePolicy();

        assertThrows(IllegalStateException.class,
                () -> policy.assertTransitionAllowed(PayableItemState.PAID, PayableItemState.PARTIALLY_PAID));
    }
}
