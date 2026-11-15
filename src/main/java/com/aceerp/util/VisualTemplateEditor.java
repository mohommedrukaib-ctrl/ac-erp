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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringWriter;
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
    private CheckBox chkShowDescription;
    private CheckBox chkShowAmountInWords;

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
            syncDescriptionToggle(content);
            syncAmountInWordsToggle(content);
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
        chkShowDescription = new CheckBox("Show Description column");
        chkShowDescription.setVisible(false);
        chkShowDescription.setManaged(false);
        chkShowDescription.setOnAction(e -> updateBooleanParameterDefault(
                "SHOW_DESCRIPTION", chkShowDescription.isSelected()));
        chkShowAmountInWords = new CheckBox("Show amount in words");
        chkShowAmountInWords.setVisible(false);
        chkShowAmountInWords.setManaged(false);
        chkShowAmountInWords.setOnAction(e -> updateBooleanParameterDefault(
                "SHOW_AMOUNT_IN_WORDS", chkShowAmountInWords.isSelected()));
        
        btnDelete = new Button("🗑️ Delete Selected");
        btnDelete.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white;");
        btnDelete.setDisable(true);

        btnAddLabel.setOnAction(e -> injectNewElement("Label"));
        btnAddField.setOnAction(e -> injectNewElement("Field"));
        btnAddLine.setOnAction(e -> injectNewElement("Line"));
        btnDelete.setOnAction(e -> deleteSelectedElement());
        rowTools.getChildren().addAll(
                new Label("Add Tools:"), btnAddLabel, btnAddField, btnAddLine,
                chkShowDescription, chkShowAmountInWords, new Region(), btnDelete);

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
                "classic/invoice.jrxml", "classic/quotation.jrxml", "classic/sales_order.jrxml",
                "classic/delivery_note.jrxml", "classic/purchase_order.jrxml", "classic/job_note_a5.jrxml",
                "classic/supplier_bill.jrxml", "classic/grn.jrxml", "warranty_certificate.jrxml",
                "amc_renewal_notice.jrxml", "technician_payslip.jrxml"
        );
        cbTemplates.getItems().addAll(standardTemplates);

        // Scan local directory
        try {
            Path dir = Paths.get(TEMPLATE_DIR);
            if (Files.exists(dir)) {
                try (Stream<Path> paths = Files.walk(dir)) {
                    paths.filter(p -> p.toString().endsWith(".jrxml")).forEach(p -> {
                        String fileName = dir.relativize(p).toString().replace(File.separatorChar, '/');
                        if (!cbTemplates.getItems().contains(fileName)) {
                            cbTemplates.getItems().add(fileName);
                        }
                    });
                }
            }
        } catch (Exception ex) {
            logger.warn("Could not scan built-in report templates in {}", TEMPLATE_DIR, ex);
        }

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
        } catch (Exception ex) {
            logger.warn("Could not scan user report templates in {}", USER_TEMPLATE_DIR, ex);
        }
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

    private String fetchTemplateContent(String fileName) throws java.io.IOException {
        // 1. Try local project src/main/resources/reports/
        Path srcPath = Paths.get(TEMPLATE_DIR).resolve(fileName);
        if (Files.exists(srcPath)) {
            return Files.readString(srcPath, StandardCharsets.UTF_8);
        }

        // 2. Try user custom directory ~/.aceerp/templates/
        Path userPath = Paths.get(USER_TEMPLATE_DIR).resolve(fileName);
        if (Files.exists(userPath)) {
            return Files.readString(userPath, StandardCharsets.UTF_8);
        }

        // 3. Try Classpath /reports/
        try (InputStream is = getClass().getResourceAsStream("/reports/" + fileName)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (java.io.IOException ex) {
            throw ex;
        }

        return null; // Not found anywhere
    }

    private void loadTemplate() {
        String selected = cbTemplates.getValue();
        if (selected == null) return;

        if (!selected.endsWith(".jrxml")) {
            selected += ".jrxml";
        }

        final String xml;
        try {
            xml = fetchTemplateContent(selected);
        } catch (java.io.IOException ex) {
            logger.error("Could not read report template {}", selected, ex);
            AlertHelper.error("Template Load Failed", "Could not read " + selected + ":\n" + ex.getMessage());
            return;
        }
        if (xml != null) {
            try {
                txtXml.setText(com.aceerp.service.ReportTemplateService.normalizeJrxml(xml));
            } catch (IllegalArgumentException ex) {
                logger.error("Could not normalize report template {}", selected, ex);
                AlertHelper.error("Template Load Failed", "Could not load " + selected + ":\n" + ex.getMessage());
                return;
            }
        } else {
            AlertHelper.error("Template Not Found", "Could not find " + selected + " in the project resources or user template folder.");
            return;
        }

        btnSave.setDisable(false);
        syncDescriptionToggle(txtXml.getText());
        syncAmountInWordsToggle(txtXml.getText());
        
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
            String safeFileName = fileName.trim();
            if (!safeFileName.endsWith(".jrxml")) safeFileName += ".jrxml";
            if (!safeFileName.matches("[A-Za-z0-9_-]+\\.jrxml")) {
                AlertHelper.error("Save Error", "Template names may contain only letters, numbers, underscores, and hyphens.");
                return;
            }
            try {
                try (InputStream in = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
                    JasperCompileManager.compileReport(in);
                }
                Path userDir = Paths.get(USER_TEMPLATE_DIR);
                Files.createDirectories(userDir);
                Path savePath = userDir.resolve(safeFileName).normalize();
                if (!savePath.startsWith(userDir.normalize())) {
                    throw new IllegalArgumentException("Template path must stay inside the user template folder.");
                }
                Files.writeString(savePath, xml, StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE);
                AlertHelper.info("Saved", "Custom template saved to:\n" + savePath.toAbsolutePath());
                loadAvailableTemplates();
                cbTemplates.setValue(safeFileName);
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
            syncDescriptionToggle(xml);
            syncAmountInWordsToggle(xml);
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
        syncDescriptionToggle(xml);
        syncAmountInWordsToggle(xml);

        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setValidating(false);
            dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbFactory.setXIncludeAware(false);
            dbFactory.setExpandEntityReferences(false);
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
            logger.error("Could not render the visual template canvas", e);
            AlertHelper.error("Canvas Refresh Failed", "Could not display the template canvas:\n" + e.getMessage());
        }
    }

    private void syncDescriptionToggle(String xml) {
        if (chkShowDescription == null) return;
        java.util.regex.Matcher parameter = java.util.regex.Pattern.compile(
                "(?s)(<parameter\\b(?=[^>]*\\bname\\s*=\\s*['\"]SHOW_DESCRIPTION['\"])[^>]*>)(.*?)(</parameter>)")
                .matcher(xml);
        boolean available = parameter.find();
        chkShowDescription.setManaged(available);
        chkShowDescription.setVisible(available);
        if (!available) return;

        java.util.regex.Matcher defaultValue = java.util.regex.Pattern.compile(
                "(?s)<defaultValueExpression\\b[^>]*>\\s*<!\\[CDATA\\[(.*?)\\]\\]>\\s*</defaultValueExpression>")
                .matcher(parameter.group(2));
        chkShowDescription.setSelected(!defaultValue.find()
                || "Boolean.TRUE".equals(defaultValue.group(1).trim()));
    }

    private void syncAmountInWordsToggle(String xml) {
        if (chkShowAmountInWords == null) return;
        java.util.regex.Matcher parameter = findBooleanParameter(xml, "SHOW_AMOUNT_IN_WORDS");
        boolean available = parameter.find();
        chkShowAmountInWords.setManaged(available);
        chkShowAmountInWords.setVisible(available);
        if (available) chkShowAmountInWords.setSelected(readBooleanDefault(parameter.group(2)));
    }

    private java.util.regex.Matcher findBooleanParameter(String xml, String name) {
        return java.util.regex.Pattern.compile(
                "(?s)(<parameter\\b(?=[^>]*\\bname\\s*=\\s*['\"]" +
                        java.util.regex.Pattern.quote(name) + "['\"])[^>]*>)(.*?)(</parameter>)")
                .matcher(xml);
    }

    private boolean readBooleanDefault(String parameterBody) {
        java.util.regex.Matcher defaultValue = java.util.regex.Pattern.compile(
                "(?s)<defaultValueExpression\\b[^>]*>\\s*<!\\[CDATA\\[(.*?)\\]\\]>\\s*</defaultValueExpression>")
                .matcher(parameterBody);
        return !defaultValue.find() || "Boolean.TRUE".equals(defaultValue.group(1).trim());
    }

    private void updateBooleanParameterDefault(String name, boolean value) {
        String xml = txtXml.getText();
        java.util.regex.Pattern parameterPattern = java.util.regex.Pattern.compile(
                "(?s)(<parameter\\b(?=[^>]*\\bname\\s*=\\s*['\"]" +
                        java.util.regex.Pattern.quote(name) + "['\"])[^>]*>)(.*?)(</parameter>)");
        java.util.regex.Matcher parameter = parameterPattern.matcher(xml);
        if (!parameter.find()) return;

        String body = parameter.group(2);
        String expression = "<defaultValueExpression><![CDATA[Boolean." +
                (value ? "TRUE" : "FALSE") + "]]></defaultValueExpression>";
        java.util.regex.Matcher existing = java.util.regex.Pattern.compile(
                "(?s)<defaultValueExpression\\b[^>]*>.*?</defaultValueExpression>").matcher(body);
        String updatedBody = existing.find()
                ? existing.replaceFirst(java.util.regex.Matcher.quoteReplacement(expression))
                : body + expression;
        String updatedParameter = parameter.group(1) + updatedBody + parameter.group(3);
        String updatedXml = xml.substring(0, parameter.start()) + updatedParameter + xml.substring(parameter.end());
        txtXml.setText(updatedXml);
        if (templateActions != null) templateActions.update(updatedXml);
        btnSave.setDisable(false);
    }

    private void injectNewElement(String type) {
        try {
            Document document = parseEditorDocument(txtXml.getText());
            Element detail = findFirstElement(document, "detail");
            Element band = detail == null ? null : findFirstElement(detail, "band");
            if (band == null) {
                AlertHelper.error("Cannot Add Element", "Template missing a <detail><band> section.");
                return;
            }

            String tagName = switch (type) {
                case "Label" -> "staticText";
                case "Field" -> "textField";
                case "Line" -> "line";
                default -> throw new IllegalArgumentException("Unsupported element type: " + type);
            };
            Element newElement = document.createElement(tagName);
            Element reportElement = document.createElement("reportElement");
            reportElement.setAttribute("x", "0");
            reportElement.setAttribute("y", "0");
            reportElement.setAttribute("width", "Line".equals(type) ? "300" : "200");
            reportElement.setAttribute("height", "Line".equals(type) ? "1" : "20");
            reportElement.setAttribute("uuid", UUID.randomUUID().toString());
            newElement.appendChild(reportElement);

            if ("Label".equals(type)) {
                Element text = document.createElement("text");
                text.appendChild(document.createTextNode("New label"));
                newElement.appendChild(text);
            } else if ("Field".equals(type)) {
                Element expression = document.createElement("textFieldExpression");
                expression.appendChild(document.createCDATASection("\"Dynamic Value\""));
                newElement.appendChild(expression);
            }
            band.appendChild(newElement);
            txtXml.setText(serializeEditorDocument(document));
            btnSave.setDisable(false);
            drawCanvas();
        } catch (Exception ex) {
            logger.error("Could not add an element to the visual template", ex);
            AlertHelper.error("Cannot Add Element", "Could not update the template:\n" + ex.getMessage());
        }
    }

    private void deleteSelectedElement() {
        if (selectedBox == null) return;
        try {
            Document document = parseEditorDocument(txtXml.getText());
            Element reportElement = findReportElement(document, selectedBox.uuid);
            if (reportElement == null || reportElement.getParentNode() == null) return;
            reportElement.getParentNode().getParentNode().removeChild(reportElement.getParentNode());
            txtXml.setText(serializeEditorDocument(document));
            btnSave.setDisable(false);
            drawCanvas();
        } catch (Exception ex) {
            logger.error("Could not delete the selected visual template element", ex);
            AlertHelper.error("Delete Failed", "Could not remove the selected element:\n" + ex.getMessage());
        }
    }

    private void loadTextFromXml(String uuid) {
        try {
            Document document = parseEditorDocument(txtXml.getText());
            Element reportElement = findReportElement(document, uuid);
            Node content = reportElement == null ? null : findEditableContent(reportElement.getParentNode());
            if (content != null) {
                txtEditContent.setText(content.getTextContent());
                btnUpdateContent.setDisable(false);
            } else {
                txtEditContent.setText("");
                btnUpdateContent.setDisable(true);
            }
        } catch (Exception ex) {
            logger.error("Could not read the selected element's text", ex);
            AlertHelper.error("Text Read Failed", "Could not read the selected element:\n" + ex.getMessage());
        }
    }

    private void applyTextToSelected() {
        if (selectedBox == null) return;
        try {
            Document document = parseEditorDocument(txtXml.getText());
            Element reportElement = findReportElement(document, selectedBox.uuid);
            Node content = reportElement == null ? null : findEditableContent(reportElement.getParentNode());
            if (content == null) {
                AlertHelper.error("Text Update Failed", "The selected element does not contain editable text.");
                return;
            }
            content.setTextContent(txtEditContent.getText());
            txtXml.setText(serializeEditorDocument(document));
            btnSave.setDisable(false);
            drawCanvas();
        } catch (Exception ex) {
            logger.error("Could not update the selected element's text", ex);
            AlertHelper.error("Text Update Failed", "Could not update the selected element:\n" + ex.getMessage());
        }
    }

    private double getDoubleAttr(Element el, String attr, double def) {
        String val = el.getAttribute(attr);
        try { return (val != null && !val.isEmpty()) ? Double.parseDouble(val) : def; } catch (Exception e) { return def; }
    }

    private void updateXmlCoordinates(String uuid, double newX, double newY) {
        try {
            Document document = parseEditorDocument(txtXml.getText());
            Element reportElement = findReportElement(document, uuid);
            if (reportElement == null) return;
            reportElement.setAttribute("x", Integer.toString((int) Math.round(newX)));
            reportElement.setAttribute("y", Integer.toString((int) Math.round(newY)));
            txtXml.setText(serializeEditorDocument(document));
            btnSave.setDisable(false);
        } catch (Exception ex) {
            logger.error("Could not save the selected element's position", ex);
            AlertHelper.error("Position Update Failed", "Could not save the element position:\n" + ex.getMessage());
        }
    }

    private Document parseEditorDocument(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private String serializeEditorDocument(Document document) throws Exception {
        var transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }

    private Element findReportElement(Document document, String uuid) {
        NodeList elements = document.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            Node node = elements.item(i);
            if (node instanceof Element element && "reportElement".equals(localName(element))
                    && uuid.equals(element.getAttribute("uuid"))) {
                return element;
            }
        }
        return null;
    }

    private Element findFirstElement(Node parent, String name) {
        if (parent instanceof Element element && name.equals(localName(element))) return element;
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Element found = findFirstElement(children.item(i), name);
            if (found != null) return found;
        }
        return null;
    }

    private String localName(Element element) {
        String name = element.getTagName();
        int colon = name.lastIndexOf(':');
        return colon < 0 ? name : name.substring(colon + 1);
    }

    private Node findEditableContent(Node parent) {
        if (parent == null) return null;
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element
                    && ("text".equals(localName(element)) || "textFieldExpression".equals(localName(element)))) {
                return child;
            }
        }
        return null;
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