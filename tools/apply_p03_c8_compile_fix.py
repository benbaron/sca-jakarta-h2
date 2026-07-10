from pathlib import Path

path = Path("src/main/java/org/nonprofitbookkeeping/ui/JournalWorkspaceCompliancePanel.java")
text = path.read_text()
old = ".findFirst()\n                    .ifPresent(restored::add);"
new = ".findFirst()\n                    .ifPresent(column -> restored.add((javafx.scene.control.TableColumn) column));"
if old in text:
    path.write_text(text.replace(old, new, 1))
elif new not in text:
    raise SystemExit("Expected Journal sort-order source pattern was not found")
