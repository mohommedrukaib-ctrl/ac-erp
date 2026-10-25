package com.aceerp;

/**
 * Application launcher.
 * 
 * WHY DO WE NEED THIS?
 * JavaFX applications that use the Java module system can have issues 
 * when the main class directly extends javafx.application.Application.
 * 
 * The solution is simple: create a plain Java class (Launcher) that 
 * calls the actual Application class (App). This is a standard pattern
 * recommended by the JavaFX community.
 * 
 * This is the class that Maven's javafx-maven-plugin will call.
 */
public class Launcher {

    public static void main(String[] args) {
        App.main(args);
    }
}