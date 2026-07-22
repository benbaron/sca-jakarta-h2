package org.nonprofitbookkeeping.dataexchange;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

class DataExchangeFixtureContractTest
{
    private static final Path FIXTURES = Path.of("src", "test", "resources", "data-exchange");
    private static final Set<String> SCLX_VERSIONS = Set.of("1.0", "1.2", "1.3");
    private static final List<String> NORMALIZED_CSV_HEADER = List.of(
            "record_version", "source_format", "source_batch_external_id", "source_file_name",
            "statement_line_external_id", "institution_id", "bank_id", "account_id", "account_type",
            "transaction_date", "posted_date", "amount", "currency", "source_transaction_id",
            "transaction_type", "payee_id", "payee_name", "memo", "check_number", "reference",
            "correction_action", "corrected_source_transaction_id", "statement_start_date", "statement_end_date",
            "ledger_balance", "available_balance", "review_status", "duplicate_status",
            "matched_transaction_external_id");

    private final ObjectMapper strictJson = new ObjectMapper(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(32)
                    .maxNumberLength(128)
                    .maxStringLength(4 * 1024 * 1024)
                    .build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build());

    @Test
    void everyGovernedJsonFixtureIsStrictlyParseableUnlessItIsIntentionallyMalformed() throws Exception
    {
        Set<String> intentionallyInvalidJsonSyntax = Set.of(
                "sclx/invalid/malformed.json",
                "sclx/invalid/duplicate-root-key.json",
                "coa-json/invalid/malformed.json");
        try (Stream<Path> paths = Files.walk(FIXTURES))
        {
            for (Path path : paths.filter(candidate -> candidate.toString().endsWith(".json")).toList())
            {
                String relative = FIXTURES.relativize(path).toString().replace('\\', '/');
                if (intentionallyInvalidJsonSyntax.contains(relative))
                {
                    assertThrows(IOException.class, () -> strictJson.readTree(path.toFile()), relative);
                }
                else
                {
                    assertNotNull(strictJson.readTree(path.toFile()), relative);
                }
            }
        }
    }

    @Test
    void validSclxFixturesFreezeSupportedVersionsAndReferences() throws Exception
    {
        Path directory = FIXTURES.resolve("sclx/valid");
        Set<String> versions = new HashSet<>();
        for (Path path : jsonFiles(directory))
        {
            JsonNode root = strictJson.readTree(path.toFile());
            assertEquals("SCLX", root.path("format").asText(), path.toString());
            String version = root.path("version").asText();
            assertTrue(SCLX_VERSIONS.contains(version), path.toString());
            versions.add(version);
            assertTrue(root.path("organization").path("organizationId").asText().startsWith("org-"));
            assertSclxReferencesResolve(root, path);
        }
        assertEquals(SCLX_VERSIONS, versions);
    }

    @Test
    void invalidSclxFixturesExerciseBlockingCategories() throws Exception
    {
        Path invalid = FIXTURES.resolve("sclx/invalid");
        assertThrows(IOException.class, () -> strictJson.readTree(invalid.resolve("malformed.json").toFile()));
        assertThrows(IOException.class, () -> strictJson.readTree(invalid.resolve("duplicate-root-key.json").toFile()));

        JsonNode unsupported = strictJson.readTree(invalid.resolve("unsupported-version.json").toFile());
        assertFalse(SCLX_VERSIONS.contains(unsupported.path("version").asText()));

        JsonNode duplicate = strictJson.readTree(invalid.resolve("duplicate-identifiers.json").toFile());
        assertTrue(hasDuplicateSclxAccountIdentity(duplicate));

        JsonNode missingReference = strictJson.readTree(invalid.resolve("missing-reference.json").toFile());
        assertThrows(IllegalArgumentException.class,
                () -> assertSclxReferencesResolve(missingReference, invalid.resolve("missing-reference.json")));

        JsonNode missingIdentifiers = strictJson.readTree(invalid.resolve("missing-identifiers.json").toFile());
        assertTrue(missingIdentifiers.path("organization").path("organizationId").asText().isBlank());
    }

