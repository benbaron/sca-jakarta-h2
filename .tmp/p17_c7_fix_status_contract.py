from pathlib import Path

path = Path("src/main/java/org/nonprofitbookkeeping/service/FixedAssetService.java")
text = path.read_text()
old = '''        if (existing.getStatus() == FixedAsset.Status.DISPOSED)
        {
            throw new IllegalStateException(
                    "A disposed asset is immutable; reverse its lifecycle event before editing it");
        }
        FixedAsset.Status requested = command.status() == null
                ? existing.getStatus() : command.status();
        if (requested != existing.getStatus())
        {
            throw new IllegalArgumentException(
                    "Fixed asset status changes use the explicit lifecycle action");
        }
'''
new = '''        FixedAsset.Status requested = command.status() == null
                ? existing.getStatus() : command.status();
        if (requested == FixedAsset.Status.DISPOSED)
        {
            throw new IllegalArgumentException(
                    "DISPOSED is created only by the governed Sale or Retirement workflow");
        }
        if (existing.getStatus() == FixedAsset.Status.DISPOSED)
        {
            throw new IllegalStateException(
                    "A disposed asset is immutable; reverse its lifecycle event before editing it");
        }
        if (requested != existing.getStatus())
        {
            throw new IllegalArgumentException(
                    "Fixed asset status changes use the explicit lifecycle action");
        }
'''
count = text.count(old)
if count != 1:
    raise SystemExit(f"expected exactly one status guard block, found {count}")
path.write_text(text.replace(old, new))
