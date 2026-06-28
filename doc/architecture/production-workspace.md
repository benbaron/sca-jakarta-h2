# Production workspace and dashboard

This document records the approved production direction for the SCA bookkeeping desktop application.

## Production shell

The dashboard experiment becomes the production main window. The shell provides a menu bar, toolbar, collapsible navigation, tabbed center workspace, collapsible inspector, draggable dividers, status bar, external CSS, and responsive scrolling.

The application opens on the dashboard. The dashboard tab remains available throughout the session.

## Workspace behavior

The center workspace uses one reusable tab per panel type. Opening an existing destination activates its tab. Dirty work is checked before a tab or database context is closed.

The inspector follows the selection in the active tab and may show details, contextual actions, editable notes, related records, and audit history.
