package org.nonprofitbookkeeping.interchange.bank;

import org.nonprofitbookkeeping.interchange.InterchangeMessageSeverity;
import org.nonprofitbookkeeping.interchange.InterchangeValidationMessage;
import org.nonprofitbookkeeping.model.BankingDataFormat;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict, bounded, content-first parser for governed OFX 2.x and QFX variants. */
public class BankStatementParser
{
    static final int MAX_FILE_BYTES = 64 * 1024 * 1024;
    private static final int MAX_DEPTH = 64;
    private static final int MAX_ATTRIBUTES = 128;
    private static final int MAX_FIELD_CHARS = 1024 * 1024;
    private static final int MAX_RECORDS = 1_000_000;
    private static final Pattern HEADER_LINE = Pattern.compile("^([A-Za-z][A-Za-z0-9]*):(.*)$");
    private static final Pattern OFX_PI = Pattern.compile("(?is)<\\?OFX\\s+([^?]+)\\?>");
    private static final Pattern PI_ATTRIBUTE = Pattern.compile("([A-Za-z][A-Za-z0-9]*)\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern SGML_TAG = Pattern.compile("<\\s*(/?)\\s*([A-Za-z][A-Za-z0-9]*)\\s*>");
    private static final Pattern OFX_DATE = Pattern.compile(
            "^(\\d{8})(\\d{6})?(?:\\.\\d+)?(?:\\[([+-]?\\d{1,2})(?::(\\d{2}))?(?::?[A-Za-z]{1,8})?\\])?$");
    private static final Set<String> SGML_CONTAINERS = Set.of(
            "OFX", "SIGNONMSGSRSV1", "SONRS", "STATUS", "FI",
            "BANKMSGSRSV1", "STMTTRNRS", "STMTRS", "BANKACCTFROM", "BANKTRANLIST",
            "CREDITCARDMSGSRSV1", "CCSTMTTRNRS", "CCSTMTRS", "CCACCTFROM",
            "STMTTRN", "LEDGERBAL", "AVAILBAL");
    private static final Set<String> SUPPORTED_XML_VERSIONS = Set.of("200", "202", "203", "210", "211", "220");
    private static final Set<String> SUPPORTED_SGML_VERSIONS = Set.of("102", "103");

    public BankStatementDocument parse(Path path)
    {
        if (path == null)
        {
            throw failure("SOURCE_REQUIRED", "source", "Bank statement path is required");
        }
        if (!Files.isRegularFile(path))
        {
            throw failure("SOURCE_NOT_FOUND", "source", "Bank statement file does not exist: " + path);
        }
        try
        {
            long size = Files.size(path);
            if (size > MAX_FILE_BYTES)
            {
                throw failure("SOURCE_TOO_LARGE", "source", "Bank statement exceeds the 64 MiB limit");
            }
            return parse(Files.readAllBytes(path), path.getFileName().toString());
        }
        catch (IOException ex)
        {
            throw failure("SOURCE_READ_FAILED", "source", "Could not read bank statement: " + ex.getMessage(), ex);
        }
    }

    public BankStatementDocument parse(byte[] bytes, String sourceName)
    {
        byte[] source = bytes == null ? new byte[0] : bytes.clone();
        if (source.length == 0)
        {
            throw failure("SOURCE_EMPTY", "source", "Bank statement is empty");
        }
        if (source.length > MAX_FILE_BYTES)
        {
            throw failure("SOURCE_TOO_LARGE", "source", "Bank statement exceeds the 64 MiB limit");
        }
        for (byte value : source)
        {
            if (value == 0)
            {
                throw failure("ENCODING_NUL", "source", "Bank statement contains a NUL byte");
            }
        }
        if (startsWith(source, new byte[] {(byte) 0xff, (byte) 0xfe})
                || startsWith(source, new byte[] {(byte) 0xfe, (byte) 0xff})
                || startsWith(source, new byte[] {0, 0, (byte) 0xfe, (byte) 0xff})
                || startsWith(source, new byte[] {(byte) 0xff, (byte) 0xfe, 0, 0}))
        {
            throw failure("ENCODING_UNSUPPORTED", "source", "UTF-16 and UTF-32 bank statements are not supported");
        }

        String asciiPrefix = new String(source, 0, Math.min(source.length, 16 * 1024), StandardCharsets.US_ASCII);
        HeaderEnvelope envelope = headerEnvelope(asciiPrefix);
        Charset charset = charset(envelope.headers());
        String decoded = decode(source, charset);
        envelope = headerEnvelope(decoded);
        String body = envelope.body().stripLeading();
        if (body.isBlank())
        {
            throw failure("BODY_MISSING", "source", "OFX/QFX body is missing");
        }

        boolean hasHeader = !envelope.headers().isEmpty();
        boolean sgml = hasHeader && "100".equals(envelope.headers().get("OFXHEADER"));
        validateHeaders(envelope.headers(), sgml);
        String xml = sgml ? sgmlToXml(body) : body;
        Document document = secureXml(xml);
        Element root = document.getDocumentElement();
        if (root == null || !"OFX".equals(name(root)))
        {
            throw failure("ROOT_UNSUPPORTED", "OFX", "Bank statement root must be OFX");
        }
        validateTree(root, 1);

        String version = version(envelope.headers(), decoded, sgml);
        if (sgml && !SUPPORTED_SGML_VERSIONS.contains(version))
        {
            throw failure("VERSION_UNSUPPORTED", "header.VERSION", "Unsupported QFX SGML version: " + version);
        }
        if (!sgml && !SUPPORTED_XML_VERSIONS.contains(version))
        {
            throw failure("VERSION_UNSUPPORTED", "header.VERSION", "Unsupported OFX/QFX XML version: " + version);
        }

        BankingDataFormat format = hasHeader ? BankingDataFormat.QFX : BankingDataFormat.OFX;
        BankStatementDocument.Variant variant = sgml
                ? BankStatementDocument.Variant.QFX_1_SGML
                : hasHeader ? BankStatementDocument.Variant.QFX_2_XML : BankStatementDocument.Variant.OFX_2_XML;
        List<InterchangeValidationMessage> messages = new ArrayList<>();
        filenameWarning(sourceName, format, messages);

        List<Element> bankStatements = path(root, "BANKMSGSRSV1", "STMTTRNRS", "STMTRS");
        List<Element> cardStatements = path(root, "CREDITCARDMSGSRSV1", "CCSTMTTRNRS", "CCSTMTRS");
        int statementCount = bankStatements.size() + cardStatements.size();
        if (statementCount == 0)
        {
            throw failure("MESSAGE_SET_UNSUPPORTED", "OFX", "No supported bank or credit-card statement response was found");
        }
        if (statementCount != 1)
        {
            throw failure("MULTI_ACCOUNT", "OFX", "Bank statement must contain exactly one supported account");
        }
        if (unsupportedMessageSetPresent(root))
        {
            messages.add(warning("UNSUPPORTED_MESSAGE_SET_IGNORED", "OFX",
                    "Unsupported OFX message sets were ignored beside the selected statement"));
        }

        Element statement = bankStatements.isEmpty() ? cardStatements.get(0) : bankStatements.get(0);
        boolean creditCard = bankStatements.isEmpty();
        String accountPath = creditCard ? "CCACCTFROM" : "BANKACCTFROM";
        Element accountElement = singleton(statement, accountPath, true,
                "OFX.statement." + accountPath);
        String accountId = scalar(accountElement, "ACCTID", true, "OFX.statement.account.ACCTID");
        String bankId = creditCard ? "" : scalar(accountElement, "BANKID", true, "OFX.statement.account.BANKID");
        String accountType = creditCard ? "CREDITCARD"
                : scalar(accountElement, "ACCTTYPE", true, "OFX.statement.account.ACCTTYPE");
        String institutionId = institutionId(root);
        BankStatementDocument.AccountIdentity account = new BankStatementDocument.AccountIdentity(
                institutionId, bankId, accountId, accountType);
        String currency = scalar(statement, "CURDEF", true, "OFX.statement.CURDEF").toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}"))
        {
            throw failure("CURRENCY_INVALID", "OFX.statement.CURDEF", "Statement currency must be a three-letter code");
        }

        Element transactionList = singleton(statement, "BANKTRANLIST", true, "OFX.statement.BANKTRANLIST");
        LocalDate startDate = date(scalar(transactionList, "DTSTART", false, "OFX.statement.BANKTRANLIST.DTSTART"),
                "OFX.statement.BANKTRANLIST.DTSTART", messages);
        LocalDate endDate = date(scalar(transactionList, "DTEND", false, "OFX.statement.BANKTRANLIST.DTEND"),
                "OFX.statement.BANKTRANLIST.DTEND", messages);
        List<Element> transactionElements = directChildren(transactionList, "STMTTRN");
        if (transactionElements.isEmpty())
        {
            throw failure("TRANSACTIONS_MISSING", "OFX.statement.BANKTRANLIST", "Statement contains no transactions");
        }
        if (transactionElements.size() > MAX_RECORDS)
        {
            throw failure("TRANSACTION_LIMIT", "OFX.statement.BANKTRANLIST", "Statement record limit exceeded");
        }
        List<BankStatementDocument.Transaction> transactions = new ArrayList<>();
        Set<String> fitIds = new HashSet<>();
        for (int index = 0; index < transactionElements.size(); index++)
        {
            transactions.add(transaction(transactionElements.get(index), index + 1, fitIds, messages));
        }

        BigDecimal ledgerBalance = balance(statement, "LEDGERBAL");
        BigDecimal availableBalance = balance(statement, "AVAILBAL");
        return new BankStatementDocument(
                sourceName,
                format,
                variant,
                version,
                charset.name(),
                account,
                currency,
                startDate,
                endDate,
                ledgerBalance,
                availableBalance,
                transactions,
                messages);
    }

