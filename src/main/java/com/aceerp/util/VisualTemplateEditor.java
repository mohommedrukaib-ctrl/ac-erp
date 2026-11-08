package com.aceerp.util;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.view.JasperViewer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Advanced Interactive JRXML Visual Designer.
 * Features:
 *   - Multi-location smart template loader (Classpath + Disk + User folder).
 *   - Live JasperReports Preview window.
 *   - A4 / A5 / Letter Page Size Selector.
 *   - Add Label / Field / Line tools.
 *   - Live text content editing.
 */
public class VisualTemplateEditor {

    public interface TemplateActions {
        void update(String xml);
        byte[] preview(String xml, Integer sampleDocumentId) throws Exception;
        byte[] previewTemplate(Integer sampleDocumentId) throws Exception;
        String loadSelectedTemplate() throws Exception;
        List<String> getDocumentTypes();
        List<String> selectDocument(String documentType);
        void selectTemplate(String templateKey);
        String getDocumentType();
        String getTemplateKey();
        String getSampleDocumentId();
        String getActiveTemplate();
        void setActiveTemplate();
        String save(String xml);
    }

    private static final Logger logger = LoggerFactory.getLogger(VisualTemplateEditor.class);
    private static final String TEMPLATE_DIR = "src/main/resources/reports/";
    private static final String USER_TEMPLATE_DIR = System.getProperty("user.home") + "/.aceerp/templates/";

    private TextArea txtXml;
    private Pane canvas;
    private ComboBox<String> cbTemplates;
    private ComboBox<String> cbPageSize;
    private Button btnSave;
    private TextField txtSampleDocumentId;
    private Label lblActiveTemplate;
    private ComboBox<String> cbReportDocType;
    private ComboBox<String> cbReportTemplate;

    private DraggableBox selectedBox = null;
    private Button btnDelete;

    private TextField txtEditContent;
    private Button btnUpdateContent;

    private String initialTemplateName;
    private String initialTemplateContent;
    private TemplateActions templateActions;

    public static void show() {
        new VisualTemplateEditor().buildAndShow();
    }

    public static void show(String templateName, String templateContent) {
        show(templateName, templateContent, null);
    }

    public static void show(String templateName, String templateContent, TemplateActions actions) {
        if (templateContent == null || templateContent.isBlank()) {
            throw new IllegalArgumentException("Template content must not be blank.");
        }
        VisualTemplateEditor editor = new VisualTemplateEditor();
        editor.initialTemplateName = templateName;
        editor.initialTemplateContent = templateContent;
        editor.templateActions = actions;
        editor.buildAndShow();
    }

    private void buildAndShow() {
        Stage stage = new Stage();
        stage.setTitle("🎨 Advanced Visual Report Template Designer");

        BorderPane root = new BorderPane();
        root.setTop(buildToolbar());
        root.setCenter(buildMainSplit());

        if (initialTemplateContent != null) {
            txtXml.setText(com.aceerp.service.ReportTemplateService
                    .normalizeJrxml(initialTemplateContent));
            btnSave.setDisable(false);
            String content = txtXml.getText();
            if (content.contains("pageWidth=\"420\"")) cbPageSize.setValue("A5 (420x595)");
            else if (content.contains("pageWidth=\"612\"")) cbPageSize.setValue("Letter (612x792)");
            else cbPageSize.setValue("A4 (595x842)");
            drawCanvas();
        }

        Scene scene = new Scene(root, 1400, 850);
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();
    }

