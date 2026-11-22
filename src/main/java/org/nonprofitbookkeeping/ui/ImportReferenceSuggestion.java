package org.nonprofitbookkeeping.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Transient source hint and an independently resolved, ID-backed import choice. */
final class ImportReferenceSuggestion
{
    private final String sourceText;
    private final boolean required;
    private String hint;
    private TransactionLineEditorModel.Option value;
    private boolean ignored;
    private boolean automaticAllowed = true;

    ImportReferenceSuggestion(String sourceText, boolean required)
    {
        this.sourceText = sourceText == null ? "" : sourceText.strip();
        this.hint = this.sourceText;
        this.required = required;
    }

    TransactionLineEditorModel.Option value() { return value; }
    String sourceText() { return sourceText; }
    boolean required() { return required; }
    boolean ignored() { return ignored; }

    boolean suggested()
    {
        return value == null && !ignored && !hint.isBlank();
    }

    boolean unresolved()
    {
        return value == null && (required || suggested());
    }

    String displayText()
    {
        return value != null ? value.label() : ignored ? "" : hint;
    }

    void choose(TransactionLineEditorModel.Option choice)
    {
        if (choice != null && choice.id() == null)
        {
            throw new IllegalArgumentException("Select a saved reference record.");
        }
        value = choice;
        ignored = false;
        automaticAllowed = false;
    }

    void clear()
    {
        value = null;
        ignored = !required;
        automaticAllowed = false;
    }

    void refresh(List<TransactionLineEditorModel.Option> choices)
    {
        if (value != null)
        {
            var previous = value;
            value = choices.stream().filter(c -> Objects.equals(c.id(), previous.id())).findFirst().orElse(null);
            if (value == null)
            {
                hint = sourceText.isBlank() ? previous.label() : sourceText;
                automaticAllowed = false;
            }
        }
        if (value == null && !ignored && automaticAllowed && !sourceText.isBlank())
        {
            String source = normalized(sourceText);
            var matches = new LinkedHashMap<Long, TransactionLineEditorModel.Option>();
            for (var choice : choices)
            {
                if (choice.id() != null && (source.equals(normalized(choice.code()))
                        || source.equals(normalized(choice.name())) || source.equals(normalized(choice.label()))))
                {
                    matches.put(choice.id(), choice);
                }
            }
            if (matches.size() == 1)
            {
                value = matches.values().iterator().next();
            }
        }
    }

    boolean copyResolvedChoice(ImportReferenceSuggestion other)
    {
        var before = value;
        boolean wasIgnored = ignored;
        if (other.value != null)
        {
            choose(other.value);
        }
        else if (other.ignored)
        {
            clear();
        }
        return !Objects.equals(before, value) || wasIgnored != ignored;
    }

    ImportReferenceSuggestion copy()
    {
        var result = new ImportReferenceSuggestion(sourceText, required);
        result.restore(this);
        return result;
    }

    void restore(ImportReferenceSuggestion other)
    {
        value = other.value;
        hint = other.hint;
        ignored = other.ignored;
        automaticAllowed = other.automaticAllowed;
    }

    String validation(String field)
    {
        if (!unresolved())
        {
            return "";
        }
        return field + (required ? " requires a saved record" : " requires a selection or Clear")
                + (hint.isBlank() ? "." : ": " + hint + ".");
    }

    private static String normalized(String value)
    {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    }
}
