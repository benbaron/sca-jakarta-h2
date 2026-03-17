package org.nonprofitbookkeeping.workflow.state;

import java.util.Map;
import java.util.Set;

/**
 * Generic transition policy abstraction for domain lifecycle states.
 *
 * @param <S> state enum type
 */
public interface StateTransitionPolicy<S extends Enum<S>>
{
    Map<S, Set<S>> allowedTransitions();

    default boolean canTransition(S from, S to)
    {
        Set<S> destinations = allowedTransitions().get(from);
        return destinations != null && destinations.contains(to);
    }

    default void assertTransitionAllowed(S from, S to)
    {
        if (!canTransition(from, to))
        {
            throw new IllegalStateException("Transition not allowed: " + from + " -> " + to);
        }
    }
}