    private VBox buildToolbar() {
        VBox topBar = new VBox(8);
        topBar.setPadding(new Insets(10));
        topBar.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #cbd5e1; -fx-border-width: 0 0 1 0;");

        // Row 1: Template Selection + Size + Save As + Preview
        HBox row1 = new HBox(15);
        row1.setAlignment(Pos.CENTER_LEFT);

        cbTemplates = new ComboBox<>();
        cbTemplates.setPromptText(initialTemplateName == null ? "Select Template..." : initialTemplateName);
        cbTemplates.setPrefWidth(260);
        loadAvailableTemplates();
        cbTemplates.setDisable(templateActions != null);
        cbTemplates.setVisible(templateActions == null);
        cbTemplates.setManaged(templateActions == null);

        cbPageSize = new ComboBox<>();
        cbPageSize.getItems().addAll("A4 (595x842)", "A5 (420x595)", "Letter (612x792)");
        cbPageSize.setValue("A4 (595x842)");

        btnSave = new Button("💾 Save As Custom");
        btnSave.setDisable(true);
        btnSave.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-font-weight: bold;");
        if (templateActions != null) {
            btnSave.setText("💾 Save As New Settings Template");
        }

        Button btnRefresh = new Button("🔄 Refresh Canvas");
        btnRefresh.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white;");

        Button btnPreview = new Button("👁️ Preview Report");
        btnPreview.setStyle("-fx-background-color: #f59e0b; -fx-text-fill: white; -fx-font-weight: bold;");
        btnPreview.setOnAction(e -> previewReport());

        if (templateActions == null) {
            row1.getChildren().addAll(new Label("Template:"), cbTemplates);
        }
        row1.getChildren().addAll(new Label("Page Size:"), cbPageSize);
        if (templateActions == null) {
            row1.getChildren().addAll(btnSave, btnRefresh, btnPreview);
        } else {
            row1.getChildren().add(btnRefresh);
        }

        if (templateActions != null) {
            HBox reportContext = new HBox(10);
            reportContext.setAlignment(Pos.CENTER_LEFT);
            cbReportDocType = new ComboBox<>();
            cbReportDocType.getItems().setAll(templateActions.getDocumentTypes());
            cbReportDocType.setValue(templateActions.getDocumentType());
            cbReportDocType.setPrefWidth(150);
            cbReportTemplate = new ComboBox<>();
            cbReportTemplate.setPrefWidth(220);
            cbReportTemplate.getItems().setAll(templateActions.selectDocument(cbReportDocType.getValue()));
            cbReportTemplate.setValue(templateActions.getTemplateKey());
            txtSampleDocumentId = new TextField();
            txtSampleDocumentId.setPromptText("Leave blank = latest saved");
            txtSampleDocumentId.setPrefWidth(200);
            txtSampleDocumentId.setText(templateActions.getSampleDocumentId());
            lblActiveTemplate = new Label(templateActions.getActiveTemplate());
            reportContext.getChildren().addAll(
                    new Label("Document Type"), cbReportDocType,
                    new Label("Template"), cbReportTemplate,
                    new Label("Sample Doc ID (optional)"), txtSampleDocumentId,
                    new Label("Active for this doc"), lblActiveTemplate);
            cbReportDocType.setOnAction(e -> {
                String docType = cbReportDocType.getValue();
                if (docType != null) {
                    cbReportTemplate.getItems().setAll(templateActions.selectDocument(docType));
                    cbReportTemplate.setValue(templateActions.getTemplateKey());
                    lblActiveTemplate.setText(templateActions.getActiveTemplate());
                }
            });
            cbReportTemplate.setOnAction(e -> {
                String templateKey = cbReportTemplate.getValue();
                if (templateKey != null) templateActions.selectTemplate(templateKey);
            });

            HBox reportActions = new HBox(10);
            reportActions.setAlignment(Pos.CENTER_LEFT);
            Button btnSetActive = new Button("Set Active");
            Button btnPreviewTemplate = new Button("Preview");
            Button btnLoadTemplate = new Button("Load JRXML into Editor");
            Button btnPreviewEditor = new Button("Preview Editor JRXML");
            btnSave.setText("Save As New Template…");
            btnSetActive.setOnAction(e -> {
                templateActions.setActiveTemplate();
                lblActiveTemplate.setText(templateActions.getActiveTemplate());
            });
            btnPreviewTemplate.setOnAction(e -> previewSelectedTemplate());
            btnLoadTemplate.setOnAction(e -> loadSelectedTemplate());
            btnPreviewEditor.setOnAction(e -> previewReport());
            reportActions.getChildren().addAll(
                    btnSetActive, btnPreviewTemplate, btnLoadTemplate, btnPreviewEditor, btnSave);
            topBar.getChildren().addAll(row1, reportContext, reportActions);
        } else {
            topBar.getChildren().add(row1);
        }

        // Row 2: Add Tools
        HBox rowTools = new HBox(10);
        rowTools.setAlignment(Pos.CENTER_LEFT);
        Button btnAddLabel = new Button("➕ Add Label / Note");
        Button btnAddField = new Button("➕ Add Field");
        Button btnAddLine = new Button("➕ Add Line");
        
        btnDelete = new Button("🗑️ Delete Selected");
        btnDelete.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white;");
        btnDelete.setDisable(true);

        btnAddLabel.setOnAction(e -> injectNewElement("Label"));
        btnAddField.setOnAction(e -> injectNewElement("Field"));
        btnAddLine.setOnAction(e -> injectNewElement("Line"));
        btnDelete.setOnAction(e -> deleteSelectedElement());
        rowTools.getChildren().addAll(new Label("Add Tools:"), btnAddLabel, btnAddField, btnAddLine, new Region(), btnDelete);

        // Row 3: Live Box Text Content Editor
        HBox rowText = new HBox(10);
        rowText.setAlignment(Pos.CENTER_LEFT);
        txtEditContent = new TextField();
        txtEditContent.setPromptText("Click any box on canvas to view/edit its text or Special Note...");
        txtEditContent.setPrefWidth(550);
        txtEditContent.setStyle("-fx-font-family: Consolas; -fx-font-size: 13px;");

        btnUpdateContent = new Button("📝 Apply Text");
        btnUpdateContent.setStyle("-fx-background-color: #7c3aed; -fx-text-fill: white; -fx-font-weight: bold;");
        btnUpdateContent.setDisable(true);
        btnUpdateContent.setOnAction(e -> applyTextToSelected());
        rowText.getChildren().addAll(new Label("✏️ Box Content:"), txtEditContent, btnUpdateContent);

        topBar.getChildren().addAll(rowTools, rowText);

        cbTemplates.setOnAction(e -> loadTemplate());
        cbPageSize.setOnAction(e -> changePageSize());
        btnSave.setOnAction(e -> saveAsCustomTemplate());
        btnRefresh.setOnAction(e -> drawCanvas());

        return topBar;
    }

