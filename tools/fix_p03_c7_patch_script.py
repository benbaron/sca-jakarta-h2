from pathlib import Path
import re

path = Path("tools/apply_p03_c7.py")
text = path.read_text()
pattern = r'''    money_pattern = r'''.*?    text = regex_once\(text, money_pattern, money_replacement, "journal money helpers"\)\n'''
replacement = '''    old_money_helpers = \'''    private static BigDecimal parseMoney(String value)\n    {\n        if (value == null || value.isBlank())\n        {\n            return BigDecimal.ZERO;\n        }\n        try\n        {\n            return new BigDecimal(value.trim().replace("$", "").replace(",", ""));\n        }\n        catch (NumberFormatException ex)\n        {\n            return null;\n        }\n    }\n\n    private static String normalizeMoney(String value)\n    {\n        BigDecimal amount = parseMoney(value);\n        return amount == null\n                ? safe(value).trim()\n                : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();\n    }\n\n    private static String money(BigDecimal value)\n    {\n        BigDecimal amount = value == null ? BigDecimal.ZERO : value;\n        return "$" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();\n    }\n\'''\n    new_money_helpers = \'''    private static BigDecimal parseMoney(String value)\n    {\n        return CompanyUiFormat.parseMoneyLenient(value);\n    }\n\n    private String normalizeMoney(String value)\n    {\n        return companyFormat.normalizeMoney(value);\n    }\n\n    private String money(BigDecimal value)\n    {\n        return companyFormat.formatMoney(value);\n    }\n\'''\n    text = replace_once(text, old_money_helpers, new_money_helpers, "journal money helpers")\n'''
updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
if count != 1:
    raise SystemExit(f"Expected one money patch block, found {count}")
path.write_text(updated)
print("P03-C7 patch script money helper replacement fixed")
