package com.aceerp.util;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Advanced Interactive JRXML Visual Designer.
 * Features:
 *   - Maximized multi-pane window.
 *   - A4 / A5 / Letter Page Size Selector.
 *   - Add Label / Field / Line tools.
 *   - Live text content editing.
 */
public class VisualTemplateEditor {

    private static final Logger logger = LoggerFactory.getLogger(VisualTemplateEditor.class);
    private static final String TEMPLATE_DIR = "src/main/resources/reports/";

    private TextArea txtXml;
    private Pane canvas;
    private ComboBox<String> cbTemplates;
    private ComboBox<String> cbPageSize;
    private Button btnSave;

    private DraggableBox selectedBox = null;
    private Button btnDelete;

    private TextField txtEditContent;
    private Button btnUpdateContent;

    public static void show() {
        new VisualTemplateEditor().buildAndShow();
    }

    private void buildAndShow() {
        Stage stage = new Stage();
        stage.setTitle("🎨 Advanced Visual Report Template Designer");

        BorderPane root = new BorderPane();
        root.setTop(buildToolbar());
        root.setCenter(buildMainSplit());

        Scene scene = new Scene(root, 1400, 850);
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();
    }

    private VBox buildToolbar() {
        VBox topBar = new VBox(8);
        topBar.setPadding(new Insets(10));
        topBar.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #cbd5e1; -fx-border-width: 0 0 1 0;");

        // Row 1: Template Selection + Size + Save As
        HBox row1 = new HBox(15);
        row1.setAlignment(Pos.CENTER_LEFT);

        cbTemplates = new ComboBox<>();
        cbTemplates.getItems().addAll("warranty_certificate.jrxml", "amc_renewal_notice.jrxml", "technician_payslip.jrxml", "invoice.jrxml", "job_note_a5.jrxml");
        cbTemplates.setPromptText("Select Template...");
        cbTemplates.setPrefWidth(260);

        cbPageSize = new ComboBox<>();
        cbPageSize.getItems().addAll("A4 (595x842)", "A5 (420x595)", "Letter (612x792)");
        cbPageSize.setValue("A4 (595x842)");

        btnSave = new Button("💾 Save As Custom");
        btnSave.setDisable(true);
        btnSave.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-font-weight: bold;");

        Button btnRefresh = new Button("🔄 Refresh Canvas");
        btnRefresh.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white;");

        row1.getChildren().addAll(new Label("Template:"), cbTemplates, new Label("Page Size:"), cbPageSize, btnSave, btnRefresh);

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

        topBar.getChildren().addAll(row1, rowTools, rowText);

        cbTemplates.setOnAction(e -> loadTemplate());
        cbPageSize.setOnAction(e -> changePageSize());
        btnSave.setOnAction(e -> saveAsCustomTemplate());
        btnRefresh.setOnAction(e -> drawCanvas());

        return topBar;
    }

    private SplitPane buildMainSplit() {
        SplitPane split = new SplitPane();

        VBox leftPane = new VBox(5);
        leftPane.setPadding(new Insets(5));
        txtXml = new TextArea();
        txtXml.setFont(Font.font("Consolas", 12));
        txtXml.setStyle("-fx-control-inner-background: #1e293b; -fx-text-fill: #f8fafc;");
        VBox.setVgrow(txtXml, Priority.ALWAYS);
        leftPane.getChildren().addAll(new Label("JRXML Code"), txtXml);

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
        int w = 595, h = 842;
        if (size.startsWith("A5")) { w = 420; h = 595; }
        else if (size.startsWith("Letter")) { w = 612; h = 792; }

        xml = xml.replaceAll("pageWidth=\"\\d+\"", "pageWidth=\"" + w + "\"");
        xml = xml.replaceAll("pageHeight=\"\\d+\"", "pageHeight=\"" + h + "\"");
        txtXml.setText(xml);
        drawCanvas();
    }

    private void loadTemplate() {
        String selected = cbTemplates.getValue();
        if (selected == null) return;
        try {
            Path path = Paths.get(TEMPLATE_DIR + selected);
            if (Files.exists(path)) {
                String xml = Files.readString(path);
                txtXml.setText(xml);
                btnSave.setDisable(false);
                
                // Auto-detect page size from loaded XML
                if (xml.contains("pageWidth=\"420\"")) cbPageSize.setValue("A5 (420x595)");
                else if (xml.contains("pageWidth=\"612\"")) cbPageSize.setValue("Letter (612x792)");
                else cbPageSize.setValue("A4 (595x842)");

                drawCanvas();
            }
        } catch (Exception ex) {
            logger.error("Failed to load", ex);
        }
    }

    private void saveAsCustomTemplate() {
        if (cbTemplates.getValue() == null) return;
        TextInputDialog dialog = new TextInputDialog("custom_" + cbTemplates.getValue());
        dialog.setHeaderText("Save edits as a NEW custom template file:");
        dialog.showAndWait().ifPresent(fileName -> {
            if (!fileName.endsWith(".jrxml")) fileName += ".jrxml";
            try {
                Files.writeString(Paths.get(TEMPLATE_DIR + fileName), txtXml.getText());
                AlertHelper.info("Saved", "Custom template created!");
                if (!cbTemplates.getItems().contains(fileName)) cbTemplates.getItems().add(fileName);
                cbTemplates.setValue(fileName);
            } catch (Exception ex) { logger.error("Save Error", ex); }
        });
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
            Document doc = dBuilder.parse(new ByteArrayInputStream(xml.getBytes()));

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
        } catch (Exception e) { logger.warn("Canvas error", e); }
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

        txtXml.setText(xml.substring(0, bandEnd + 1) + newElem + xml.substring(bandEnd + 1));
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
                String newXml = xml.substring(0, cdataStart + 9) + txtEditContent.getText() + xml.substring(cdataEnd);
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
        tag = tag.replaceAll("\\bx=\"\\d+\"", "x=\"" + (int) newX + "\"");
        tag = tag.replaceAll("\\by=\"\\d+\"", "y=\"" + (int) newY + "\"");

        txtXml.setText(xml.substring(0, start) + tag + xml.substring(end + 1));
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

                dragX = e.getSceneX() - getLayoutX();
                dragY = e.getSceneY() - getLayoutY();
                toFront();
                e.consume();
            });

            setOnMouseDragged(e -> {
                double newX = Math.max(0, e.getSceneX() - dragX);
                double rawY = e.getSceneY() - dragY;
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