    private void loadAvailableTemplates() {
        cbTemplates.getItems().clear();
        
        // Standard built-in list
        List<String> standardTemplates = Arrays.asList(
                "invoice.jrxml", "quotation.jrxml", "sales_order.jrxml",
                "delivery_note.jrxml", "purchase_order.jrxml", "job_note_a5.jrxml",
                "supplier_bill.jrxml", "warranty_certificate.jrxml",
                "amc_renewal_notice.jrxml", "technician_payslip.jrxml"
        );
        cbTemplates.getItems().addAll(standardTemplates);

        // Scan local directory
        try {
            Path dir = Paths.get(TEMPLATE_DIR);
            if (Files.exists(dir)) {
                try (Stream<Path> paths = Files.list(dir)) {
                    paths.filter(p -> p.toString().endsWith(".jrxml")).forEach(p -> {
                        String fileName = p.getFileName().toString();
                        if (!cbTemplates.getItems().contains(fileName)) {
                            cbTemplates.getItems().add(fileName);
                        }
                    });
                }
            }
        } catch (Exception ignored) {}

        // Scan user custom directory (~/.aceerp/templates/)
        try {
            Path userDir = Paths.get(USER_TEMPLATE_DIR);
            if (Files.exists(userDir)) {
                try (Stream<Path> paths = Files.list(userDir)) {
                    paths.filter(p -> p.toString().endsWith(".jrxml")).forEach(p -> {
                        String fileName = p.getFileName().toString();
                        if (!cbTemplates.getItems().contains(fileName)) {
                            cbTemplates.getItems().add(fileName);
                        }
                    });
                }
            }
        } catch (Exception ignored) {}
    }

