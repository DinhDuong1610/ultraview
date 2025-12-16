package server.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import server.RemoteControlServer; // Import class server cũ (chứa hàm main/start)
import server.model.ClientModel; // Import Model mới

import java.io.OutputStream;
import java.io.PrintStream;

public class ServerApp extends Application {

    private TextArea logArea;
    private TableView<ClientModel> clientTable;

    // Danh sách dữ liệu (ObservableList) để Binding vào bảng
    // Để static để ServerContext có thể gọi update được
    private static final ObservableList<ClientModel> connectedClients = FXCollections.observableArrayList();

    /**
     * Hàm static để cập nhật giao diện từ bất kỳ đâu (ServerContext, Handler...)
     * 
     * @param client Đối tượng ClientModel chứa thông tin
     * @param isAdd  true = Thêm/Cập nhật, false = Xóa
     */
    public static void updateClientList(ClientModel client, boolean isAdd) {
        Platform.runLater(() -> {
            // Luôn xóa client cũ có cùng ID trước để tránh trùng lặp
            connectedClients.removeIf(c -> c.getId().equals(client.getId()));

            if (isAdd) {
                connectedClients.add(client);
            }
        });
    }

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1e1e1e;"); // Dark mode theme

        // --- 1. HEADER (Tiêu đề) ---
        Label lblTitle = new Label("Remote Control SERVER (Relay & P2P Broker)");
        lblTitle.setStyle("-fx-text-fill: #00a8ff; -fx-font-size: 20px; -fx-font-weight: bold; -fx-padding: 10;");
        root.setTop(lblTitle);

        // --- 2. CENTER: DANH SÁCH CLIENT (Bảng) ---
        clientTable = new TableView<>();
        clientTable.setItems(connectedClients); // Liên kết bảng với danh sách dữ liệu
        clientTable.setStyle("-fx-background-color: #2d2d2d; -fx-text-fill: white;");
        // CSS placeholder khi bảng rỗng
        clientTable.setPlaceholder(new Label("Waiting for connections..."));

        // Cột 1: Client ID
        TableColumn<ClientModel, String> colId = new TableColumn<>("Client ID");
        colId.setCellValueFactory(data -> data.getValue().idProperty()); // Dùng Property của JavaFX
        colId.setPrefWidth(150);

        // Cột 2: IP Address
        TableColumn<ClientModel, String> colIp = new TableColumn<>("IP Address");
        colIp.setCellValueFactory(data -> data.getValue().ipProperty());
        colIp.setPrefWidth(150);

        // Cột 3: Status
        TableColumn<ClientModel, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(data -> data.getValue().statusProperty());
        colStatus.setPrefWidth(150);

        clientTable.getColumns().addAll(colId, colIp, colStatus);

        VBox centerBox = new VBox(10, new Label("Connected Clients:"), clientTable);
        centerBox.setPadding(new Insets(10));
        ((Label) centerBox.getChildren().get(0)).setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        root.setCenter(centerBox);

        // --- 3. BOTTOM: LOGS (Nhật ký hệ thống) ---
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(200);
        logArea.setStyle("-fx-control-inner-background: #000; -fx-text-fill: #0f0; -fx-font-family: 'Consolas';");

        VBox bottomBox = new VBox(5, new Label("System Logs:"), logArea);
        bottomBox.setPadding(new Insets(10));
        ((Label) bottomBox.getChildren().get(0)).setStyle("-fx-text-fill: white;");
        root.setBottom(bottomBox);

        // --- 4. REDIRECT SYSTEM.OUT (Chuyển hướng System.out vào TextArea) ---
        redirectSystemOut();

        // --- 5. START SERVER (Chạy Server trong luồng riêng) ---
        new Thread(() -> {
            try {
                // Khởi động Server Netty trên port 8080 (TCP) và 8081 (UDP)
                // Lưu ý: Đảm bảo class RemoteControlServer đã được cập nhật logic Handler mới
                new RemoteControlServer(8080).run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        // --- 6. SETUP SCENE ---
        Scene scene = new Scene(root, 800, 600);
        primaryStage.setTitle("Server Monitor - UltraViewer Clone");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Tắt toàn bộ khi đóng cửa sổ
        primaryStage.setOnCloseRequest(e -> {
            Platform.exit();
            System.exit(0);
        });
    }

    // --- CÁC HÀM PHỤ TRỢ (HELPER) ---

    // Hàm chuyển hướng System.out và System.err vào TextArea
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

    // Hàm append text an toàn với luồng JavaFX
    private void appendText(String str) {
        Platform.runLater(() -> {
            if (logArea != null) {
                logArea.appendText(str);
            }
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}