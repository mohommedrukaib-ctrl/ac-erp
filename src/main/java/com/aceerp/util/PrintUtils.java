package com.aceerp.util;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PrinterJob;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class PrintUtils {

    public static void showPrintPreviewDialog(String title, TableView<?> table) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Print Preview - " + title);

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: white;");

        Label header = new Label(title);
        header.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        VBox contentBox = new VBox(10);
        contentBox.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1px; -fx-padding: 15; -fx-background-color: white;");
        
        Label docLabel = new Label("ACE ERP - Document Preview");
        docLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        contentBox.getChildren().add(docLabel);

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(8);
        grid.setStyle("-fx-padding: 10 0 10 0;");

        // Column Headers
        int colIdx = 0;
        for (TableColumn<?, ?> col : table.getColumns()) {
            Label colHeader = new Label(col.getText());
            colHeader.setStyle("-fx-font-weight: bold; -fx-border-color: #333; -fx-border-width: 0 0 1 0;");
            grid.add(colHeader, colIdx++, 0);
        }

        // Data Rows
        int rowIdx = 1;
        for (Object item : table.getItems()) {
            colIdx = 0;
            for (TableColumn<?, ?> col : table.getColumns()) {
                Object cellVal = col.getCellData(rowIdx - 1);
                Label valLabel = new Label(cellVal != null ? cellVal.toString() : "");
                grid.add(valLabel, colIdx++, rowIdx);
            }
            rowIdx++;
        }

        contentBox.getChildren().add(grid);

        ScrollPane scrollPane = new ScrollPane(contentBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefSize(600, 450);

        HBox btnBox = new HBox(15);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        Button btnPrint = new Button("Confirm Print");
        btnPrint.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-weight: bold;");
        btnPrint.setOnAction(e -> {
            PrinterJob job = PrinterJob.createPrinterJob();
            if (job != null && job.showPrintDialog(stage.getOwner())) {
                boolean success = job.printPage(contentBox);
                if (success) {
                    job.endJob();
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Print Complete");
                    alert.setHeaderText(null);
                    alert.setContentText("Document sent to printer successfully.");
                    alert.showAndWait();
                    stage.close();
                } else {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Print Failed");
                    alert.setHeaderText(null);
                    alert.setContentText("Could not send job to printer.");
                    alert.showAndWait();
                }
            }
        });

        Button btnClose = new Button("Close");
        btnClose.setOnAction(e -> stage.close());

        btnBox.getChildren().addAll(btnPrint, btnClose);
        root.getChildren().addAll(header, scrollPane, btnBox);

        Scene scene = new Scene(root, 650, 580);
        stage.setScene(scene);
        stage.showAndWait();
    }
}