    @Test
    void donorGeneratedCoaFixtureFreezesActualEmittedShape() throws Exception
    {
        JsonNode root = strictJson.readTree(FIXTURES.resolve("coa-json/valid/donor-generated.json").toFile());
        assertEquals(List.of("chartOfAccounts", "rootAccounts", "accountNames", "accounts"),
                iterableToList(root.fieldNames()));
        assertFalse(root.has("_schemaVersion"), "Donor Javadoc is not the emitted compatibility authority.");

        JsonNode accounts = root.path("chartOfAccounts");
        assertTrue(accounts.isArray());
        assertEquals(3, accounts.size());
        assertEquals(accounts, root.path("accounts"), "The donor emits a redundant full account list.");
        assertEquals(2, root.path("rootAccounts").size());
        assertEquals("Assets, Fictional Community Bank Checking, Fictional Event Income",
                root.path("accountNames").asText());

        JsonNode first = accounts.get(0);
        assertEquals(List.of("associatedFundIds", "accountNumber", "increaseSide", "name", "accountCode",
                "accountType", "parentAccountId", "currency", "openingBalance", "supplementalLineKinds",
                "effectiveIncreaseSide"), iterableToList(first.fieldNames()));
        assertEquals(first.path("increaseSide").asText(), first.path("effectiveIncreaseSide").asText());
    }

    @Test
    void intendedCoaFixtureHasDeterministicShapeAndValidHierarchy() throws Exception
    {
        JsonNode root = strictJson.readTree(FIXTURES.resolve("coa-json/valid/sca-coa-1.0.json").toFile());
        assertEquals("SCA-COA", root.path("format").asText());
        assertEquals("1.0", root.path("version").asText());
        assertEquals("USD", root.path("chart").path("currency").asText());
        assertCoaGraphValid(root);

        List<String> codes = new ArrayList<>();
        root.path("accounts").forEach(account -> codes.add(account.path("code").asText()));
        assertEquals(List.of("1000", "1010", "4000"), codes);
    }

    @Test
    void invalidCoaFixturesExerciseVersionDuplicateReferenceCycleAndMoneyRules() throws Exception
    {
        Path invalid = FIXTURES.resolve("coa-json/invalid");
        assertThrows(IOException.class, () -> strictJson.readTree(invalid.resolve("malformed.json").toFile()));

        JsonNode unsupported = strictJson.readTree(invalid.resolve("unsupported-version.json").toFile());
        assertFalse("1.0".equals(unsupported.path("version").asText()));

        JsonNode duplicate = strictJson.readTree(invalid.resolve("duplicate-code.json").toFile());
        assertThrows(IllegalArgumentException.class, () -> assertCoaGraphValid(duplicate));

        JsonNode missingParent = strictJson.readTree(invalid.resolve("missing-parent.json").toFile());
        assertThrows(IllegalArgumentException.class, () -> assertCoaGraphValid(missingParent));

        JsonNode cycle = strictJson.readTree(invalid.resolve("hierarchy-cycle.json").toFile());
        assertThrows(IllegalArgumentException.class, () -> assertCoaGraphValid(cycle));

        BigDecimal overflow = new BigDecimal(strictJson.readTree(invalid.resolve("opening-balance-overflow.json").toFile())
                .path("accounts").get(0).path("openingBalance").asText());
        assertTrue(overflow.abs().compareTo(new BigDecimal("999999999999999.99")) > 0);
    }

    @Test
    void ofx2FixtureParsesSecurelyAndHasOneAccountWithUniqueFitids() throws Exception
    {
        Document document = parseSecureXml(Files.readAllBytes(FIXTURES.resolve("bank-statement/ofx/valid/ofx2-checking.xml")));
        assertEquals("OFX", document.getDocumentElement().getTagName());
        assertEquals(1, document.getElementsByTagName("BANKACCTFROM").getLength());
        assertEquals("FICTIONAL-4321", text(document, "ACCTID"));
        assertEquals("USD", text(document, "CURDEF"));

        Set<String> fitids = new HashSet<>();
        NodeList nodes = document.getElementsByTagName("FITID");
        assertEquals(3, nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++)
        {
            assertTrue(fitids.add(nodes.item(i).getTextContent()));
        }
        assertTrue(document.getElementsByTagName("CORRECTFITID").getLength() > 0);
        assertTrue(document.getElementsByTagName("LEDGERBAL").getLength() > 0);
    }

    @Test
    void xmlSecurityFixturesAreRejectedBeforeEntityResolution() throws Exception
    {
        Path invalid = FIXTURES.resolve("bank-statement/ofx/invalid");
        assertThrows(SAXException.class,
                () -> parseSecureXml(Files.readAllBytes(invalid.resolve("xml-external-entity.xml"))));
        assertThrows(SAXException.class,
                () -> parseSecureXml(Files.readAllBytes(invalid.resolve("xml-entity-expansion.xml"))));
        assertThrows(SAXException.class,
                () -> parseSecureXml(Files.readAllBytes(invalid.resolve("malformed.xml"))));
    }

