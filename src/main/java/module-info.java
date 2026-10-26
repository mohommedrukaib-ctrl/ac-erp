module com.aceerp {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires java.sql;
    requires java.desktop;
    requires com.zaxxer.hikari;
    requires org.slf4j;
    requires jbcrypt;
    requires jasperreports;

    opens com.aceerp to javafx.fxml;
    opens com.aceerp.controller to javafx.fxml;
    opens com.aceerp.model to javafx.base, javafx.fxml;

    exports com.aceerp;
    exports com.aceerp.controller;
    exports com.aceerp.model;
    exports com.aceerp.service;
    exports com.aceerp.util;
}