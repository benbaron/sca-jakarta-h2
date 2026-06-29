package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ActivePeriodContextTest
{
    private final LocalDate originalDate = ActivePeriodContext.get();

    @AfterEach
    public void restoreOriginalDate()
    {
        ActivePeriodContext.set(originalDate);
    }

    @Test
    public void setUpdatesSharedDateAndNotifiesListeners()
    {
        LocalDate expected = LocalDate.of(2026, 9, 30);
        AtomicReference<LocalDate> observed = new AtomicReference<>();
        ActivePeriodContext.activeDateProperty().addListener(
                (observable, oldDate, newDate) -> observed.set(newDate));

        ActivePeriodContext.set(expected);

        assertEquals(expected, ActivePeriodContext.get());
        assertEquals(expected, observed.get());
    }

    @Test
    public void setRejectsNullDateWithoutChangingCurrentValue()
    {
        LocalDate before = ActivePeriodContext.get();

        assertThrows(NullPointerException.class, () -> ActivePeriodContext.set(null));
        assertEquals(before, ActivePeriodContext.get());
    }
}