    @Test
    void bankInvalidFixturesCoverMultiAccountMismatchIdentifiersAndMessageSets() throws Exception
    {
        Path invalid = FIXTURES.resolve("bank-statement/ofx/invalid");
        Document multi = parseSecureXml(Files.readAllBytes(invalid.resolve("multi-account.xml")));
        assertEquals(2, multi.getElementsByTagName("BANKACCTFROM").getLength());

        Document mismatch = parseSecureXml(Files.readAllBytes(invalid.resolve("account-mismatch.xml")));
        assertEquals("FICTIONAL-WRONG-9999", text(mismatch, "ACCTID"));

        Document duplicate = parseSecureXml(Files.readAllBytes(invalid.resolve("duplicate-fitid.xml")));
        NodeList fitidNodes = duplicate.getElementsByTagName("FITID");
        assertEquals(fitidNodes.item(0).getTextContent(), fitidNodes.item(1).getTextContent());

        Document missing = parseSecureXml(Files.readAllBytes(invalid.resolve("missing-fitid.xml")));
        assertEquals(0, missing.getElementsByTagName("FITID").getLength());

        Document unsupported = parseSecureXml(Files.readAllBytes(invalid.resolve("unsupported-message-set.xml")));
        assertEquals(1, unsupported.getElementsByTagName("INVSTMTMSGSRSV1").getLength());
        assertEquals(0, unsupported.getElementsByTagName("BANKMSGSRSV1").getLength());

        String unsupportedVersion = Files.readString(invalid.resolve("unsupported-version.xml"), StandardCharsets.UTF_8);
        assertTrue(unsupportedVersion.contains("VERSION=\"999\""));
    }

    @Test
    void qfxFixturesFreezeXmlAndSgmlHeaderVariants() throws Exception
    {
        String xmlQfx = read("bank-statement/qfx/valid/qfx-xml-header.qfx");
        Map<String, String> xmlHeader = qfxHeader(xmlQfx);
        assertEquals("200", xmlHeader.get("OFXHEADER"));
        assertEquals("202", xmlHeader.get("VERSION"));
        assertEquals("NONE", xmlHeader.get("SECURITY"));
        assertEquals("UTF-8", xmlHeader.get("ENCODING"));
        Document xmlBody = parseSecureXml(xmlBody(xmlQfx).getBytes(StandardCharsets.UTF_8));
        assertEquals("OFX", xmlBody.getDocumentElement().getTagName());

        String sgmlQfx = read("bank-statement/qfx/valid/qfx-sgml-v1.qfx");
        Map<String, String> sgmlHeader = qfxHeader(sgmlQfx);
        assertEquals("100", sgmlHeader.get("OFXHEADER"));
        assertEquals("103", sgmlHeader.get("VERSION"));
        assertEquals("OFXSGML", sgmlHeader.get("DATA"));
        assertEquals("NONE", sgmlHeader.get("SECURITY"));
        assertTrue(sgmlQfx.contains("<BANKACCTFROM>"));
        assertTrue(sgmlQfx.contains("<FITID>QFX-SGML-FICTIONAL-001"));
    }

    @Test
    void invalidQfxHeadersCoverEncryptionCompressionAndMalformedMetadata() throws Exception
    {
        Map<String, String> encrypted = qfxHeader(read("bank-statement/qfx/invalid/encrypted.qfx"));
        assertFalse("NONE".equals(encrypted.get("SECURITY")));

        Map<String, String> compressed = qfxHeader(read("bank-statement/qfx/invalid/unsupported-compression.qfx"));
        assertFalse("NONE".equals(compressed.get("COMPRESSION")));

        assertThrows(IllegalArgumentException.class,
                () -> qfxHeader(read("bank-statement/qfx/invalid/malformed-header.qfx")));
    }

