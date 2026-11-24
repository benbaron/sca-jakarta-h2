package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ImportReferenceSuggestionTest
{
    @Test
    void addReceivesOnlyTheLiteralUnresolvedNameAndNeverAnExistingOrClearedIdentity()
    {
        String literal = "January's Coronation / MiXeD & café #2";
        var hint = new ImportReferenceSuggestion(literal, false);
        assertEquals(literal, hint.nameForNewRecord());
        hint.choose(option(7, "CODE", "Saved choice"));
        assertEquals("", hint.nameForNewRecord(), "A saved choice is not a missing record to duplicate");
        hint.clear();
        assertEquals("", hint.nameForNewRecord(), "An explicitly cleared suggestion is not restored by Add");
        assertEquals("", new ImportReferenceSuggestion("", true).nameForNewRecord());
        var stale = new ImportReferenceSuggestion("", false);
        stale.choose(option(12, "OLD", "Retired name"));
        stale.refresh(List.of());
        assertEquals(stale.displayText(), stale.nameForNewRecord(), "Copy exactly the unresolved text the user sees");
    }

    @Test
    void uniqueExactCodeOrNameResolvesButSimilarTextAndAmbiguityStayUnmapped()
    {
        var checking = option(1, "1000", "Operating Checking");
        var state = new ImportReferenceSuggestion(" operating checking ", true);
        state.refresh(List.of(checking));
        assertEquals(checking, state.value());
        assertFalse(state.suggested());
        assertFalse(state.unresolved());
        var missing = new ImportReferenceSuggestion("Checking", true);
        missing.refresh(List.of(checking));
        assertNull(missing.value());
        assertTrue(missing.suggested());
        assertEquals("Checking", missing.displayText());
        var ambiguous = new ImportReferenceSuggestion("1000", true);
        ambiguous.refresh(List.of(checking, option(2, "OTHER", "1000")));
        assertNull(ambiguous.value(), "Code and name collisions across different IDs are ambiguous");
        assertTrue(ambiguous.unresolved());
    }

    @Test
    void choosingARecordReplacesHintAndPreservesTheOriginalSource()
    {
        var state = new ImportReferenceSuggestion("CSV expense category", true);
        var selected = option(7, "5000", "Venue rental");
        state.choose(selected);
        assertEquals(selected, state.value());
        assertEquals(selected.label(), state.displayText());
        assertEquals("CSV expense category", state.sourceText());
        assertFalse(state.suggested());
        assertThrows(IllegalArgumentException.class, () -> state.choose(option(null, "", "Invented record")));
        state.clear();
        assertTrue(state.unresolved(), "A required reference cannot be omitted");
        assertEquals("CSV expense category", state.displayText());
    }

    @Test
    void optionalSourceNeedsAnExplicitChoiceOrClearAndClearingSurvivesRefresh()
    {
        var state = new ImportReferenceSuggestion("Example payee", false);
        assertTrue(state.unresolved());
        assertTrue(state.validation("Payee").contains("selection or Clear"));
        state.clear();
        assertTrue(state.ignored());
        assertFalse(state.unresolved());
        state.refresh(List.of(option(1, "", "Example payee")));
        assertNull(state.value(), "A deliberately omitted source is not restored automatically");
        assertFalse(state.suggested());
        assertEquals("Example payee", state.sourceText());
        assertFalse(new ImportReferenceSuggestion("", false).unresolved());
    }

    @Test
    void refreshKeepsChosenIdentityWithNewLabelAndRejectsSameNameReplacementIdentity()
    {
        var state = new ImportReferenceSuggestion("Source bank", true);
        state.choose(option(1, "1000", "Old name"));
        var renamed = option(1, "1000", "New name");
        state.refresh(List.of(renamed));
        assertEquals(renamed, state.value());
        state.refresh(List.of(option(2, "1000", "Source bank")));
        assertNull(state.value(), "A removed or wrong-chart choice cannot silently move to another ID");
        assertTrue(state.suggested());
        assertTrue(state.unresolved());
    }

    @Test
    void untouchedMissingSuggestionCanResolveAfterReferenceCreation()
    {
        var state = new ImportReferenceSuggestion("General Fund", true);
        state.refresh(List.of());
        assertTrue(state.unresolved());
        var fund = option(12, "GENERAL", "General Fund");
        state.refresh(List.of(fund));
        assertEquals(fund, state.value());
    }

    @Test
    void staleManualOptionalChoiceWithoutSourceStillRequiresResolution()
    {
        var state = new ImportReferenceSuggestion("", false);
        state.choose(option(4, "", "Selected merchant"));
        state.refresh(List.of());
        assertTrue(state.unresolved());
        assertTrue(state.displayText().contains("Selected merchant"));
        state.clear();
        assertFalse(state.unresolved());
    }

    @Test
    void explicitChoiceOrClearingCanBeCopiedButAnUnresolvedHintCannotOverwriteAChoice()
    {
        var first = new ImportReferenceSuggestion("Merchant label", false);
        first.clear();
        var target = new ImportReferenceSuggestion("Merchant label", false);
        target.copyResolvedChoice(first);
        assertTrue(target.ignored());
        first.choose(option(10, "", "Saved merchant"));
        target.copyResolvedChoice(first);
        assertEquals(first.value(), target.value());
        target.copyResolvedChoice(new ImportReferenceSuggestion("Unknown", false));
        assertEquals(10, target.value().id());
    }

    private static TransactionLineEditorModel.Option option(Integer id, String code, String name)
    {
        return new TransactionLineEditorModel.Option(id == null ? null : id.longValue(), code, name);
    }

    @Test
    void snapshotsRestoreTheLastAppliedSelectionAndExplicitClearingWithoutSharingMutableState()
    {
        var value = new ImportReferenceSuggestion("Original CSV", false);
        value.clear();
        var cleared = value.copy();
        value.choose(option(1, "", "New choice"));
        assertTrue(cleared.ignored());
        value.restore(cleared);
        assertTrue(value.ignored());
        assertNull(value.value());
        assertEquals("Original CSV", value.sourceText());
    }
}
