# Dashboard UI experiment

This standalone JavaFX module implements the proposed SCA-Jakarta main-screen mockup as an isolated experiment.

It intentionally uses fictional in-memory data. It does not initialize CDI, JPA, Flyway, or the production H2 database, and therefore cannot modify accounting data.

## Requirements

- JDK 17 or later
- Maven 3.9 or later

## Run

From the repository root:

```bash
cd experiments/dashboard-ui
mvn clean test
mvn javafx:run
```

## Eclipse

Import `experiments/dashboard-ui` as an existing Maven project, then run the Maven goal `javafx:run`.

The launcher class is:

```text
org.nonprofitbookkeeping.ui.experiment.DashboardExperimentApp
```

## Experiment checklist

1. Start at approximately 1440 by 900 pixels.
2. Resize down to the enforced 1024 by 700 minimum.
3. Drag both split-pane dividers.
4. Collapse and re-expand the left navigation pane.
5. Open several navigation destinations and close their workspace tabs.
6. Confirm the inspector never overlays the center workspace.
7. Observe the dashboard changing between four-column, two-column, and single-column layouts.
8. Evaluate the information density at 100%, 125%, and 150% display scaling.
9. Record navigation labels that appear redundant or unclear.

## Integration boundary

This module is not yet the production application shell. A later focused slice can move the approved shell into the main UI package, connect it to existing panels, and replace sample values with read-only service projections from the SCA-Jakarta database.