    @Test
    void csvFixturesCoverSignedDebitCreditAndNormalizedRoundTripProfiles() throws Exception
    {
        List<List<String>> signed = parseCsv(read("bank-statement/csv/valid/mapped-signed.csv"), ',');
        assertEquals("Amount", signed.get(0).get(2));
        assertEquals(new BigDecimal("300.00"), new BigDecimal(signed.get(1).get(2)));
        assertEquals(new BigDecimal("-75.25"), new BigDecimal(signed.get(2).get(2)));
        assertEquals("Program supplies, brushes and paper", signed.get(2).get(6));

        List<List<String>> debitCredit = parseCsv(read("bank-statement/csv/valid/mapped-debit-credit.csv"), ',');
        BigDecimal debit = decimalOrZero(debitCredit.get(1).get(1));
        BigDecimal credit = decimalOrZero(debitCredit.get(1).get(2));
        assertEquals(new BigDecimal("-75.25"), credit.subtract(debit));
        debit = decimalOrZero(debitCredit.get(2).get(1));
        credit = decimalOrZero(debitCredit.get(2).get(2));
        assertEquals(new BigDecimal("85.00"), credit.subtract(debit));

        List<List<String>> normalized = parseCsv(read("bank-statement/csv/valid/normalized-round-trip.csv"), ',');
        assertEquals(NORMALIZED_CSV_HEADER, normalized.get(0));
        assertTrue(normalized.stream().skip(1).allMatch(row -> row.size() == NORMALIZED_CSV_HEADER.size()));
        assertTrue(normalized.stream().skip(1).allMatch(row -> "1.0".equals(row.get(0))));
    }

    @Test
    void invalidCsvFixturesExerciseQuotingHeaderAndAmountRules() throws Exception
    {
        assertThrows(IllegalArgumentException.class,
                () -> parseCsv(read("bank-statement/csv/invalid/malformed.csv"), ','));

        List<List<String>> duplicateHeaders = parseCsv(read("bank-statement/csv/invalid/duplicate-headers.csv"), ',');
        Set<String> normalized = new HashSet<>();
        assertFalse(duplicateHeaders.get(0).stream()
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .allMatch(normalized::add));

        List<List<String>> both = parseCsv(read("bank-statement/csv/invalid/both-debit-credit.csv"), ',');
        assertFalse(both.get(1).get(1).isBlank());
        assertFalse(both.get(1).get(2).isBlank());

        List<List<String>> missing = parseCsv(read("bank-statement/csv/invalid/missing-amount.csv"), ',');
        assertFalse(missing.get(0).stream().map(String::toLowerCase).anyMatch("amount"::equals));
    }

    @Test
    void generatedLimitFixturesFreezeAcceptedAndFirstRejectedValues() throws Exception
    {
        JsonNode root = strictJson.readTree(FIXTURES.resolve("limits/oversized-boundary.json").toFile());
        assertEquals("GENERATED_LIMIT_CASES", root.path("fixtureType").asText());
        for (JsonNode testCase : root.path("cases"))
        {
            assertEquals(testCase.path("accepted").asLong() + 1, testCase.path("rejected").asLong(),
                    testCase.toString());
        }

        JsonNode depth = strictJson.readTree(FIXTURES.resolve("limits/deep-nesting.json").toFile());
        assertDoesNotThrow(() -> strictJson.readTree(nestedJson(depth.path("acceptedDepth").asInt())));
        assertThrows(IOException.class, () -> strictJson.readTree(nestedJson(depth.path("rejectedDepth").asInt())));
    }

