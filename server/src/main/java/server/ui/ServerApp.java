package server.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import server.RemoteControlServer;
import server.model.ClientModel;

import java.io.OutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ServerApp extends Application {

    private TextArea logArea;
    private TableView<ClientModel> clientTable;
    private TextField txtPort;
    private Button btnStart, btnStop;
    private Label lblStatusIndicator;
    private Label lblClientCount;

    private static final ObservableList<ClientModel> connectedClients = FXCollections.observableArrayList();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    private Thread serverThread;

    public static void updateClientList(ClientModel client, boolean isAdd) {
        Platform.runLater(() -> {
            connectedClients.removeIf(c -> c.getId().equals(client.getId()));
            if (isAdd) {
                connectedClients.add(client);
            }
        });
    }

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1e1e1e;");

        VBox topContainer = new VBox(10);
        topContainer.setPadding(new Insets(15));
        topContainer.setStyle(
                "-fx-background-color: #252526; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 10, 0, 0, 5);");

        // Header Title
        // Label lblTitle = new Label("Server");
        // lblTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        // lblTitle.setTextFill(Color.web("#00a8ff"));

        // Control Bar (Port, Start, Stop, Status)
        HBox controlBar = createControlBar();

        topContainer.getChildren().addAll(controlBar);
        root.setTop(topContainer);

        VBox centerBox = new VBox(10);
        centerBox.setPadding(new Insets(15));

        HBox statsBox = new HBox(15);
        statsBox.setAlignment(Pos.CENTER_LEFT);
        Label lblListTitle = new Label("Connected Clients");
        lblListTitle.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;");

        lblClientCount = new Label("Total: 0");
        lblClientCount.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 14px;");
        connectedClients.addListener((javafx.collections.ListChangeListener<ClientModel>) c -> Platform
                .runLater(() -> lblClientCount.setText("Total: " + connectedClients.size())));

        statsBox.getChildren().addAll(lblListTitle, lblClientCount);

        createModernTable();

        centerBox.getChildren().addAll(statsBox, clientTable);
        root.setCenter(centerBox);

        VBox bottomBox = new VBox(5);
        bottomBox.setPadding(new Insets(10, 15, 15, 15));

        Label lblLog = new Label("System Logs");
        lblLog.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(180);
        logArea.setStyle(
                "-fx-control-inner-background: #000; -fx-text-fill: #00ff00; -fx-font-family: 'Consolas'; -fx-highlight-fill: #004400; -fx-highlight-text-fill: #ffffff;");

        bottomBox.getChildren().addAll(lblLog, logArea);
        root.setBottom(bottomBox);

        redirectSystemOut();

        Scene scene = new Scene(root, 900, 650);
        scene.getStylesheets().add("data:text/css," + getInlineCSS());

        primaryStage.setTitle("Server");
        primaryStage.setScene(scene);
        primaryStage.show();

        primaryStage.setOnCloseRequest(e -> {
            Platform.exit();
            System.exit(0);
        });
    }

    private HBox createControlBar() {
        HBox box = new HBox(15);
        box.setAlignment(Pos.CENTER_LEFT);

        Label lblPort = new Label("PORT:");
        lblPort.setStyle("-fx-text-fill: #dddddd; -fx-font-weight: bold;");

        txtPort = new TextField("8080");
        txtPort.setPrefWidth(80);
        txtPort.setStyle(
                "-fx-background-color: #3e3e42; -fx-text-fill: white; -fx-border-color: #555; -fx-border-radius: 3;");

        btnStart = new Button("START SERVER");
        btnStart.getStyleClass().add("btn-start");
        btnStart.setOnAction(e -> startServer());

        btnStop = new Button("STOP");
        btnStop.getStyleClass().add("btn-stop");
        btnStop.setDisable(true);
        btnStop.setOnAction(e -> stopServer());

        HBox statusBox = new HBox(8);
        statusBox.setAlignment(Pos.CENTER);
        statusBox.setStyle("-fx-background-color: #333; -fx-padding: 5 10 5 10; -fx-background-radius: 15;");

        Circle statusDot = new Circle(5);
        statusDot.setFill(Color.GRAY);

        lblStatusIndicator = new Label("STOPPED");
        lblStatusIndicator.setStyle("-fx-text-fill: #aaaaaa; -fx-font-weight: bold; -fx-font-size: 11px;");

        lblStatusIndicator.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.equals("RUNNING")) {
                statusDot.setFill(Color.rgb(46, 204, 113)); // Green
                lblStatusIndicator.setStyle("-fx-text-fill: #2ecc71; -fx-font-weight: bold; -fx-font-size: 11px;");
            } else {
                statusDot.setFill(Color.GRAY);
                lblStatusIndicator.setStyle("-fx-text-fill: #aaaaaa; -fx-font-weight: bold; -fx-font-size: 11px;");
            }
        });

        statusBox.getChildren().addAll(statusDot, lblStatusIndicator);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        box.getChildren().addAll(lblPort, txtPort, btnStart, btnStop, spacer, statusBox);
        return box;
    }

    private void createModernTable() {
        clientTable = new TableView<>();
        clientTable.setItems(connectedClients);
        clientTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(clientTable, Priority.ALWAYS);
        clientTable.getStyleClass().add("modern-table");
        clientTable.setPlaceholder(new Label("No clients connected"));

        TableColumn<ClientModel, String> colId = new TableColumn<>("CLIENT ID");
        colId.setCellValueFactory(data -> data.getValue().idProperty());
        colId.setStyle("-fx-alignment: CENTER-LEFT; -fx-text-fill: #87cefa; -fx-font-weight: bold;");

        TableColumn<ClientModel, String> colIp = new TableColumn<>("IP ADDRESS");
        colIp.setCellValueFactory(data -> data.getValue().ipProperty());
        colIp.setStyle("-fx-alignment: CENTER;");

        TableColumn<ClientModel, String> colStatus = new TableColumn<>("STATUS");
        colStatus.setCellValueFactory(data -> data.getValue().statusProperty());
        colStatus.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    HBox box = new HBox(8);
                    box.setAlignment(Pos.CENTER);
                    Circle dot = new Circle(4);
                    Label lbl = new Label(item);

                    if ("Online".equalsIgnoreCase(item)) {
                        dot.setFill(Color.rgb(46, 204, 113));
                        lbl.setStyle("-fx-text-fill: #2ecc71;");
                    } else {
                        dot.setFill(Color.GRAY);
                        lbl.setStyle("-fx-text-fill: #aaa;");
                    }
                    box.getChildren().addAll(dot, lbl);
                    setGraphic(box);
                    setText(null);
                }
            }
        });

        clientTable.getColumns().addAll(colId, colIp, colStatus);
    }

    private void startServer() {
        String portStr = txtPort.getText();
        try {
            int port = Integer.parseInt(portStr);

            serverThread = new Thread(() -> {
                try {
                    System.out.println("Starting Server on port " + port + "...");
                    new RemoteControlServer(port).run();
                } catch (Exception e) {
                    // System.err.println("Server Error: " + e.getMessage());
                    Platform.runLater(() -> stopServerUIState());
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();

            txtPort.setDisable(true);
            btnStart.setDisable(true);
            btnStop.setDisable(false);
            lblStatusIndicator.setText("RUNNING");

        } catch (NumberFormatException e) {
            logArea.appendText("Invalid Port Number!\n");
        }
    }

    private void stopServer() {

        if (serverThread != null && serverThread.isAlive()) {
            serverThread.interrupt();
            serverThread = null;
        }

        System.out.println("Server stopping...");
        stopServerUIState();
    }

    private void stopServerUIState() {
        txtPort.setDisable(false);
        btnStart.setDisable(false);
        btnStop.setDisable(true);
        lblStatusIndicator.setText("STOPPED");
        connectedClients.clear();
    }

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
        Platform.runLater(() -> {
            if (logArea != null) {
                if (!str.isBlank()) {
                    String time = dateFormat.format(new Date());
                    logArea.appendText("[" + time + "] " + str);
                } else {
                    logArea.appendText(str);
                }
            }
        });
    }

    private String getInlineCSS() {
        return ".root { -fx-font-family: 'Segoe UI', sans-serif; }" +

        // Buttons
                ".btn-start { -fx-background-color: #007acc; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; }"
                +
                ".btn-start:hover { -fx-background-color: #0098ff; }" +
                ".btn-start:disabled { -fx-background-color: #444; -fx-text-fill: #888; }" +

                ".btn-stop { -fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; }"
                +
                ".btn-stop:hover { -fx-background-color: #c0392b; }" +
                ".btn-stop:disabled { -fx-background-color: #444; -fx-text-fill: #888; }" +

                // Table
                ".modern-table { -fx-background-color: transparent; }" +
                ".modern-table .column-header-background { -fx-background-color: #2d2d30; -fx-border-color: transparent transparent #444 transparent; }"
                +
                ".modern-table .column-header .label { -fx-text-fill: #aaaaaa; -fx-font-weight: bold; -fx-font-size: 11px; }"
                +
                ".modern-table .table-cell { -fx-text-fill: white; -fx-border-color: transparent; }" +
                ".modern-table .table-row-cell { -fx-background-color: #1e1e1e; }" +
                ".modern-table .table-row-cell:odd { -fx-background-color: #252526; }" +
                ".modern-table .table-row-cell:selected { -fx-background-color: #007acc; }";
    }

    public static void main(String[] args) {
        launch(args);
    }
}