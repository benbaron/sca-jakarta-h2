# Application composition

P01-S1 establishes `ProductionWorkspaceWindow` as the production shell owner. The launched JavaFX application constructs one workspace shell containing menu, toolbar, navigation, reusable center tabs, inspector, dividers, and status bar.

`ReferenceWorkspaceWindow` remains a package-local compatibility subclass only for existing reference-chrome tests while the P01-S2 composition slice moves construction into explicit workspace factories. New production routes must use `ProductionWorkspaceWindow` directly and must not add another window shell.

Global shell actions use typed `AppCommand` values. The shell and `PanelHost` route commands by enum identity instead of discovering behavior from button text. Panel-local operations continue through the `AppPanel` contract until P01-S2 introduces the lifecycle-owned panel factory.

User-facing global navigation uses factual audit-history terminology. Approval/rejection workflows are not introduced by the production shell.