    @Test
    void databaseTransferFixturesCoverCorruptionAndArchiveTraversal() throws Exception
    {
        byte[] corrupt = Files.readAllBytes(FIXTURES.resolve("database-transfer/invalid/corrupt-backup.zip"));
        assertFalse(corrupt.length >= 4 && corrupt[0] == 'P' && corrupt[1] == 'K');

        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(
                FIXTURES.resolve("database-transfer/invalid/path-traversal.zip").toFile()))
        {
            assertTrue(zip.stream().map(java.util.zip.ZipEntry::getName)
                    .anyMatch(name -> name.startsWith("/") || name.contains("..")));
        }
    }

    @Test
    void fourExchangeContractsRemainExplicitlySeparated() throws Exception
    {
        Map<String, List<String>> requiredPhrases = Map.of(
                "doc/data-exchange/sclx.md", List.of("not a database backup", "Chart of Accounts", "bank-statement"),
                "doc/data-exchange/chart-of-accounts-json.md", List.of("does not transfer transactions", "SCLX", "Whole-database"),
                "doc/data-exchange/database-transfer.md", List.of("not a selected-company SCLX", "Chart of Accounts", "bank-statement"),
                "doc/data-exchange/bank-statement-interchange.md", List.of("never a double-entry ledger", "SCLX", "Whole-database"));

        for (Map.Entry<String, List<String>> entry : requiredPhrases.entrySet())
        {
            String document = Files.readString(Path.of(entry.getKey()), StandardCharsets.UTF_8);
            for (String phrase : entry.getValue())
            {
                assertTrue(document.toLowerCase(Locale.ROOT).contains(phrase.toLowerCase(Locale.ROOT)),
                        entry.getKey() + " must contain separation phrase: " + phrase);
            }
        }
    }

    @Test
    void fixtureManifestMatchesEveryGovernedFile() throws Exception
    {
        Map<String, String> expected = new LinkedHashMap<>();
        for (String line : Files.readAllLines(FIXTURES.resolve("manifest.sha256"), StandardCharsets.UTF_8))
        {
            if (line.isBlank() || line.startsWith("#"))
            {
                continue;
            }
            String[] parts = line.split("  ", 2);
            assertEquals(2, parts.length, line);
            expected.put(parts[1], parts[0]);
        }

        Map<String, String> actual = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(FIXTURES))
        {
            paths.filter(Files::isRegularFile)
                    .filter(path -> !path.equals(FIXTURES.resolve("manifest.sha256")))
                    .sorted()
                    .forEach(path -> actual.put(FIXTURES.relativize(path).toString().replace('\\', '/'), sha256(path)));
        }
        assertEquals(expected, actual);
    }

    @Test
    void fixturesContainOnlyFictionalIdentityAndBankData() throws Exception
    {
        Pattern email = Pattern.compile("[A-Za-z0-9._%+-]+@([A-Za-z0-9.-]+)");
        try (Stream<Path> paths = Files.walk(FIXTURES))
        {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(p -> !p.toString().endsWith(".zip"))
                    .toList())
            {
                String text = Files.readString(path, StandardCharsets.UTF_8);
                assertFalse(text.toLowerCase(Locale.ROOT).contains("benbaron"), path.toString());
                assertFalse(text.toLowerCase(Locale.ROOT).contains("sca-jakarta-h2"), path.toString());
                var matcher = email.matcher(text);
                while (matcher.find())
                {
                    assertEquals("example.invalid", matcher.group(1).toLowerCase(Locale.ROOT), path.toString());
                }
                if (text.contains("<ACCTID>"))
                {
                    assertTrue(text.contains("<ACCTID>FICTIONAL-"), path.toString());
                }
            }
        }
    }

    private void assertSclxReferencesResolve(JsonNode root, Path source)
    {
        Set<String> accounts = new HashSet<>();
        for (JsonNode account : root.path("chartOfAccounts"))
        {
            String identity = firstNonblank(account, "accountId", "code", "Number");
            if (identity == null || !accounts.add(identity))
            {
                throw new IllegalArgumentException("Duplicate or missing account identity in " + source);
            }
        }
        Set<String> funds = new HashSet<>();
        root.path("funds").forEach(fund -> funds.add(fund.path("fundId").asText()));
        for (JsonNode transaction : root.path("transactions"))
        {
            if (transaction.path("transactionId").asText().isBlank())
            {
                throw new IllegalArgumentException("Missing transaction identity in " + source);
            }
            for (JsonNode line : transaction.path("lines"))
            {
                if (!accounts.contains(line.path("accountId").asText()))
                {
                    throw new IllegalArgumentException("Missing account reference in " + source);
                }
                if (!line.path("fundId").asText().isBlank() && !funds.contains(line.path("fundId").asText()))
                {
                    throw new IllegalArgumentException("Missing fund reference in " + source);
                }
            }
        }
    }

    private static boolean hasDuplicateSclxAccountIdentity(JsonNode root)
    {
        Set<String> identities = new HashSet<>();
        for (JsonNode account : root.path("chartOfAccounts"))
        {
            String identity = firstNonblank(account, "accountId", "code", "Number");
            if (!identities.add(identity))
            {
                return true;
            }
        }
        return false;
    }

    private static String firstNonblank(JsonNode node, String... fields)
    {
        for (String field : fields)
        {
            String value = node.path(field).asText();
            if (!value.isBlank())
            {
                return value;
            }
        }
        return null;
    }

    private static void assertCoaGraphValid(JsonNode root)
    {
        Map<String, String> parents = new HashMap<>();
        for (JsonNode account : root.path("accounts"))
        {
            String code = account.path("code").asText();
            if (code.isBlank() || parents.containsKey(code))
            {
                throw new IllegalArgumentException("Duplicate or missing account code: " + code);
            }
            parents.put(code, account.path("parentCode").asText(null));
        }
        for (Map.Entry<String, String> entry : parents.entrySet())
        {
            String parent = entry.getValue();
            if (parent != null && !parents.containsKey(parent))
            {
                throw new IllegalArgumentException("Missing parent: " + parent);
            }
            Set<String> seen = new HashSet<>();
            String cursor = entry.getKey();
            while (cursor != null)
            {
                if (!seen.add(cursor))
                {
                    throw new IllegalArgumentException("Hierarchy cycle: " + entry.getKey());
                }
                cursor = parents.get(cursor);
            }
        }
    }

    private static Document parseSecureXml(byte[] bytes) throws Exception
    {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }

    private static String text(Document document, String tag)
    {
        NodeList nodes = document.getElementsByTagName(tag);
        assertTrue(nodes.getLength() > 0, "Missing XML tag: " + tag);
        return nodes.item(0).getTextContent();
    }

    private static Map<String, String> qfxHeader(String content)
    {
        int separator = content.indexOf("\n\n");
        if (separator < 0)
        {
            separator = content.indexOf("\r\n\r\n");
        }
        if (separator < 0)
        {
            throw new IllegalArgumentException("QFX header terminator is missing.");
        }
        String headerText = content.substring(0, separator).replace("\r", "");
        Map<String, String> header = new LinkedHashMap<>();
        for (String line : headerText.split("\n"))
        {
            int colon = line.indexOf(':');
            if (colon <= 0 || colon == line.length() - 1)
            {
                throw new IllegalArgumentException("Malformed QFX header line: " + line);
            }
            String key = line.substring(0, colon).trim().toUpperCase(Locale.ROOT);
            if (header.put(key, line.substring(colon + 1).trim()) != null)
            {
                throw new IllegalArgumentException("Duplicate QFX header: " + key);
            }
        }
        return header;
    }

    private static String xmlBody(String qfx)
    {
        int xml = qfx.indexOf("<?xml");
        if (xml < 0)
        {
            throw new IllegalArgumentException("QFX XML body is missing.");
        }
        return qfx.substring(xml);
    }

    private static List<List<String>> parseCsv(String content, char delimiter)
    {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < content.length(); i++)
        {
            char ch = content.charAt(i);
            if (quoted)
            {
                if (ch == '"')
                {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"')
                    {
                        field.append('"');
                        i++;
                    }
                    else
                    {
                        quoted = false;
                    }
                }
                else
                {
                    field.append(ch);
                }
            }
            else if (ch == '"' && field.length() == 0)
            {
                quoted = true;
            }
            else if (ch == delimiter)
            {
                row.add(field.toString());
                field.setLength(0);
            }
            else if (ch == '\n' || ch == '\r')
            {
                if (ch == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n')
                {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                if (!(row.size() == 1 && row.get(0).isEmpty()))
                {
                    rows.add(List.copyOf(row));
                }
                row.clear();
            }
            else
            {
                field.append(ch);
            }
        }
        if (quoted)
        {
            throw new IllegalArgumentException("CSV contains an unclosed quoted field.");
        }
        if (field.length() > 0 || !row.isEmpty())
        {
            row.add(field.toString());
            rows.add(List.copyOf(row));
        }
        if (!rows.isEmpty())
        {
            int width = rows.get(0).size();
            if (rows.stream().anyMatch(candidate -> candidate.size() != width))
            {
                throw new IllegalArgumentException("CSV rows have inconsistent column counts.");
            }
        }
        return rows;
    }

    private static BigDecimal decimalOrZero(String value)
    {
        return value == null || value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value);
    }

    private static String nestedJson(int depth)
    {
        StringBuilder value = new StringBuilder("0");
        for (int i = 0; i < depth; i++)
        {
            value.insert(0, "{\"n\":").append('}');
        }
        return value.toString();
    }

    private static <T> List<T> iterableToList(java.util.Iterator<T> values)
    {
        List<T> result = new ArrayList<>();
        values.forEachRemaining(result::add);
        return result;
    }

    private static List<Path> jsonFiles(Path directory) throws IOException
    {
        try (Stream<Path> paths = Files.list(directory))
        {
            return paths.filter(path -> path.toString().endsWith(".json")).sorted().toList();
        }
    }

    private static String read(String relative) throws IOException
    {
        return Files.readString(FIXTURES.resolve(relative), StandardCharsets.UTF_8);
    }

    private static String sha256(Path path)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
            StringBuilder value = new StringBuilder();
            for (byte part : digest)
            {
                value.append(String.format("%02x", part));
            }
            return value.toString();
        }
        catch (Exception ex)
        {
            throw new IllegalStateException("Could not hash fixture: " + path, ex);
        }
    }
}
