# Dashboard UI experiment

This standalone JavaFX module implements the proposed SCA-Jakarta main-screen mockup as an isolated experiment.

It intentionally uses fictional in-memory data. It does not initialize CDI, JPA, Flyway, or the production H2 database, and therefore cannot modify accounting data.

## Requirements

- JDK 17 or later
- Maven 3.9 or later

## Run

Run the experiment from the repository root so the repository-level `.mvn/maven.config` can resolve `.mvn/settings.xml` correctly:

```bash
mvn -f experiments/dashboard-ui/pom.xml clean test
mvn -f experiments/dashboard-ui/pom.xml javafx:run
```

Do not change into `experiments/dashboard-ui` before running Maven. The repository's `.mvn/maven.config` contains a relative `--settings .mvn/settings.xml` entry, and Maven resolves that relative path from the current working directory.

To run the repository's complete verification first:

```bash
mvn clean verify
```

## Eclipse

Import `experiments/dashboard-ui` as an existing Maven project. For a Maven launch configuration, set the base directory to the repository root and use:

```text
-f experiments/dashboard-ui/pom.xml javafx:run
```

The launcher class is:

```text
org.nonprofitbookkeeping.ui.experiment.DashboardExperimentLauncher
```

The launcher constrains the initial window to 90% of the usable display area, caps it at 1440 by 900, and lowers the minimum size on smaller or scaled Windows desktops so the resize borders remain reachable.

## Experiment checklist

1. Start at approximately 1440 by 900 pixels on a large display, or 90% of the usable display on a smaller one.
2. Resize the window down to its screen-aware minimum.
3. Drag both split-pane dividers.
4. Collapse and re-expand the left navigation pane.
5. Open several navigation destinations and close their workspace tabs.
6. Confirm the inspector never overlays the center workspace.
7. Observe the dashboard changing between four-column, two-column, and single-column layouts.
8. Evaluate the information density at 100%, 125%, and 150% display scaling.
9. Record navigation labels that appear redundant or unclear.

## Integration boundary

This module is not yet the production application shell. A later focused slice can move the approved shell into the main UI package, connect it to existing panels, and replace sample values with read-only service projections from the SCA-Jakarta database.
