package com.shambanexa.implementation;

import com.shambanexa.common.ApiException;
import org.springframework.http.HttpStatus;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

final class MasterWorkbook {

    private MasterWorkbook() {
    }

    static byte[] csv(List<String> headers, List<List<String>> rows) {
        StringBuilder out = new StringBuilder();
        out.append(line(headers));
        for (List<String> row : rows) {
            out.append(line(row));
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    static byte[] xlsx(List<String> headers, List<List<String>> rows) {
        List<List<String>> all = new ArrayList<>();
        all.add(headers);
        all.addAll(rows);
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(raw)) {
                entry(zip, "[Content_Types].xml", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                        <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                        <Default Extension="xml" ContentType="application/xml"/>
                        <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                        <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                        </Types>
                        """);
                entry(zip, "_rels/.rels", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                        </Relationships>
                        """);
                entry(zip, "xl/workbook.xml", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                        <sheets><sheet name="Master data" sheetId="1" r:id="rId1"/></sheets>
                        </workbook>
                        """);
                entry(zip, "xl/_rels/workbook.xml.rels", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                        </Relationships>
                        """);
                entry(zip, "xl/worksheets/sheet1.xml", sheetXml(all));
            }
            return raw.toByteArray();
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "EXPORT_FAILED", "The spreadsheet could not be prepared");
        }
    }

    static List<List<String>> read(String filename, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "Choose a file that contains rows");
        }
        if (bytes.length > 1_000_000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", "Keep the file under 1 MB");
        }
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".xlsx") || (bytes.length > 1 && bytes[0] == 'P' && bytes[1] == 'K')) {
                return readXlsx(bytes);
            }
            if (name.endsWith(".csv") || name.endsWith(".txt") || name.isBlank()) {
                return readCsv(new String(bytes, StandardCharsets.UTF_8));
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNREADABLE_FILE", "That file could not be read. Use CSV or Excel .xlsx");
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_FILE", "Use a CSV or Excel .xlsx file");
    }

    private static List<List<String>> readCsv(String text) {
        String source = text.startsWith("\uFEFF") ? text.substring(1) : text;
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < source.length() && source.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (ch == '\n') {
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else if (ch != '\r') {
                cell.append(ch);
            }
        }
        if (quoted) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BROKEN_CSV", "A quoted value in the CSV was not closed");
        }
        if (!cell.isEmpty() || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }

    private static List<List<String>> readXlsx(byte[] bytes) throws Exception {
        Map<String, byte[]> parts = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    parts.put(entry.getName(), zip.readAllBytes());
                }
            }
        }
        byte[] sheet = parts.get("xl/worksheets/sheet1.xml");
        if (sheet == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNREADABLE_FILE", "The workbook has no first sheet");
        }
        List<String> shared = sharedStrings(parts.get("xl/sharedStrings.xml"));
        Document document = xml(sheet);
        List<List<String>> rows = new ArrayList<>();
        NodeList rowNodes = document.getElementsByTagName("*");
        for (int i = 0; i < rowNodes.getLength(); i++) {
            Node node = rowNodes.item(i);
            if (!"row".equals(node.getLocalName()) || !(node instanceof Element rowElement)) {
                continue;
            }
            int width = 0;
            List<Cell> cells = new ArrayList<>();
            NodeList children = rowElement.getChildNodes();
            for (int c = 0; c < children.getLength(); c++) {
                Node child = children.item(c);
                if (!"c".equals(child.getLocalName()) || !(child instanceof Element cell)) {
                    continue;
                }
                int index = columnIndex(cell.getAttribute("r"));
                width = Math.max(width, index + 1);
                cells.add(new Cell(index, cellText(cell, shared)));
            }
            List<String> values = new ArrayList<>();
            for (int n = 0; n < width; n++) {
                values.add("");
            }
            for (Cell cell : cells) {
                if (cell.index >= 0 && cell.index < values.size()) {
                    values.set(cell.index, cell.value);
                }
            }
            rows.add(values);
        }
        return rows;
    }

    private static List<String> sharedStrings(byte[] raw) throws Exception {
        List<String> values = new ArrayList<>();
        if (raw == null) {
            return values;
        }
        Document document = xml(raw);
        NodeList items = document.getElementsByTagName("*");
        for (int i = 0; i < items.getLength(); i++) {
            Node node = items.item(i);
            if (!"si".equals(node.getLocalName())) {
                continue;
            }
            StringBuilder text = new StringBuilder();
            NodeList inner = node.getChildNodes();
            collectText(inner, text);
            values.add(text.toString());
        }
        return values;
    }

    private static void collectText(NodeList nodes, StringBuilder text) {
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if ("t".equals(node.getLocalName())) {
                text.append(node.getTextContent());
            } else if (node.hasChildNodes()) {
                collectText(node.getChildNodes(), text);
            }
        }
    }

    private static String cellText(Element cell, List<String> shared) {
        String type = cell.getAttribute("t");
        if ("inlineStr".equals(type)) {
            StringBuilder text = new StringBuilder();
            collectText(cell.getChildNodes(), text);
            return text.toString();
        }
        String value = directValue(cell);
        if ("s".equals(type)) {
            try {
                int index = Integer.parseInt(value.trim());
                return index >= 0 && index < shared.size() ? shared.get(index) : "";
            } catch (NumberFormatException ex) {
                return "";
            }
        }
        return value;
    }

    private static String directValue(Element cell) {
        NodeList children = cell.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if ("v".equals(node.getLocalName())) {
                return node.getTextContent() == null ? "" : node.getTextContent();
            }
        }
        return "";
    }

    private static int columnIndex(String reference) {
        int index = 0;
        for (int i = 0; i < reference.length(); i++) {
            char ch = reference.charAt(i);
            if (ch >= 'A' && ch <= 'Z') {
                index = index * 26 + (ch - 'A' + 1);
            } else if (ch >= 'a' && ch <= 'z') {
                index = index * 26 + (ch - 'a' + 1);
            } else {
                break;
            }
        }
        return Math.max(0, index - 1);
    }

    private static Document xml(byte[] raw) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(raw));
    }

    private static String sheetXml(List<List<String>> rows) {
        StringBuilder xml = new StringBuilder();
        xml.append("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
                """);
        for (int r = 0; r < rows.size(); r++) {
            xml.append("<row r=\"").append(r + 1).append("\">");
            List<String> row = rows.get(r);
            for (int c = 0; c < row.size(); c++) {
                xml.append("<c r=\"").append(columnName(c)).append(r + 1).append("\" t=\"inlineStr\"><is><t>")
                        .append(escape(row.get(c)))
                        .append("</t></is></c>");
            }
            xml.append("</row>");
        }
        xml.append("</sheetData></worksheet>");
        return xml.toString();
    }

    private static String columnName(int index) {
        int value = index + 1;
        StringBuilder name = new StringBuilder();
        while (value > 0) {
            value--;
            name.insert(0, (char) ('A' + (value % 26)));
            value /= 26;
        }
        return name.toString();
    }

    private static void entry(ZipOutputStream zip, String name, String xml) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(xml.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String line(List<String> cells) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                row.append(',');
            }
            row.append(escapeCsv(cells.get(i)));
        }
        row.append('\n');
        return row.toString();
    }

    private static String escapeCsv(String value) {
        String text = value == null ? "" : value;
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static String escape(String value) {
        return (value == null ? "" : value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private record Cell(int index, String value) {
    }
}