    private static BankStatementDocument.Transaction transaction(
            Element element,
            int row,
            Set<String> fitIds,
            List<InterchangeValidationMessage> messages)
    {
        String path = "OFX.statement.transactions[" + row + "]";
        String fitId = scalar(element, "FITID", true, path + ".FITID");
        String comparisonId = fitId.toUpperCase(Locale.ROOT);
        if (!fitIds.add(comparisonId))
        {
            throw failure("FITID_DUPLICATE", path + ".FITID", "Duplicate FITID in statement: " + fitId);
        }
        String postedRaw = scalar(element, "DTPOSTED", false, path + ".DTPOSTED");
        String transactionRaw = scalar(element, "DTUSER", false, path + ".DTUSER");
        LocalDate posted = date(postedRaw, path + ".DTPOSTED", messages);
        LocalDate transactionDate = date(transactionRaw, path + ".DTUSER", messages);
        if (posted == null && transactionDate == null)
        {
            throw failure("DATE_MISSING", path, "Statement transaction requires DTPOSTED or DTUSER");
        }
        BigDecimal amount = decimal(scalar(element, "TRNAMT", true, path + ".TRNAMT"), path + ".TRNAMT", true);
        String correctionAction = scalar(element, "CORRECTACTION", false, path + ".CORRECTACTION");
        String correctedFitId = scalar(element, "CORRECTFITID", false, path + ".CORRECTFITID");
        if (correctionAction.isBlank() != correctedFitId.isBlank())
        {
            throw failure("CORRECTION_INCOMPLETE", path, "Correction action and corrected FITID must be supplied together");
        }
        if (!correctionAction.isBlank()
                && !Set.of("DELETE", "REPLACE").contains(correctionAction.toUpperCase(Locale.ROOT)))
        {
            throw failure("CORRECTION_ACTION_UNSUPPORTED", path + ".CORRECTACTION",
                    "Unsupported OFX correction action: " + correctionAction);
        }
        return new BankStatementDocument.Transaction(
                row,
                transactionDate,
                posted,
                amount,
                fitId,
                scalar(element, "TRNTYPE", true, path + ".TRNTYPE"),
                scalar(element, "NAME", false, path + ".NAME"),
                scalar(element, "MEMO", false, path + ".MEMO"),
                scalar(element, "CHECKNUM", false, path + ".CHECKNUM"),
                scalar(element, "REFNUM", false, path + ".REFNUM"),
                correctionAction.toUpperCase(Locale.ROOT),
                correctedFitId);
    }

