package com.aceerp.service;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.CompanyProfile;
import net.sf.jasperreports.engine.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

public class ReportTemplateService {

    private static final Logger logger = LoggerFactory.getLogger(ReportTemplateService.class);

    public static final List<String> BUILTIN_THEMES = List.of("classic", "modern", "professional");

    public static final List<String> DOC_TYPES = List.of(
            "invoice",
            "quotation",
            "sales_order",
            "delivery_note",
            "purchase_order",
            "job_note_a5",
            "supplier_bill"
    );

    private static final Map<String, String> ID_PARAM = Map.of(
            "invoice", "INVOICE_ID",
            "quotation", "QUOTE_ID",
            "sales_order", "ORDER_ID",
            "delivery_note", "DN_ID",
            "purchase_order", "PO_ID",
            "job_note_a5", "JOB_ID",
            "supplier_bill", "BILL_ID"
    );

    private static final Map<String, String> LATEST_ID_SQL = Map.of(
            "invoice", "SELECT id FROM invoices ORDER BY id DESC LIMIT 1",
            "quotation", "SELECT id FROM quotations ORDER BY id DESC LIMIT 1",
            "sales_order", "SELECT id FROM sales_orders ORDER BY id DESC LIMIT 1",
            "delivery_note", "SELECT id FROM delivery_notes ORDER BY id DESC LIMIT 1",
            "purchase_order", "SELECT id FROM purchase_orders ORDER BY id DESC LIMIT 1",
            "job_note_a5", "SELECT id FROM service_jobs ORDER BY id DESC LIMIT 1",
            "supplier_bill", "SELECT id FROM supplier_bills ORDER BY id DESC LIMIT 1"
    );

    private final CompanyProfileService companyProfileService = new CompanyProfileService();
    private final SettingsService settingsService = SettingsService.getInstance();

    public static final class VisualStyle {
        public String headerColor = "#1e40af";
        public String accentColor = "#3b82f6";
        public String textColor = "#111827";
        public int marginTop = 40;
        public int marginBottom = 40;
        public int marginLeft = 40;
        public int marginRight = 40;
        public int titleFontSize = 16;
        public int bodyFontSize = 10;
        public boolean showLogo = true;
        public boolean showTagline = true;
        public boolean showTaxNo = true;
        public boolean showWebsite = true;
        public boolean showFooter = true;
    }

    public static class ReportElementItem {
        public String key;
        public String label;
        public String tag;
        public String band;
        public int x;
        public int y;
        public int width;
        public int height;
        public int bandOffsetY;
        public int bandHeight; // <--- NEW FIELD added for clamping

        @Override
        public String toString() {
            return label + " (" + x + ", " + y + " " + width + "x" + height + ")";
        }
    }

    public Path getCustomRoot() throws IOException {
        Path root = Paths.get(System.getProperty("user.home"), ".aceerp", "templates");
        Files.createDirectories(root);
        return root;
    }

    public Path getCustomDocDir(String docType) throws IOException {
        Path dir = getCustomRoot().resolve(docType);
        Files.createDirectories(dir);
        return dir;
    }

