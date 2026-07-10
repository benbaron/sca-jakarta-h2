from pathlib import Path

path = Path("src/main/java/org/nonprofitbookkeeping/ui/JournalWorkspaceCompliancePanel.java")
text = path.read_text()
old = ".findFirst()\n                    .ifPresent(restored::add);"
new = ".findFirst()\n                    .ifPresent(column -> restored.add((javafx.scene.control.TableColumn) column));"
if text.count(old) != 1:
    raise SystemExit(f"Expected one raw sort-order method reference, found {text.count(old)}")
path.write_text(text.replace(old, new, 1))
print("Adapter compile fix applied")
