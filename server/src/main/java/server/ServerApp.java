package server;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.OutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ServerApp extends Application {

    private TextArea logArea;
    private TableView<ClientModel> clientTable;
    public static ObservableList<ClientModel> connectedClients = FXCollections.observableArrayList();

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1e1e1e;"); // Dark mode

        // --- 1. HEADER ---
        Label lblTitle = new Label("Remote Control SERVER (Relay & P2P Broker)");
        lblTitle.setStyle("-fx-text-fill: #00a8ff; -fx-font-size: 20px; -fx-font-weight: bold; -fx-padding: 10;");
        root.setTop(lblTitle);

        // --- 2. CENTER: DANH SÁCH CLIENT ---
        clientTable = new TableView<>();
        clientTable.setItems(connectedClients);
        clientTable.setStyle("-fx-background-color: #2d2d2d; -fx-text-fill: white;");

        TableColumn<ClientModel, String> colId = new TableColumn<>("Client ID");
        colId.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getId()));
        colId.setPrefWidth(150);

        TableColumn<ClientModel, String> colIp = new TableColumn<>("IP Address");
        colIp.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getIp()));
        colIp.setPrefWidth(150);

        TableColumn<ClientModel, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatus()));
        colStatus.setPrefWidth(150);

        clientTable.getColumns().addAll(colId, colIp, colStatus);

        VBox centerBox = new VBox(10, new Label("Connected Clients:"), clientTable);
        centerBox.setPadding(new Insets(10));
        ((Label) centerBox.getChildren().get(0)).setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        root.setCenter(centerBox);

        // --- 3. BOTTOM: LOGS ---
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(200);
        logArea.setStyle("-fx-control-inner-background: #000; -fx-text-fill: #0f0; -fx-font-family: 'Consolas';");

        VBox bottomBox = new VBox(5, new Label("System Logs:"), logArea);
        bottomBox.setPadding(new Insets(10));
        ((Label) bottomBox.getChildren().get(0)).setStyle("-fx-text-fill: white;");
        root.setBottom(bottomBox);

        // --- 4. REDIRECT SYSTEM.OUT ---
        redirectSystemOut();

        // --- 5. START SERVER ---
        new Thread(() -> {
            try {
                // Gọi hàm main cũ của Server, hoặc khởi tạo Server instance
                new RemoteControlServer(8080).start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        Scene scene = new Scene(root, 800, 600);
        primaryStage.setTitle("Server Monitor");
        primaryStage.setScene(scene);
        primaryStage.show();

        primaryStage.setOnCloseRequest(e -> {
            Platform.exit();
            System.exit(0);
        });
    }

    // Class model cho bảng
    public static class ClientModel {
        private String id;
        private String ip;
        private String status;

        public ClientModel(String id, String ip, String status) {
            this.id = id;
            this.ip = ip;
            this.status = status;
        }

        public String getId() {
            return id;
        }

        public String getIp() {
            return ip;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String s) {
            this.status = s;
        }
    }

    // Hàm chuyển System.out vào TextArea
    private void redirectSystemOut() {
        OutputStream out = new OutputStream() {
            @Override
            public void write(int b) {
                appendText(String.valueOf((char) b));
            }

            @Override
            public void write(byte[] b, int off, int len) {
                appendText(new String(b, off, len));
            }
        };
        System.setOut(new PrintStream(out, true));
        System.setErr(new PrintStream(out, true));
    }

    private void appendText(String str) {
        Platform.runLater(() -> logArea.appendText(str));
    }

    public static void main(String[] args) {
        launch(args);
    }
}