    public List<String> listTemplatesFor(String docType) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String theme : BUILTIN_THEMES) {
            if (builtinExists(theme, docType)) {
                keys.add(theme);
            }
        }
        try {
            Path dir = getCustomDocDir(docType);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.jrxml")) {
                for (Path p : stream) {
                    String name = p.getFileName().toString().replaceFirst("\\.jrxml$", "");
                    keys.add("custom:" + name);
                }
            }
        } catch (IOException e) {
            logger.warn("Could not list custom templates for {}: {}", docType, e.getMessage());
        }
        return new ArrayList<>(keys);
    }

    public boolean isBuiltin(String templateKey) {
        if (templateKey == null) return true;
        String k = templateKey.toLowerCase(Locale.ROOT);
        return !k.startsWith("custom:") && BUILTIN_THEMES.contains(k);
    }

    public String getActiveTemplateKey(String docType) {
        String perDoc = settingsService.getSetting("print.template." + docType, null);
        if (perDoc != null && !perDoc.isBlank()) {
            return perDoc;
        }
        CompanyProfile c = companyProfileService.getProfile();
        if (c != null && c.getDefaultPrintTemplate() != null && !c.getDefaultPrintTemplate().isBlank()) {
            return c.getDefaultPrintTemplate().toLowerCase(Locale.ROOT);
        }
        return settingsService.getSetting("PRINT_THEME", "classic").toLowerCase(Locale.ROOT);
    }

    public void setActiveTemplateKey(String docType, String templateKey) {
        settingsService.setSetting("print.template." + docType, templateKey);
    }

    public String loadJrxmlText(String docType, String templateKey) throws IOException {
        if (templateKey != null && templateKey.toLowerCase(Locale.ROOT).startsWith("custom:")) {
            String name = templateKey.substring("custom:".length()).trim();
            Path file = getCustomDocDir(docType).resolve(name + ".jrxml");
            if (!Files.exists(file)) {
                throw new FileNotFoundException("Custom template not found: " + file);
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        }

        String theme = templateKey == null ? "classic" : templateKey.toLowerCase(Locale.ROOT);
        String resourcePath = "/reports/" + theme + "/" + docType + ".jrxml";

        InputStream stream = ReportTemplateService.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            stream = ReportTemplateService.class.getResourceAsStream("/reports/classic/" + docType + ".jrxml");
        }
        if (stream == null) {
            stream = ReportTemplateService.class.getResourceAsStream("/reports/" + docType + ".jrxml");
        }
        if (stream == null) {
            throw new FileNotFoundException("Built-in template not found for " + docType + " / " + theme);
        }

        try (InputStream is = stream) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public String saveAsNewCustomTemplate(String docType, String newName, String jrxmlContent) throws Exception {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("Template name is required.");
        }
        String safe = newName.trim().replaceAll("[^a-zA-Z0-9_\\- ]", "").replace(' ', '_');
        if (safe.isBlank()) {
            throw new IllegalArgumentException("Invalid template name.");
        }
        if (BUILTIN_THEMES.contains(safe.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Name conflicts with a built-in theme. Choose another name.");
        }

        try (InputStream in = new ByteArrayInputStream(jrxmlContent.getBytes(StandardCharsets.UTF_8))) {
            JasperCompileManager.compileReport(in);
        }

        Path target = getCustomDocDir(docType).resolve(safe + ".jrxml");
        if (Files.exists(target)) {
            throw new IllegalArgumentException("A custom template named '" + safe + "' already exists. Choose a new name.");
        }
        Files.writeString(target, jrxmlContent, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

        return "custom:" + safe;
    }

    public JasperReport compileTemplate(String docType, String templateKey) throws Exception {
        String jrxml = loadJrxmlText(docType, templateKey);
        try (InputStream in = new ByteArrayInputStream(jrxml.getBytes(StandardCharsets.UTF_8))) {
            return JasperCompileManager.compileReport(in);
        }
    }

    public JasperReport compileJrxmlText(String jrxmlContent) throws Exception {
        try (InputStream in = new ByteArrayInputStream(jrxmlContent.getBytes(StandardCharsets.UTF_8))) {
            return JasperCompileManager.compileReport(in);
        }
    }

    public Integer findLatestDocumentId(String docType) {
        String sql = LATEST_ID_SQL.get(docType);
        if (sql == null) return null;
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) {
            logger.warn("No sample id for {}: {}", docType, e.getMessage());
        }
        return null;
    }

    public String idParamName(String docType) {
        return ID_PARAM.getOrDefault(docType, "ID");
    }

    public Map<String, Object> buildCompanyParameters() {
        CompanyProfile c = companyProfileService.getProfile();
        Map<String, Object> p = new HashMap<>();
        p.put("COMPANY_NAME", c.getCompanyName() != null ? c.getCompanyName() : "");
        p.put("COMPANY_TAGLINE", c.getTagline() != null ? c.getTagline() : "");
        p.put("COMPANY_ADDRESS", c.getFullAddress());
        p.put("COMPANY_PHONE", c.getPhone() != null ? c.getPhone() : "");
        p.put("COMPANY_EMAIL", c.getEmail() != null ? c.getEmail() : "");
        p.put("COMPANY_WEBSITE", c.getWebsite() != null ? c.getWebsite() : "");
        p.put("COMPANY_TAX_NO", c.getTaxNumber() != null ? c.getTaxNumber() : "");
        p.put("COMPANY_FOOTER", c.getInvoiceFooter() != null ? c.getInvoiceFooter() : "");
        p.put("COMPANY_LOGO", companyProfileService.resolveLogoAbsolutePath(c.getLogoPath()));
        return p;
    }

    public byte[] renderPreviewPng(String docType, String templateKey, Integer documentId, float zoom) throws Exception {
        JasperReport report = compileTemplate(docType, templateKey);
        return fillToPng(report, docType, documentId, zoom);
    }

    public byte[] renderPreviewPngFromText(String docType, String jrxmlText, Integer documentId, float zoom) throws Exception {
        JasperReport report = compileJrxmlText(jrxmlText);
        return fillToPng(report, docType, documentId, zoom);
    }

    private byte[] fillToPng(JasperReport report, String docType, Integer documentId, float zoom) throws Exception {
        Integer id = documentId;
        if (id == null || id <= 0) {
            id = findLatestDocumentId(docType);
        }
        if (id == null || id <= 0) {
            throw new IllegalStateException("No saved " + docType + " found to preview. Create one document first, then preview.");
        }

        Map<String, Object> params = buildCompanyParameters();
        params.put(idParamName(docType), id);

        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            JasperPrint print = JasperFillManager.fillReport(report, params, conn);
            if (print.getPages() == null || print.getPages().isEmpty()) {
                throw new IllegalStateException("Report produced no pages (check query / sample id).");
            }
            BufferedImage bi = (BufferedImage) JasperPrintManager.printPageToImage(print, 0, zoom <= 0 ? 1.5f : zoom);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bi, "png", baos);
            return baos.toByteArray();
        }
    }

    private boolean builtinExists(String theme, String docType) {
        String path = "/reports/" + theme + "/" + docType + ".jrxml";
        try (InputStream in = ReportTemplateService.class.getResourceAsStream(path)) {
            return in != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** Resolve stream/path used at print time. Prefer per-doc active key. */
    public InputStream openActiveTemplateStream(String docType) throws IOException {
        String key = getActiveTemplateKey(docType);
        if (key != null && key.toLowerCase(Locale.ROOT).startsWith("custom:")) {
            String name = key.substring("custom:".length()).trim();
            Path file = getCustomDocDir(docType).resolve(name + ".jrxml");
            if (Files.exists(file)) {
                return Files.newInputStream(file);
            }
            logger.warn("Active custom template missing {}, falling back to theme", file);
        }

        String theme = (key != null && !key.toLowerCase(Locale.ROOT).startsWith("custom:"))
                ? key.toLowerCase(Locale.ROOT)
                : settingsService.getSetting("PRINT_THEME", "classic").toLowerCase(Locale.ROOT);

        InputStream stream = ReportTemplateService.class.getResourceAsStream("/reports/" + theme + "/" + docType + ".jrxml");
        if (stream == null) {
            stream = ReportTemplateService.class.getResourceAsStream("/reports/classic/" + docType + ".jrxml");
        }
        if (stream == null) {
            stream = ReportTemplateService.class.getResourceAsStream("/reports/" + docType + ".jrxml");
        }
        if (stream == null) {
            throw new FileNotFoundException("No template for docType=" + docType);
        }
        return stream;
    }

    // ---- DOM-based JRXML Parser & Layout Patcher ----

    public List<ReportElementItem> parseReportElements(String jrxml) {
        List<ReportElementItem> items = new ArrayList<>();
        try {
            Document doc = parseDom(jrxml);
            NodeList bands = doc.getElementsByTagName("band");

            int cumulativeBandY = 0;
            for (int i = 0; i < bands.getLength(); i++) {
                Element band = (Element) bands.item(i);
                int bandHeight = parseInt(band.getAttribute("height"), 0);
                String bandName = band.getParentNode() != null ? band.getParentNode().getNodeName() : "band";

                NodeList children = band.getChildNodes();
                for (int j = 0; j < children.getLength(); j++) {
                    if (children.item(j) instanceof Element elem) {
                        String tagName = elem.getNodeName();
                        if (List.of("image", "textField", "staticText", "rectangle", "line").contains(tagName)) {
                            Element reportElem = getSingleChild(elem, "reportElement");
                            if (reportElem != null) {
                                ReportElementItem item = new ReportElementItem();
                                item.tag = tagName;
                                item.band = bandName;
                                item.x = parseInt(reportElem.getAttribute("x"), 0);
                                item.y = parseInt(reportElem.getAttribute("y"), 0);
                                item.width = parseInt(reportElem.getAttribute("width"), 100);
                                item.height = parseInt(reportElem.getAttribute("height"), 20);
                                item.bandOffsetY = cumulativeBandY;
                                item.bandHeight = bandHeight; // <--- SETTING THE VALUE

                                String text = elem.getTextContent();
                                if (text.contains("$P{COMPANY_LOGO}")) { item.key = "COMPANY_LOGO"; item.label = "Company Logo"; }
                                else if (text.contains("$P{COMPANY_NAME}")) { item.key = "COMPANY_NAME"; item.label = "Company Name"; }
                                else if (text.contains("$P{COMPANY_TAGLINE}")) { item.key = "COMPANY_TAGLINE"; item.label = "Tagline"; }
                                else if (text.contains("$P{COMPANY_ADDRESS}")) { item.key = "COMPANY_ADDRESS"; item.label = "Address Block"; }
                                else if (text.contains("$P{COMPANY_FOOTER}")) { item.key = "COMPANY_FOOTER"; item.label = "Footer"; }
                                else if (text.contains("total_amount") || text.contains("Total:")) { item.key = "SUMMARY_TOTAL"; item.label = "Summary Total"; }
                                else {
                                    item.key = bandName + "_" + tagName + "_" + j;
                                    item.label = bandName + " " + tagName + " (" + item.x + "," + item.y + ")";
                                }
                                items.add(item);
                            }
                        }
                    }
                }
                cumulativeBandY += bandHeight;
            }
        } catch (Exception e) {
            logger.warn("Failed to parse report elements: {}", e.getMessage());
        }
        return items;
    }


    public String updateElementBounds(String jrxml, String elementKey, int x, int y, int width, int height) throws Exception {
        Document doc = parseDom(jrxml);
        NodeList bands = doc.getElementsByTagName("band");

        for (int i = 0; i < bands.getLength(); i++) {
            Element band = (Element) bands.item(i);
            NodeList children = band.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                if (children.item(j) instanceof Element elem) {
                    String text = elem.getTextContent();
                    boolean matches = (elementKey.equals("COMPANY_LOGO") && text.contains("$P{COMPANY_LOGO}"))
                            || (elementKey.equals("COMPANY_NAME") && text.contains("$P{COMPANY_NAME}"))
                            || (elementKey.equals("COMPANY_TAGLINE") && text.contains("$P{COMPANY_TAGLINE}"))
                            || (elementKey.equals("COMPANY_ADDRESS") && text.contains("$P{COMPANY_ADDRESS}"))
                            || (elementKey.equals("COMPANY_FOOTER") && text.contains("$P{COMPANY_FOOTER}"))
                            || (elementKey.equals("SUMMARY_TOTAL") && (text.contains("total_amount") || text.contains("Total:")));

                    if (matches) {
                        Element reportElem = getSingleChild(elem, "reportElement");
                        if (reportElem != null) {
                            reportElem.setAttribute("x", String.valueOf(x));
                            reportElem.setAttribute("y", String.valueOf(y));
                            reportElem.setAttribute("width", String.valueOf(width));
                            reportElem.setAttribute("height", String.valueOf(height));
                            return domToString(doc);
                        }
                    }
                }
            }
        }
        return jrxml;
    }

    public String applyVisualStyle(String jrxml, VisualStyle style) throws Exception {
        Document doc = parseDom(jrxml);
        Element root = doc.getDocumentElement();

        // Page Margins
        root.setAttribute("topMargin", String.valueOf(style.marginTop));
        root.setAttribute("bottomMargin", String.valueOf(style.marginBottom));
        root.setAttribute("leftMargin", String.valueOf(style.marginLeft));
        root.setAttribute("rightMargin", String.valueOf(style.marginRight));

        // Schema-compliant Visibility Toggling
        toggleParamVisibilityDom(doc, "$P{COMPANY_LOGO}", style.showLogo);
        toggleParamVisibilityDom(doc, "$P{COMPANY_TAGLINE}", style.showTagline);
        toggleParamVisibilityDom(doc, "$P{COMPANY_TAX_NO}", style.showTaxNo);
        toggleParamVisibilityDom(doc, "$P{COMPANY_WEBSITE}", style.showWebsite);
        toggleParamVisibilityDom(doc, "$P{COMPANY_FOOTER}", style.showFooter);

        return domToString(doc);
    }

        private void toggleParamVisibilityDom(Document doc, String paramToken, boolean show) {
        NodeList elements = doc.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element elem = (Element) elements.item(i);
            String name = elem.getNodeName();
            if ("image".equals(name) || "textField".equals(name)) {
                if (elem.getTextContent().contains(paramToken)) {
                    Element reportElem = getSingleChild(elem, "reportElement");
                    if (reportElem != null) {
                        Element printWhen = getSingleChild(reportElem, "printWhenExpression");
                        String exprText = show ? "Boolean.TRUE" : "Boolean.FALSE";

                        if (printWhen != null) {
                            // Jasper requires CDATA inside printWhenExpression
                            while (printWhen.hasChildNodes()) {
                                printWhen.removeChild(printWhen.getFirstChild());
                            }
                            printWhen.appendChild(doc.createCDATASection(exprText));
                        } else {
                            Element newPrintWhen = doc.createElement("printWhenExpression");
                            newPrintWhen.appendChild(doc.createCDATASection(exprText));

                            // Strict XSD Ordering: MUST go after <property> tags, inside <reportElement>
                            Node insertRef = reportElem.getFirstChild();
                            while (insertRef != null && (insertRef.getNodeName().equals("property") || insertRef.getNodeName().equals("propertyExpression"))) {
                                insertRef = insertRef.getNextSibling();
                            }
                            
                            if (insertRef != null) {
                                reportElem.insertBefore(newPrintWhen, insertRef);
                            } else {
                                reportElem.appendChild(newPrintWhen);
                            }
                        }
                    }
                }
            }
        }
    }
    private Document parseDom(String jrxml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(new InputSource(new StringReader(jrxml)));
    }

    private String domToString(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }

    private Element getSingleChild(Element parent, String childName) {
        NodeList list = parent.getElementsByTagName(childName);
        if (list.getLength() > 0) return (Element) list.item(0);
        return null;
    }

    private int parseInt(String val, int fallback) {
        try {
            return Integer.parseInt(val);
        } catch (Exception e) {
            return fallback;
        }
    }
}