    private static BigDecimal balance(Element statement, String tag)
    {
        Element balance = singleton(statement, tag, false, "OFX.statement." + tag);
        if (balance == null)
        {
            return null;
        }
        return decimal(scalar(balance, "BALAMT", true, "OFX.statement." + tag + ".BALAMT"),
                "OFX.statement." + tag + ".BALAMT", false);
    }

    private static BigDecimal decimal(String raw, String path, boolean nonzero)
    {
        try
        {
            BigDecimal value = new BigDecimal(raw);
            if (value.scale() > 4 || value.precision() > 19)
            {
                throw failure("AMOUNT_PRECISION", path, "Amount exceeds DECIMAL(19,4) without rounding");
            }
            if (nonzero && value.signum() == 0)
            {
                throw failure("AMOUNT_ZERO", path, "Statement transaction amount cannot be zero");
            }
            return value;
        }
        catch (NumberFormatException ex)
        {
            throw failure("AMOUNT_INVALID", path, "Invalid statement amount: " + raw, ex);
        }
    }

    private static LocalDate date(String raw, String path, List<InterchangeValidationMessage> messages)
    {
        if (raw == null || raw.isBlank())
        {
            return null;
        }
        Matcher matcher = OFX_DATE.matcher(raw.trim());
        if (!matcher.matches())
        {
            throw failure("DATE_INVALID", path, "Invalid OFX date: " + raw);
        }
        if (matcher.group(3) != null)
        {
            try
            {
                int hours = Integer.parseInt(matcher.group(3));
                int minutes = matcher.group(4) == null ? 0 : Integer.parseInt(matcher.group(4));
                if (hours < -18 || hours > 18 || minutes < 0 || minutes > 59
                        || (Math.abs(hours) == 18 && minutes != 0))
                {
                    throw new DateTimeException("invalid offset");
                }
            }
            catch (NumberFormatException | DateTimeException ex)
            {
                throw failure("DATE_OFFSET_INVALID", path, "Invalid OFX date offset: " + raw, ex);
            }
        }
        else if (matcher.group(2) != null)
        {
            messages.add(warning("DATE_ZONE_MISSING", path,
                    "OFX time has no explicit offset; the source calendar date was retained"));
        }
        try
        {
            return LocalDate.parse(matcher.group(1), DateTimeFormatter.BASIC_ISO_DATE);
        }
        catch (DateTimeParseException ex)
        {
            throw failure("DATE_INVALID", path, "Invalid OFX calendar date: " + raw, ex);
        }
    }

