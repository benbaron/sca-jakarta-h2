# P15-S6-C1 strict OFX/QFX preview desktop checklist

Status: required owner acceptance before P15-S6-C1 merge.

1. Start the packaged JavaFX application with a disposable database and open **Import Preview**.
2. Preview `src/test/resources/data-exchange/bank-statement/ofx/valid/ofx2-checking.xml` and confirm the status identifies `OFX_2_XML`, version `220`, masked account `…4321`, currency `USD`, three transactions, and **No data was changed**.
3. Copy that fixture to a `.qfx` filename, preview it, and confirm content still controls as OFX while a filename/content warning is visible.
4. Preview `qfx/valid/qfx-xml-header.qfx` and confirm it identifies `QFX_2_XML`, version `202`, account `…4321`, currency `USD`, and one transaction.
5. Preview `qfx/valid/qfx-sgml-v1.qfx` and confirm it identifies `QFX_1_SGML`, version `103`, account `…4321`, currency `USD`, and one transaction.
6. Preview the external-entity, entity-expansion, duplicate-FITID, multi-account, encrypted-QFX, compressed-QFX, unsupported-version, and malformed fixtures. Confirm each is rejected and the status identifies a safe bounded reason without exposing an unmasked account number.
7. Confirm no bank import batch, statement line, issue, or canonical ledger transaction is created by any preview.
8. At the supported laptop-width window size, confirm the bank preview status and warnings remain readable and operable and the existing SCLX/COA preview controls still work.

Record pass/fail notes, operating system, display scale, database path label, and tested fixture names in the PR before merge.
