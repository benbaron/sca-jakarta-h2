package org.nonprofitbookkeeping.model;

/**
 * Guided wizard progression state.
 */
public record WizardState(String wizardId, int stepIndex, boolean completed)
{
}
