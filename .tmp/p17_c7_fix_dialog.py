from pathlib import Path
p = Path('src/main/java/org/nonprofitbookkeeping/ui/AssetsRegisterPanel.java')
s = p.read_text()
old = 'CompanyDialogUiCompliance.install(confirmation, AppPanelId.ASSETS_REGISTER);'
new = 'CompanyDialogUiCompliance.install(confirmation.getDialogPane(), AppPanelId.ASSETS_REGISTER);'
if old not in s:
    raise SystemExit('dialog compliance anchor not found')
p.write_text(s.replace(old, new, 1))
print('P17-C7 dialog compliance corrected')
