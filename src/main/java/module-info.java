module com.breachinthecontainment.launcher {
    requires javafx.controls;
    requires javafx.fxml;

    opens org.breachinthecontainment.launcher_client to javafx.fxml;
    exports org.breachinthecontainment.launcher_client;
}
