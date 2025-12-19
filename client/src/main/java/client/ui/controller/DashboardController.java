package client.ui.controller;

import client.network.NetworkClient;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class DashboardController {

    private VBox view;
    private TextField partnerIdField;
    private PasswordField partnerPassField;
    private Button btnShareScreen;
    private ToggleButton btnConnectionMode;

    // Callbacks để báo ngược lại cho ClientApp
    private Runnable onConnectRequest;
    private Runnable onP2PToggle;

    public DashboardController(String myId, String myPass) {
        createView(myId, myPass);
    }

    private void createView(String myId, String myPass) {
        view = new VBox(20);
        view.setPadding(new Insets(20));

        HBox splitBox = new HBox(20);
        VBox.setVgrow(splitBox, Priority.ALWAYS);

        // --- LEFT PANE (INFO) ---
        VBox leftPane = new VBox(20);
        leftPane.getStyleClass().add("left-card");
        leftPane.setPrefWidth(450);
        HBox.setHgrow(leftPane, Priority.ALWAYS);

        Label lblAllow = new Label("Cho phép điều khiển");
        lblAllow.getStyleClass().add("header-blue");

        leftPane.getChildren().addAll(
                lblAllow,
                new Label("Gửi ID/Pass cho đối tác:"),
                new Separator(),
                new VBox(5, new Label("ID của bạn"), createReadOnlyField(myId)),
                new VBox(5, new Label("Mật khẩu"), createReadOnlyField(myPass)));

        // --- RIGHT PANE (CONTROL) ---
        VBox rightPane = new VBox(20);
        rightPane.getStyleClass().add("right-card");
        rightPane.setPrefWidth(450);
        HBox.setHgrow(rightPane, Priority.ALWAYS);

        Label lblControl = new Label("Điều khiển máy khác");
        lblControl.getStyleClass().add("header-orange");

        partnerIdField = new TextField();
        partnerIdField.setPromptText("Nhập ID đối tác");
        partnerIdField.getStyleClass().add("big-input");

        partnerPassField = new PasswordField();
        partnerPassField.setPromptText("Nhập Mật khẩu");
        partnerPassField.getStyleClass().add("big-input");

        // Button Switch Mode
        btnConnectionMode = new ToggleButton("Chế độ: P2P");
        btnConnectionMode.setSelected(true);
        btnConnectionMode.setMaxWidth(Double.MAX_VALUE);
        btnConnectionMode.setStyle(
                "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");

        btnConnectionMode.setOnAction(e -> {
            updateModeButtonStyle(btnConnectionMode.isSelected());
            if (onP2PToggle != null)
                onP2PToggle.run();
        });

        // Button Connect
        btnShareScreen = new Button("Bắt đầu điều khiển");
        btnShareScreen.getStyleClass().add("connect-btn");
        btnShareScreen.setMaxWidth(Double.MAX_VALUE);

        btnShareScreen.setOnAction(e -> {
            if (onConnectRequest != null)
                onConnectRequest.run();
        });

        rightPane.getChildren().addAll(
                lblControl,
                new Label("Nhập thông tin đối tác:"),
                new Separator(),
                new Label("Partner ID"), partnerIdField,
                new Label("Mật khẩu"), partnerPassField,
                new Label("Cấu hình mạng:"), btnConnectionMode,
                new Separator(),
                btnShareScreen);

        splitBox.getChildren().addAll(leftPane, rightPane);
        view.getChildren().add(splitBox);
    }

    public VBox getView() {
        return view;
    }

    // --- GETTERS & SETTERS (Để ClientApp lấy dữ liệu) ---

    public String getTargetId() {
        return partnerIdField.getText().trim();
    }

    public String getTargetPass() {
        return partnerPassField.getText().trim();
    }

    public void setTargetId(String id) {
        partnerIdField.setText(id);
    }

    public boolean isP2PSelected() {
        return btnConnectionMode.isSelected();
    }

    public void setOnConnectRequest(Runnable action) {
        this.onConnectRequest = action;
    }

    public void setOnP2PToggle(Runnable action) {
        this.onP2PToggle = action;
    }

    public void setConnectingState(boolean isConnecting) {
        btnShareScreen.setDisable(isConnecting);
        btnShareScreen.setText(isConnecting ? "Đang kết nối..." : "Bắt đầu điều khiển");
    }

    // --- HELPERS ---
    private TextField createReadOnlyField(String text) {
        TextField tf = new TextField(text);
        tf.setEditable(false);
        tf.getStyleClass().add("big-input");
        tf.setStyle("-fx-background-color: #2d2d30; -fx-text-fill: #87cefa; -fx-font-weight: bold;");
        return tf;
    }

    private void updateModeButtonStyle(boolean isP2P) {
        if (isP2P) {
            btnConnectionMode.setText("Chế độ: P2P");
            btnConnectionMode.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;");
        } else {
            btnConnectionMode.setText("Chế độ: Server Relay");
            btnConnectionMode.setStyle("-fx-background-color: #e67e22; -fx-text-fill: white; -fx-font-weight: bold;");
        }
    }
}