    private static void filenameWarning(
            String sourceName,
            BankingDataFormat format,
            List<InterchangeValidationMessage> messages)
    {
        String name = sourceName == null ? "" : sourceName.toLowerCase(Locale.ROOT);
        boolean mismatch = (name.endsWith(".ofx") && format == BankingDataFormat.QFX)
                || (name.endsWith(".qfx") && format == BankingDataFormat.OFX);
        if (mismatch)
        {
            messages.add(warning("FILENAME_CONTENT_MISMATCH", "source",
                    "Filename extension disagrees with the content envelope; content controls"));
        }
    }

    private static String institutionId(Element root)
    {
        List<Element> fi = path(root, "SIGNONMSGSRSV1", "SONRS", "FI");
        if (fi.size() != 1)
        {
            return "";
        }
        String org = scalar(fi.get(0), "ORG", false, "OFX.SIGNONMSGSRSV1.SONRS.FI.ORG");
        String fid = scalar(fi.get(0), "FID", false, "OFX.SIGNONMSGSRSV1.SONRS.FI.FID");
        return org.isBlank() ? fid : org;
    }

    private static boolean unsupportedMessageSetPresent(Element root)
    {
        for (Element child : directChildren(root, null))
        {
            String name = name(child);
            if (name.endsWith("MSGSRSV1")
                    && !Set.of("SIGNONMSGSRSV1", "BANKMSGSRSV1", "CREDITCARDMSGSRSV1").contains(name))
            {
                return true;
            }
        }
        return false;
    }

