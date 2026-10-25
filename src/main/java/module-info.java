module com.aceerp {

    requires javafx.controls;
    requires javafx.fxml;

    requires java.sql;
    requires com.zaxxer.hikari;

    requires org.slf4j;
    requires jbcrypt;

    opens com.aceerp to javafx.fxml;
    opens com.aceerp.controller to javafx.fxml;
    opens com.aceerp.model to javafx.fxml;

    exports com.aceerp;
    exports com.aceerp.config;
    exports com.aceerp.model;
    exports com.aceerp.service;
    exports com.aceerp.util;
}