    private SplitPane buildMainSplit() {
        SplitPane split = new SplitPane();

        VBox leftPane = new VBox(5);
        leftPane.setPadding(new Insets(5));
        txtXml = new TextArea();
        txtXml.setFont(Font.font("Consolas", 12));
        txtXml.setStyle("-fx-control-inner-background: #1e293b; -fx-text-fill: #f8fafc;");
        
        txtXml.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) drawCanvas();
        });

        VBox.setVgrow(txtXml, Priority.ALWAYS);
        leftPane.getChildren().addAll(new Label("JRXML Code (Edit directly and click away to refresh)"), txtXml);

        VBox rightPane = new VBox(5);
        rightPane.setPadding(new Insets(5));

        ScrollPane canvasScroll = new ScrollPane();
        canvas = new Pane();
        canvas.setStyle("-fx-background-color: white; -fx-border-color: #94a3b8; -fx-border-width: 1;");
        canvasScroll.setContent(canvas);
        canvasScroll.setStyle("-fx-background-color: #f1f5f9;");
        VBox.setVgrow(canvasScroll, Priority.ALWAYS);

        canvas.setOnMousePressed(e -> {
            if (e.getTarget() == canvas && selectedBox != null) {
                selectedBox.deselect();
                selectedBox = null;
                btnDelete.setDisable(true);
                txtEditContent.setText("");
                btnUpdateContent.setDisable(true);
            }
        });

        rightPane.getChildren().addAll(new Label("Interactive Canvas"), canvasScroll);
        split.getItems().addAll(leftPane, rightPane);
        split.setDividerPositions(0.32);
        return split;
    }

    private void changePageSize() {
        String xml = txtXml.getText();
        if (xml == null || xml.isBlank()) return;

        String size = cbPageSize.getValue();
        if (size == null) return;
        int w = 595, h = 842;
        if (size.startsWith("A5")) { w = 420; h = 595; }
        else if (size.startsWith("Letter")) { w = 612; h = 792; }

        try {
            xml = setReportAttribute(xml, "pageWidth", w);
            xml = setReportAttribute(xml, "pageHeight", h);
            txtXml.setText(com.aceerp.service.ReportTemplateService.normalizeJrxml(xml));
            drawCanvas();
        } catch (IllegalArgumentException ex) {
            AlertHelper.error("Invalid Page Layout", ex.getMessage());
        }
    }

    private String fetchTemplateContent(String fileName) {
        // 1. Try local project src/main/resources/reports/
        Path srcPath = Paths.get(TEMPLATE_DIR + fileName);
        if (Files.exists(srcPath)) {
            try { return Files.readString(srcPath, StandardCharsets.UTF_8); } catch (Exception ignored) {}
        }

        // 2. Try user custom directory ~/.aceerp/templates/
        Path userPath = Paths.get(USER_TEMPLATE_DIR + fileName);
        if (Files.exists(userPath)) {
            try { return Files.readString(userPath, StandardCharsets.UTF_8); } catch (Exception ignored) {}
        }

        // 3. Try Classpath /reports/
        try (InputStream is = getClass().getResourceAsStream("/reports/" + fileName)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {}

        return null; // Not found anywhere
    }

    private void loadTemplate() {
        String selected = cbTemplates.getValue();
        if (selected == null) return;

        if (!selected.endsWith(".jrxml")) {
            selected += ".jrxml";
        }

        String xml = fetchTemplateContent(selected);
        if (xml != null) {
            txtXml.setText(com.aceerp.service.ReportTemplateService.normalizeJrxml(xml));
        } else {
            // If template doesn't exist anywhere, create a starter A4 template
            String blankTemplate = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<jasperReport xmlns=\"http://jasperreports.sourceforge.net/jasperreports\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://jasperreports.sourceforge.net/jasperreports http://jasperreports.sourceforge.net/xsd/jasperreport.xsd\" name=\"New_Template\" pageWidth=\"595\" pageHeight=\"842\" columnWidth=\"555\" leftMargin=\"20\" rightMargin=\"20\" topMargin=\"20\" bottomMargin=\"20\" uuid=\"" + UUID.randomUUID() + "\">\n" +
                    "\t<title>\n\t\t<band height=\"50\">\n\t\t\t<staticText>\n\t\t\t\t<reportElement x=\"0\" y=\"10\" width=\"300\" height=\"30\" uuid=\"" + UUID.randomUUID() + "\"/>\n\t\t\t\t<text><![CDATA[New Document Template]]></text>\n\t\t\t</staticText>\n\t\t</band>\n\t</title>\n" +
                    "\t<detail>\n\t\t<band height=\"200\"/>\n\t</detail>\n" +
                    "</jasperReport>";
            txtXml.setText(blankTemplate);
        }

        btnSave.setDisable(false);
        
        // Auto-detect page size
        String content = txtXml.getText();
        if (content.contains("pageWidth=\"420\"")) cbPageSize.setValue("A5 (420x595)");
        else if (content.contains("pageWidth=\"612\"")) cbPageSize.setValue("Letter (612x792)");
        else cbPageSize.setValue("A4 (595x842)");

        drawCanvas();
    }

    private void saveAsCustomTemplate() {
        String xml;
        try {
            xml = com.aceerp.service.ReportTemplateService.normalizeJrxml(txtXml.getText());
            txtXml.setText(xml);
        } catch (IllegalArgumentException ex) {
            AlertHelper.error("Save Error", ex.getMessage());
            return;
        }
        if (templateActions != null) {
            templateActions.update(xml);
            String savedTemplate = templateActions.save(xml);
            if (savedTemplate != null && !savedTemplate.isBlank()) {
                initialTemplateName = savedTemplate;
                cbReportTemplate.getItems().setAll(
                        templateActions.selectDocument(cbReportDocType.getValue()));
                cbReportTemplate.setValue(savedTemplate);
                templateActions.selectTemplate(savedTemplate);
                lblActiveTemplate.setText(templateActions.getActiveTemplate());
            }
            return;
        }
        String sourceName = initialTemplateName != null ? initialTemplateName : cbTemplates.getValue();
        if (sourceName == null) return;
        String suggestedName = "custom_" + sourceName.replaceAll("[^a-zA-Z0-9_-]", "_");
        TextInputDialog dialog = new TextInputDialog(suggestedName);
        dialog.setHeaderText("Save edits as a NEW custom template file:");
        dialog.showAndWait().ifPresent(fileName -> {
            if (!fileName.endsWith(".jrxml")) fileName += ".jrxml";
            try {
                try (InputStream in = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
                    JasperCompileManager.compileReport(in);
                }
                Path userDir = Paths.get(USER_TEMPLATE_DIR);
                Files.createDirectories(userDir);
                Path savePath = userDir.resolve(fileName);
                Files.writeString(savePath, xml, StandardCharsets.UTF_8);
                AlertHelper.info("Saved", "Custom template saved to:\n" + savePath.toAbsolutePath());
                loadAvailableTemplates();
                cbTemplates.setValue(fileName);
            } catch (Exception ex) { 
                logger.error("Save Error", ex);
                AlertHelper.error("Save Error", ex.getMessage());
            }
        });
    }

    private void previewReport() {
        String sourceXml = txtXml.getText();
        if (sourceXml == null || sourceXml.isBlank()) {
            AlertHelper.error("Preview Error", "No JRXML code to preview.");
            return;
        }
        final String xml;
        try {
            xml = com.aceerp.service.ReportTemplateService.normalizeJrxml(sourceXml);
            txtXml.setText(xml);
        } catch (IllegalArgumentException ex) {
            AlertHelper.error("Preview Error", ex.getMessage());
            return;
        }
        if (templateActions != null) {
            final Integer sampleDocumentId;
            try {
                sampleDocumentId = getSampleDocumentId();
            } catch (NumberFormatException ex) {
                AlertHelper.error("Invalid Sample Doc ID", "Enter a whole-number document ID or leave it blank.");
                return;
            }
            templateActions.update(xml);
            new Thread(() -> {
                try {
                    byte[] preview = templateActions.preview(xml, sampleDocumentId);
                    Platform.runLater(() -> showPreview(preview));
                } catch (Exception ex) {
                    logger.error("Preview failed", ex);
                    Platform.runLater(() -> AlertHelper.error("Preview Failed",
                            "Could not render report using Settings data:\n" + ex.getMessage()));
                }
            }, "settings-report-preview").start();
            return;
        }
        try {
            InputStream stream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
            JasperReport report = JasperCompileManager.compileReport(stream);
            JasperPrint print = JasperFillManager.fillReport(report, new HashMap<>(), new JREmptyDataSource(1));
            JasperViewer.viewReport(print, false);
        } catch (Exception ex) {
            logger.error("Preview failed", ex);
            AlertHelper.error("Preview Failed", "Could not compile report. Check your XML syntax:\n" + ex.getMessage());
        }
    }

    private void previewSelectedTemplate() {
        final Integer sampleDocumentId;
        try {
            sampleDocumentId = getSampleDocumentId();
        } catch (NumberFormatException ex) {
            AlertHelper.error("Invalid Sample Doc ID", "Enter a whole-number document ID or leave it blank.");
            return;
        }
        new Thread(() -> {
            try {
                byte[] preview = templateActions.previewTemplate(sampleDocumentId);
                Platform.runLater(() -> showPreview(preview));
            } catch (Exception ex) {
                logger.error("Selected template preview failed", ex);
                Platform.runLater(() -> AlertHelper.error("Preview Failed",
                        "Could not preview the selected template:\n" + ex.getMessage()));
            }
        }, "settings-selected-template-preview").start();
    }

    private void loadSelectedTemplate() {
        try {
            String xml = templateActions.loadSelectedTemplate();
            if (xml == null || xml.isBlank()) {
                throw new IllegalStateException("The selected report template is empty.");
            }
            xml = com.aceerp.service.ReportTemplateService.normalizeJrxml(xml);
            txtXml.setText(xml);
            templateActions.update(xml);
            drawCanvas();
        } catch (Exception ex) {
            logger.error("Could not load selected template into visual editor", ex);
            AlertHelper.error("Load Failed", "Could not load the selected template:\n" + ex.getMessage());
        }
    }

    private Integer getSampleDocumentId() {
        if (txtSampleDocumentId == null || txtSampleDocumentId.getText().isBlank()) return null;
        int id = Integer.parseInt(txtSampleDocumentId.getText().trim());
        if (id <= 0) throw new NumberFormatException("Sample document ID must be positive.");
        return id;
    }

    private void showPreview(byte[] preview) {
        ImageView imageView = new ImageView(new Image(new ByteArrayInputStream(preview)));
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(900);
        ScrollPane scrollPane = new ScrollPane(imageView);
        scrollPane.setFitToWidth(true);
        Stage previewStage = new Stage();
        previewStage.setTitle("Report Preview - " + initialTemplateName);
        previewStage.setScene(new Scene(scrollPane, 950, 750));
        previewStage.show();
    }

    private void drawCanvas() {
        canvas.getChildren().clear();
        selectedBox = null;
        btnDelete.setDisable(true);
        txtEditContent.setText("");
        btnUpdateContent.setDisable(true);

        String xml = txtXml.getText();
        if (xml == null || xml.isBlank()) return;

        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setValidating(false);
            dbFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            Element root = doc.getDocumentElement();
            int pageWidth = (int) getDoubleAttr(root, "pageWidth", 595);
            int pageHeight = (int) getDoubleAttr(root, "pageHeight", 842);
            canvas.setPrefSize(pageWidth, pageHeight);

            String[] bandNames = {"title", "pageHeader", "columnHeader", "detail", "columnFooter", "pageFooter", "summary"};
            double currentYOffset = 0;

            for (String bName : bandNames) {
                NodeList bandNodes = doc.getElementsByTagName(bName);
                if (bandNodes.getLength() > 0) {
                    Element bandContainer = (Element) bandNodes.item(0);
                    NodeList bandTag = bandContainer.getElementsByTagName("band");
                    if (bandTag.getLength() > 0) {
                        Element bandEl = (Element) bandTag.item(0);
                        double bandHeight = getDoubleAttr(bandEl, "height", 0);

                        Line sep = new Line(0, currentYOffset, pageWidth, currentYOffset);
                        sep.setStroke(Color.LIGHTGRAY);
                        sep.getStrokeDashArray().addAll(4d, 4d);

                        Label lblBand = new Label(bName + " (H:" + (int) bandHeight + ")");
                        lblBand.setLayoutX(5); lblBand.setLayoutY(currentYOffset + 2);
                        lblBand.setTextFill(Color.GRAY); lblBand.setFont(Font.font("Arial", 9));
                        canvas.getChildren().addAll(sep, lblBand);

                        NodeList elements = bandEl.getElementsByTagName("reportElement");
                        for (int i = 0; i < elements.getLength(); i++) {
                            Element reportEl = (Element) elements.item(i);
                            String uuid = reportEl.getAttribute("uuid");
                            if (uuid == null || uuid.isEmpty()) continue;

                            double x = getDoubleAttr(reportEl, "x", 0);
                            double y = getDoubleAttr(reportEl, "y", 0);
                            double w = getDoubleAttr(reportEl, "width", 100);
                            double h = getDoubleAttr(reportEl, "height", 20);
                            String type = reportEl.getParentNode().getNodeName();

                            canvas.getChildren().add(new DraggableBox(uuid, type, x, y, w, h, currentYOffset, bandHeight));
                        }
                        currentYOffset += bandHeight;
                    }
                }
            }
        } catch (SAXParseException e) {
            logger.warn("Canvas XML parse error: {}", e.getMessage());
            AlertHelper.error("Malformed Template XML", 
                "Syntax error in JRXML code.\nLine " + e.getLineNumber() + ": " + e.getMessage());
        } catch (Exception e) { 
            logger.warn("Canvas error", e); 
        }
    }

    private void injectNewElement(String type) {
        String xml = txtXml.getText();
        int detailIdx = xml.indexOf("<detail>");
        if (detailIdx == -1) { AlertHelper.error("Error", "Template missing <detail> band."); return; }
        int bandIdx = xml.indexOf("<band", detailIdx);
        int bandEnd = xml.indexOf(">", bandIdx);
        if (bandEnd == -1) return;

        String uuid = UUID.randomUUID().toString();
        String newElem = "";

        if ("Label".equals(type)) {
            newElem = "\n\t\t\t<staticText>\n\t\t\t\t<reportElement x=\"0\" y=\"0\" width=\"250\" height=\"20\" uuid=\"" + uuid + "\"/>\n\t\t\t\t<text><![CDATA[Special Note: Free service warranty applied]]></text>\n\t\t\t</staticText>";
        } else if ("Field".equals(type)) {
            newElem = "\n\t\t\t<textField>\n\t\t\t\t<reportElement x=\"0\" y=\"0\" width=\"150\" height=\"20\" uuid=\"" + uuid + "\"/>\n\t\t\t\t<textFieldExpression><![CDATA[\"Dynamic Value\"]]></textFieldExpression>\n\t\t\t</textField>";
        } else if ("Line".equals(type)) {
            newElem = "\n\t\t\t<line>\n\t\t\t\t<reportElement x=\"0\" y=\"0\" width=\"300\" height=\"1\" uuid=\"" + uuid + "\"/>\n\t\t\t</line>";
        }

        if (xml.charAt(bandEnd - 1) == '/') {
            String bandTag = xml.substring(bandIdx, bandEnd);
            int slash = bandTag.lastIndexOf('/');
            String openBand = bandTag.substring(0, slash) + bandTag.substring(slash + 1);
            xml = xml.substring(0, bandIdx) + openBand + ">" + newElem + "</band>" + xml.substring(bandEnd + 1);
        } else {
            xml = xml.substring(0, bandEnd + 1) + newElem + xml.substring(bandEnd + 1);
        }
        txtXml.setText(xml);
        drawCanvas();
    }

    private void deleteSelectedElement() {
        if (selectedBox == null) return;
        String xml = txtXml.getText();
        int uuidIndex = xml.indexOf("uuid=\"" + selectedBox.uuid + "\"");
        if (uuidIndex == -1) return;

        int tagStart = xml.lastIndexOf("<", xml.lastIndexOf("<reportElement", uuidIndex) - 1);
        int closeAngleIdx = xml.indexOf(">", tagStart);
        int spaceIdx = xml.indexOf(" ", tagStart);
        int nameEnd = (spaceIdx != -1 && spaceIdx < closeAngleIdx) ? spaceIdx : closeAngleIdx;
        String tagName = xml.substring(tagStart + 1, nameEnd);

        int tagEnd = xml.indexOf("</" + tagName + ">", uuidIndex);
        if (tagEnd != -1) {
            tagEnd += ("</" + tagName + ">").length();
            txtXml.setText(xml.substring(0, tagStart) + xml.substring(tagEnd));
            drawCanvas();
        }
    }

    private void loadTextFromXml(String uuid) {
        String xml = txtXml.getText();
        int uuidIdx = xml.indexOf("uuid=\"" + uuid + "\"");
        if (uuidIdx == -1) return;

        int tagStart = xml.lastIndexOf("<", xml.lastIndexOf("<reportElement", uuidIdx) - 1);
        int nextElem = xml.indexOf("<reportElement", uuidIdx + 1);
        if (nextElem == -1) nextElem = xml.length();

        int cdataStart = xml.indexOf("<![CDATA[", tagStart);
        if (cdataStart != -1 && cdataStart < nextElem) {
            int cdataEnd = xml.indexOf("]]>", cdataStart);
            if (cdataEnd != -1) {
                txtEditContent.setText(xml.substring(cdataStart + 9, cdataEnd));
                btnUpdateContent.setDisable(false);
                return;
            }
        }
        txtEditContent.setText("");
        btnUpdateContent.setDisable(true);
    }

    private void applyTextToSelected() {
        if (selectedBox == null) return;
        String xml = txtXml.getText();
        int uuidIdx = xml.indexOf("uuid=\"" + selectedBox.uuid + "\"");
        if (uuidIdx == -1) return;

        int tagStart = xml.lastIndexOf("<", xml.lastIndexOf("<reportElement", uuidIdx) - 1);
        int nextElem = xml.indexOf("<reportElement", uuidIdx + 1);
        if (nextElem == -1) nextElem = xml.length();

        int cdataStart = xml.indexOf("<![CDATA[", tagStart);
        if (cdataStart != -1 && cdataStart < nextElem) {
            int cdataEnd = xml.indexOf("]]>", cdataStart);
            if (cdataEnd != -1) {
                String safeCdata = txtEditContent.getText().replace("]]>", "]]]]><![CDATA[>");
                String newXml = xml.substring(0, cdataStart + 9) + safeCdata + xml.substring(cdataEnd);
                txtXml.setText(newXml);
                AlertHelper.info("Updated", "Text content updated!");
                drawCanvas();
            }
        }
    }

    private double getDoubleAttr(Element el, String attr, double def) {
        String val = el.getAttribute(attr);
        try { return (val != null && !val.isEmpty()) ? Double.parseDouble(val) : def; } catch (Exception e) { return def; }
    }

    private void updateXmlCoordinates(String uuid, double newX, double newY) {
        String xml = txtXml.getText();
        int idx = xml.indexOf("uuid=\"" + uuid + "\"");
        if (idx == -1) return;

        int start = xml.lastIndexOf("<reportElement", idx);
        int end = xml.indexOf(">", idx);
        if (start == -1 || end == -1) return;

        String tag = xml.substring(start, end + 1);
        tag = setElementAttribute(tag, "x", (int) Math.round(newX));
        tag = setElementAttribute(tag, "y", (int) Math.round(newY));

        txtXml.setText(xml.substring(0, start) + tag + xml.substring(end + 1));
    }

    private String setReportAttribute(String xml, String attribute, int value) {
        java.util.regex.Matcher rootMatcher = java.util.regex.Pattern
                .compile("<jasperReport\\b[^>]*>", java.util.regex.Pattern.DOTALL)
                .matcher(xml);
        if (!rootMatcher.find()) {
            throw new IllegalArgumentException("JRXML must contain a <jasperReport> root element.");
        }
        String rootTag = setElementAttribute(rootMatcher.group(), attribute, value);
        return xml.substring(0, rootMatcher.start()) + rootTag + xml.substring(rootMatcher.end());
    }

    private String setElementAttribute(String tag, String attribute, int value) {
        java.util.regex.Pattern attributePattern = java.util.regex.Pattern.compile(
                "(\\b" + java.util.regex.Pattern.quote(attribute) + "\\s*=\\s*\")[^\"]*(\")");
        java.util.regex.Matcher matcher = attributePattern.matcher(tag);
        if (matcher.find()) {
            return matcher.replaceFirst(java.util.regex.Matcher.quoteReplacement(
                    matcher.group(1) + value + matcher.group(2)));
        }
        int insertAt = tag.length() - 1;
        while (insertAt > 0 && Character.isWhitespace(tag.charAt(insertAt - 1))) insertAt--;
        if (insertAt > 0 && tag.charAt(insertAt - 1) == '/') insertAt--;
        return tag.substring(0, insertAt) + " " + attribute + "=\"" + value + "\"" + tag.substring(insertAt);
    }

    private class DraggableBox extends StackPane {
        private double dragX, dragY;
        private final String uuid;
        private final double bandOffset, bandHeight, initialHeight;
        private final Label lbl;

        public DraggableBox(String uuid, String type, double x, double y, double w, double h, double bandOffset, double bandHeight) {
            this.uuid = uuid; this.bandOffset = bandOffset; this.bandHeight = bandHeight; this.initialHeight = h;
            setLayoutX(x); setLayoutY(y + bandOffset); setPrefSize(w, h);

            lbl = new Label(type);
            lbl.setFont(Font.font("Arial", 9));
            getChildren().add(lbl);
            deselect();

            setOnMousePressed(e -> {
                if (selectedBox != null) selectedBox.deselect();
                selectedBox = this;
                select();
                btnDelete.setDisable(false);
                loadTextFromXml(this.uuid);

                javafx.geometry.Point2D pointer = canvas.sceneToLocal(e.getSceneX(), e.getSceneY());
                dragX = pointer.getX() - getLayoutX();
                dragY = pointer.getY() - getLayoutY();
                toFront();
                e.consume();
            });

            setOnMouseDragged(e -> {
                javafx.geometry.Point2D pointer = canvas.sceneToLocal(e.getSceneX(), e.getSceneY());
                double maxX = Math.max(0, canvas.getPrefWidth() - getWidth());
                double newX = Math.max(0, Math.min(pointer.getX() - dragX, maxX));
                double rawY = pointer.getY() - dragY;
                double localY = rawY - this.bandOffset;
                double maxLocalY = Math.max(0, this.bandHeight - this.initialHeight);
                localY = Math.max(0, Math.min(localY, maxLocalY));

                setLayoutX(newX);
                setLayoutY(localY + this.bandOffset);
                e.consume();
            });

            setOnMouseReleased(e -> {
                updateXmlCoordinates(this.uuid, getLayoutX(), getLayoutY() - this.bandOffset);
                e.consume();
            });
        }

        public void select() {
            setStyle("-fx-border-color: #dc2626; -fx-border-style: solid; -fx-border-width: 2; -fx-background-color: rgba(220, 38, 38, 0.2);");
            lbl.setTextFill(Color.DARKRED);
        }

        public void deselect() {
            setStyle("-fx-border-color: #2563eb; -fx-border-style: dashed; -fx-background-color: rgba(37, 99, 235, 0.12);");
            lbl.setTextFill(Color.web("#1e3a8a"));
        }
    }
}