    private static List<Element> path(Element start, String... names)
    {
        List<Element> current = List.of(start);
        for (String childName : names)
        {
            List<Element> next = new ArrayList<>();
            for (Element element : current)
            {
                next.addAll(directChildren(element, childName));
            }
            current = next;
        }
        return current;
    }

    private static Element singleton(Element parent, String tag, boolean required, String path)
    {
        List<Element> values = directChildren(parent, tag);
        if (values.size() > 1)
        {
            throw failure("SINGLETON_DUPLICATE", path, "Duplicate singleton element: " + tag);
        }
        if (values.isEmpty())
        {
            if (required)
            {
                throw failure("ELEMENT_MISSING", path, "Required element is missing: " + tag);
            }
            return null;
        }
        return values.get(0);
    }

    private static String scalar(Element parent, String tag, boolean required, String path)
    {
        Element value = singleton(parent, tag, required, path);
        if (value == null)
        {
            return "";
        }
        for (Element ignored : directChildren(value, null))
        {
            throw failure("SCALAR_STRUCTURE", path, "Scalar element contains nested elements: " + tag);
        }
        String text = value.getTextContent() == null ? "" : value.getTextContent().trim();
        if (text.length() > MAX_FIELD_CHARS)
        {
            throw failure("FIELD_TOO_LONG", path, "Statement field exceeds the 1 MiB character limit");
        }
        if (required && text.isBlank())
        {
            throw failure("VALUE_MISSING", path, "Required value is blank: " + tag);
        }
        return text;
    }

