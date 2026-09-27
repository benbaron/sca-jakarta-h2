# P23-S2 — Budget reversal actuals

Budget vs Actual now retains reversed originals on their original dates and applies inverse entries on their own dates. Missing income/expense categories appear as **Unclassified (no budget category)**, separately by fund, rather than disappearing. No database migration is needed.

Use a disposable test company with an active fiscal-year budget, an expense category, a fund, and an event. Enter balanced Journal transactions with the expense/income line tagged to those same classifications. Refresh Budget vs Actual after each correction. Compare the same company, fund, fiscal start and cutoff in GL and Income Statement; budget actual uses expense minus income. Event Accounting can be compared where all tested income/expense lines belong to the selected event.

| Case | Entries | Expected actual |
|---|---|---|
| Same period | Expense 100 on Feb 10; reverse Feb 20 | February 0 |
| Later period | Separate expense 100 on Feb 10; reverse March 5 | February 100; March cumulative 0 |
| Replacement | Expense 100; reverse and replace with 80 | 80 after replacement |
| Refund | Refund 20 against that replacement | 60 |
| Income | Repeat using income 100, replacement 80, refund 20 | −100, −80, −60 respectively; full reversal 0 |
| Fiscal boundary | July fiscal start; expense 100 in June; reverse in July | Prior June 100; new fiscal year's July −100 |
| Missing category | Expense 25 without a budget category; reverse next month | Unclassified 25 before reversal; 0 afterward; classified category unchanged |

Check that another fund/category remains separate, and that an untagged bank line does not create an unclassified budget amount. Existing budget comparison covers all events; this slice does not introduce an event budget selector.

Automated coverage: `BudgetReversalActualsTest` uses real entry/correction commands and compares dated budget actuals with canonical reports. Owner desktop execution remains required; no visual acceptance is claimed by container tests.
