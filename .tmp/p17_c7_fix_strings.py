from pathlib import Path
p = Path('src/main/java/org/nonprofitbookkeeping/ui/AssetsRegisterPanel.java')
s = p.read_text()
old1 = '''? "Deactivation retains the asset and all accounting history but stops depreciation and Sale/Retirement/Impairment actions until the asset is reactivated.

Reason: " + reason'''
new1 = '''? "Deactivation retains the asset and all accounting history but stops depreciation and Sale/Retirement/Impairment actions until the asset is reactivated.\\n\\nReason: " + reason'''
old2 = ''': "Reactivation resumes depreciation and governed financial lifecycle eligibility for this retained asset.

Reason: " + reason);'''
new2 = ''': "Reactivation resumes depreciation and governed financial lifecycle eligibility for this retained asset.\\n\\nReason: " + reason);'''
if old1 not in s or old2 not in s:
    raise SystemExit('confirmation string anchors not found')
s = s.replace(old1, new1, 1).replace(old2, new2, 1)
p.write_text(s)
print('P17-C7 confirmation strings corrected')