    private static List<Element> directChildren(Element parent, String expectedName)
    {
        List<Element> values = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++)
        {
            Node child = children.item(i);
            if (child instanceof Element element
                    && (expectedName == null || expectedName.equals(name(element))))
            {
                values.add(element);
            }
        }
        return values;
    }

    private static String name(Element element)
    {
        String local = element.getLocalName();
        String value = local == null ? element.getTagName() : local;
        return value.toUpperCase(Locale.ROOT);
    }

    private static void validateTree(Element element, int depth)
    {
        if (depth > MAX_DEPTH)
        {
            throw failure("XML_DEPTH_LIMIT", name(element), "OFX/QFX nesting depth exceeds 64");
        }
        if (element.getAttributes().getLength() > MAX_ATTRIBUTES)
        {
            throw failure("XML_ATTRIBUTE_LIMIT", name(element), "OFX/QFX element has too many attributes");
        }
        for (Element child : directChildren(element, null))
        {
            validateTree(child, depth + 1);
        }
    }

    private static Document secureXml(String xml)
    {
        try
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
            var builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> {
                throw new SAXException("External entities are prohibited");
            });
            builder.setErrorHandler(new org.xml.sax.ErrorHandler()
            {
                @Override
                public void warning(SAXParseException exception) throws SAXException
                {
                    throw exception;
                }

                @Override
                public void error(SAXParseException exception) throws SAXException
                {
                    throw exception;
                }

                @Override
                public void fatalError(SAXParseException exception) throws SAXException
                {
                    throw exception;
                }
            });
            return builder.parse(new InputSource(new StringReader(xml)));
        }
        catch (ParserConfigurationException | SAXException | IOException | RuntimeException ex)
        {
            throw failure("XML_INVALID", "OFX", "Malformed or unsafe OFX/QFX document: " + ex.getMessage(), ex);
        }
    }

    private static String sgmlToXml(String body)
    {
        if (body.regionMatches(true, 0, "<!DOCTYPE", 0, 9))
        {
            throw failure("SGML_DOCTYPE", "OFX", "DOCTYPE is prohibited in QFX SGML");
        }
        Matcher matcher = SGML_TAG.matcher(body);
        StringBuilder out = new StringBuilder(body.length() + 1024);
        Deque<String> containers = new ArrayDeque<>();
        String openScalar = null;
        int cursor = 0;
        int tagCount = 0;
        while (matcher.find())
        {
            String between = body.substring(cursor, matcher.start());
            if (openScalar != null)
            {
                out.append(escapeXml(between.trim())).append("</").append(openScalar).append('>');
                openScalar = null;
            }
            else if (!between.isBlank())
            {
                throw failure("SGML_TEXT_OUTSIDE_FIELD", "OFX", "Unexpected text outside a QFX scalar field");
            }

            boolean closing = !matcher.group(1).isEmpty();
            String tag = matcher.group(2).toUpperCase(Locale.ROOT);
            if (closing)
            {
                if (containers.isEmpty() || !containers.peek().equals(tag))
                {
                    throw failure("SGML_NESTING", "OFX", "Mismatched QFX SGML closing tag: " + tag);
                }
                containers.pop();
                out.append("</").append(tag).append('>');
            }
            else if (SGML_CONTAINERS.contains(tag))
            {
                containers.push(tag);
                out.append('<').append(tag).append('>');
            }
            else
            {
                out.append('<').append(tag).append('>');
                openScalar = tag;
            }
            cursor = matcher.end();
            tagCount++;
            if (tagCount > MAX_RECORDS * 16L)
            {
                throw failure("SGML_TAG_LIMIT", "OFX", "QFX SGML tag limit exceeded");
            }
        }
        String tail = body.substring(cursor);
        if (openScalar != null)
        {
            out.append(escapeXml(tail.trim())).append("</").append(openScalar).append('>');
        }
        else if (!tail.isBlank())
        {
            throw failure("SGML_TRAILING_TEXT", "OFX", "Unexpected trailing QFX SGML text");
        }
        if (!containers.isEmpty())
        {
            throw failure("SGML_NESTING", "OFX", "Unclosed QFX SGML container: " + containers.peek());
        }
        return out.toString();
    }

    private static String escapeXml(String value)
    {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static String version(Map<String, String> headers, String decoded, boolean sgml)
    {
        String headerVersion = headers.get("VERSION");
        if (headerVersion != null && !headerVersion.isBlank())
        {
            return headerVersion.trim();
        }
        Matcher pi = OFX_PI.matcher(decoded);
        if (pi.find())
        {
            Matcher attributes = PI_ATTRIBUTE.matcher(pi.group(1));
            while (attributes.find())
            {
                if ("VERSION".equalsIgnoreCase(attributes.group(1)))
                {
                    return attributes.group(2).trim();
                }
            }
        }
        if (sgml)
        {
            throw failure("VERSION_MISSING", "header.VERSION", "QFX SGML VERSION header is required");
        }
        return "200";
    }

    private static HeaderEnvelope headerEnvelope(String decoded)
    {
        String normalized = decoded == null ? "" : decoded.replace("\r\n", "\n").replace('\r', '\n');
        String stripped = normalized.stripLeading();
        if (!stripped.regionMatches(true, 0, "OFXHEADER:", 0, 10))
        {
            return new HeaderEnvelope(Map.of(), stripped);
        }
        int boundary = stripped.indexOf("\n\n");
        if (boundary < 0)
        {
            throw failure("HEADER_TERMINATOR_MISSING", "header", "QFX header terminator is missing");
        }
        Map<String, String> headers = new LinkedHashMap<>();
        for (String line : stripped.substring(0, boundary).split("\n"))
        {
            Matcher matcher = HEADER_LINE.matcher(line.trim());
            if (!matcher.matches())
            {
                throw failure("HEADER_MALFORMED", "header", "Malformed QFX header line: " + line);
            }
            String key = matcher.group(1).toUpperCase(Locale.ROOT);
            if (headers.putIfAbsent(key, matcher.group(2).trim()) != null)
            {
                throw failure("HEADER_DUPLICATE", "header." + key, "Duplicate QFX header: " + key);
            }
        }
        return new HeaderEnvelope(Map.copyOf(headers), stripped.substring(boundary + 2));
    }

    private static void validateHeaders(Map<String, String> headers, boolean sgml)
    {
        if (headers.isEmpty())
        {
            return;
        }
        Set<String> allowed = Set.of("OFXHEADER", "DATA", "VERSION", "SECURITY", "ENCODING", "CHARSET",
                "COMPRESSION", "OLDFILEUID", "NEWFILEUID");
        for (String key : headers.keySet())
        {
            if (!allowed.contains(key))
            {
                throw failure("HEADER_UNSUPPORTED", "header." + key, "Unsupported QFX header: " + key);
            }
        }
        requireHeader(headers, "OFXHEADER");
        requireHeader(headers, "DATA");
        requireHeader(headers, "VERSION");
        requireHeader(headers, "SECURITY");
        requireHeader(headers, "ENCODING");
        requireHeader(headers, "CHARSET");
        requireHeader(headers, "COMPRESSION");
        if (!"NONE".equalsIgnoreCase(headers.get("SECURITY")))
        {
            throw failure("SECURITY_UNSUPPORTED", "header.SECURITY", "Encrypted or secured QFX is not supported");
        }
        if (!"NONE".equalsIgnoreCase(headers.get("COMPRESSION")))
        {
            throw failure("COMPRESSION_UNSUPPORTED", "header.COMPRESSION", "Compressed QFX is not supported");
        }
        if (sgml && !"OFXSGML".equalsIgnoreCase(headers.get("DATA")))
        {
            throw failure("DATA_UNSUPPORTED", "header.DATA", "QFX 1.x requires DATA:OFXSGML");
        }
        if (!sgml && !Set.of("OFXXML", "OFXSGML").contains(headers.get("DATA").toUpperCase(Locale.ROOT)))
        {
            throw failure("DATA_UNSUPPORTED", "header.DATA", "QFX 2.x requires OFXXML or OFXSGML data");
        }
    }

    private static void requireHeader(Map<String, String> headers, String key)
    {
        if (headers.getOrDefault(key, "").isBlank())
        {
            throw failure("HEADER_REQUIRED", "header." + key, "Required QFX header is missing: " + key);
        }
    }

    private static Charset charset(Map<String, String> headers)
    {
        if (headers.isEmpty())
        {
            return StandardCharsets.UTF_8;
        }
        String encoding = headers.getOrDefault("ENCODING", "").toUpperCase(Locale.ROOT);
        String charset = headers.getOrDefault("CHARSET", "").toUpperCase(Locale.ROOT);
        if ("UTF-8".equals(encoding) || "UTF8".equals(encoding))
        {
            if (!Set.of("NONE", "UTF-8", "UTF8").contains(charset))
            {
                throw failure("ENCODING_MISMATCH", "header.CHARSET", "QFX UTF-8 encoding conflicts with CHARSET");
            }
            return StandardCharsets.UTF_8;
        }
        if ("USASCII".equals(encoding) || "US-ASCII".equals(encoding))
        {
            if ("1252".equals(charset))
            {
                return Charset.forName("windows-1252");
            }
            if (!Set.of("NONE", "USASCII", "US-ASCII", "UTF-8", "UTF8").contains(charset))
            {
                throw failure("ENCODING_UNSUPPORTED", "header.CHARSET", "Unsupported QFX character set: " + charset);
            }
            return StandardCharsets.US_ASCII;
        }
        throw failure("ENCODING_UNSUPPORTED", "header.ENCODING", "Unsupported QFX encoding: " + encoding);
    }

    private static String decode(byte[] bytes, Charset charset)
    {
        try
        {
            CharBuffer chars = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return chars.toString();
        }
        catch (CharacterCodingException ex)
        {
            throw failure("ENCODING_INVALID", "source", "Bank statement bytes do not match declared encoding", ex);
        }
    }

    private static boolean startsWith(byte[] source, byte[] prefix)
    {
        if (source.length < prefix.length)
        {
            return false;
        }
        for (int i = 0; i < prefix.length; i++)
        {
            if (source[i] != prefix[i])
            {
                return false;
            }
        }
        return true;
    }

    private static InterchangeValidationMessage warning(String code, String path, String message)
    {
        return new InterchangeValidationMessage(InterchangeMessageSeverity.WARNING, code, path, message, false);
    }

    private static IllegalArgumentException failure(String code, String path, String message)
    {
        return failure(code, path, message, null);
    }

    private static IllegalArgumentException failure(String code, String path, String message, Throwable cause)
    {
        String detail = code + " [" + path + "]: " + message;
        return cause == null ? new IllegalArgumentException(detail) : new IllegalArgumentException(detail, cause);
    }

    private record HeaderEnvelope(Map<String, String> headers, String body)
    {
    }
}
