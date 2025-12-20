package client.ui;

import client.network.NetworkClient;
import client.network.handler.ClientHandler;
import client.network.handler.UdpClientHandler;
import client.service.audio.AudioRecorder;
import client.service.file.FileReceiver;
import client.service.file.FileSender;
import client.service.input.ClipboardWorker;
import client.service.input.KeyMapper;
import client.service.video.ScreenSender;
import client.ui.controller.ChatController;
import client.ui.controller.DashboardController;
import client.ui.controller.ChatController.ChatMessageModel;
import protocol.input.ControlPayload;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.Random;

public class ClientApp extends Application {

    private NetworkClient networkClient;
    private FileSender fileSender;
    private FileReceiver fileReceiver;
    private AudioRecorder audioRecorder;
    private ScreenSender currentSender;

    private DashboardController dashboardController;
    private ChatController chatController;

    private Stage primaryStage;
    private BorderPane mainLayout;
    private Button btnMic;

    private boolean isMicOn = false;
    private final String myId = generateRandomId();
    private final String myPass = generateRandomPass();

    private Stage remoteStage;
    private ImageView remoteView;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        initServices();

        initControllers();

        buildMainLayout();

        setupNetworkCallbacks();
        setupControllerEvents();

        connectToServer();
    }

    private void initServices() {
        networkClient = new NetworkClient("172.20.10.3", 8080);

        fileSender = new FileSender(networkClient);
        fileReceiver = new FileReceiver();
        audioRecorder = new AudioRecorder(networkClient);
    }

    private void initControllers() {
        dashboardController = new DashboardController(myId, myPass);

        chatController = new ChatController(primaryStage, fileSender);
    }

    private void connectToServer() {
        new Thread(() -> {
            try {
                networkClient.connect(myId, myPass);
                startClipboardWorker();
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Lỗi kết nối", e.getMessage()));
                // e.printStackTrace();
            }
        }).start();
    }

    private void buildMainLayout() {
        mainLayout = new BorderPane();
        mainLayout.getStyleClass().add("root");

        MenuBar menuBar = new MenuBar();
        menuBar.getStyleClass().add("menu-bar");
        menuBar.setStyle("-fx-background-color: #333333; -fx-text-fill: white;");
        Menu menuView = new Menu("Chế độ xem");
        menuView.getStyleClass().add("menu-bar");
        menuView.setStyle("-fx-background-color: #434343ff; -fx-text-fill: white;");
        MenuItem itemDashboard = new MenuItem("Bảng điều khiển");
        itemDashboard.getStyleClass().add("menu-bar");
        itemDashboard.setStyle("-fx-background-color: #ffffffff; -fx-text-fill: black;");
        MenuItem itemChat = new MenuItem("Trò chuyện");
        itemChat.getStyleClass().add("menu-bar");
        itemChat.setStyle("-fx-background-color: #ffffffff; -fx-text-fill: black;");

        itemDashboard.setOnAction(e -> mainLayout.setCenter(dashboardController.getView()));
        itemChat.setOnAction(e -> {
            mainLayout.setCenter(chatController.getView());
            chatController.focusInput();
        });

        menuView.getItems().addAll(itemDashboard, itemChat);
        menuBar.getMenus().add(menuView);
        mainLayout.setTop(menuBar);

        mainLayout.setCenter(dashboardController.getView());

        HBox footer = new HBox(15);
        footer.setPadding(new Insets(8, 15, 8, 15));
        footer.setStyle("-fx-background-color: #007acc;");
        footer.setAlignment(Pos.CENTER_LEFT);

        btnMic = new Button("🔇 Mic: OFF");
        btnMic.setStyle(
                "-fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        btnMic.setOnAction(e -> toggleMic());

        Label lblStatus = new Label("Ready (Secured connection)");
        lblStatus.setStyle("-fx-text-fill: white;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        footer.getChildren().addAll(btnMic, spacer, lblStatus);
        mainLayout.setBottom(footer);

        Scene scene = new Scene(mainLayout, 900, 600);
        applyCSS(scene);
        primaryStage.setTitle("UltraViewer" + myId);
        primaryStage.setScene(scene);
        primaryStage.show();

        primaryStage.setOnCloseRequest(e -> {
            Platform.exit();
            System.exit(0);
        });
    }

    private void setupControllerEvents() {

        dashboardController.setOnConnectRequest(() -> {
            String targetId = dashboardController.getTargetId();
            String targetPass = dashboardController.getTargetPass();

            if (targetId.isEmpty() || targetPass.isEmpty()) {
                showAlert("Thiếu thông tin", "Vui lòng nhập ID và Mật khẩu!");
                return;
            }

            networkClient.setP2PEnabled(dashboardController.isP2PSelected());

            dashboardController.setConnectingState(true);

            networkClient.requestControl(targetId, targetPass);
        });

        chatController.setOnSendText((msg) -> {
            String target = dashboardController.getTargetId();
            if (target.isEmpty()) {
            } else {
                networkClient.sendChat(myId, target, msg);
            }
        });

        dashboardController.setOnP2PToggle(() -> {
            networkClient.setP2PEnabled(dashboardController.isP2PSelected());
        });
    }

    private void setupNetworkCallbacks() {

        ClientHandler.onMessageReceived = (msg) -> {
            boolean isSystem = !msg.contains("]: ");
            ChatMessageModel model = new ChatMessageModel(msg, false, isSystem);
            chatController.addMessage(model);

            if (mainLayout.getCenter() != chatController.getView()) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Tin nhắn mới");
                    alert.setHeaderText("Bạn có tin nhắn mới:");
                    alert.setContentText(msg);
                    alert.showAndWait();
                });
            }
        };

        ClientHandler.onConnectResult = (res) -> {
            Platform.runLater(() -> {
                dashboardController.setConnectingState(false);

                if (res.isSuccess()) {
                    mainLayout.setCenter(chatController.getView());
                    startMicAuto();
                } else {
                    showAlert("Lỗi kết nối", res.getMessage());
                }
            });
        };

        ClientHandler.onStartStreaming = (controllerId) -> {
            Platform.runLater(() -> {
                dashboardController.setTargetId(controllerId);

                new Thread(() -> {
                    if (currentSender != null)
                        currentSender.stopStreaming();
                    currentSender = new ScreenSender(networkClient, myId, controllerId);
                    currentSender.startStreaming();
                }).start();

                startMicAuto();
            });
        };

        ClientHandler.onPartnerDisconnect = (disconnectedId) -> {
            String currentPartner = dashboardController.getTargetId();
            if (!currentPartner.isEmpty() && currentPartner.equals(disconnectedId)) {
                Platform.runLater(() -> {
                    closeRemoteWindow();
                    if (currentSender != null) {
                        currentSender.stopStreaming();
                        currentSender = null;
                    }

                    dashboardController.setTargetId("");
                    dashboardController.setConnectingState(false);
                    showAlert("Thông báo", "Phiên làm việc đã kết thúc.");
                });
            }
        };

        ClientHandler.onFileOffer = (offer) -> {
            String sizeStr = offer.getFileSize() / 1024 + " KB";
            chatController.addFileMessage(offer.getFileName(), sizeStr, false);
        };

        ClientHandler.onFileAccepted = (fileName) -> {
            fileSender.startFileStream(fileName);
        };

        ClientHandler.onFileTransferSuccess = (msg) -> {
            // chatController.addMessage(">>> " + msg, false, true);
        };

        ClientHandler.onFileReq = (req) -> {
            if (fileReceiver != null)
                fileReceiver.prepareReceive(req);

            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Nhận File");
                alert.setHeaderText("Đối tác muốn gửi file: " + req.getFileName());
                alert.setContentText("Kích thước: " + (req.getFileSize() / 1024) + " KB. Bạn có muốn nhận?");

                ButtonType btnNhan = new ButtonType("Chọn nơi lưu");
                ButtonType btnHuy = new ButtonType("Từ chối", ButtonBar.ButtonData.CANCEL_CLOSE);
                alert.getButtonTypes().setAll(btnNhan, btnHuy);

                alert.showAndWait().ifPresent(type -> {
                    if (type == btnNhan) {
                        DirectoryChooser directoryChooser = new DirectoryChooser();
                        directoryChooser.setTitle("Chọn thư mục để lưu file");
                        File selectedDir = directoryChooser.showDialog(primaryStage);

                        if (selectedDir != null) {
                            if (fileReceiver != null) {
                                fileReceiver.startReceiving(req, selectedDir);
                            }
                        } else {
                            if (fileReceiver != null)
                                fileReceiver.cancelReceive();
                            chatController.addMessage(new ChatMessageModel("Đã hủy chọn thư mục lưu.", false, true));
                        }
                    } else {
                        if (fileReceiver != null)
                            fileReceiver.cancelReceive();
                        chatController.addMessage(new ChatMessageModel("Đã từ chối nhận file.", false, true));
                    }
                });
            });
        };

        ClientHandler.onFileChunk = (chunk) -> {
            if (fileReceiver != null)
                fileReceiver.receiveChunk(chunk);
        };

        ClientHandler.onFileTransferSuccess = (msg) -> {
            chatController.addMessage(new ChatMessageModel(msg, false, true));
        };

        UdpClientHandler.setOnImageReceived(image -> Platform.runLater(() -> showRemoteWindow(image)));

    }

    private void showRemoteWindow(Image image) {
        if (remoteStage == null || !remoteStage.isShowing()) {
            remoteStage = new Stage();
            remoteView = new ImageView();
            remoteView.setPreserveRatio(true);
            remoteView.setFitWidth(1150);

            StackPane root = new StackPane(remoteView);
            root.setStyle("-fx-background-color: black;");
            root.setAlignment(Pos.CENTER);

            Scene scene = new Scene(root, 1150, 768);
            setupInputEvents(remoteView, scene);

            remoteStage.setTitle("Remote Control - " +
                    dashboardController.getTargetId());
            remoteStage.setScene(scene);
            remoteStage.show();
            remoteStage.setOnCloseRequest(e -> remoteStage = null);
        }
        remoteView.setImage(image);
    }

    private void closeRemoteWindow() {
        if (remoteStage != null) {
            remoteStage.close();
            remoteStage = null;
        }
    }

    private void setupInputEvents(ImageView view, Scene scene) {
        // Mouse
        view.setOnMouseMoved(e -> sendMouse(e.getX(), e.getY(), view, 0, 0));
        view.setOnMouseDragged(e -> sendMouse(e.getX(), e.getY(), view, 0, 0));
        view.setOnMousePressed(e -> sendMouse(e.getX(), e.getY(), view, 1, getBtnCode(e)));
        view.setOnMouseReleased(e -> sendMouse(e.getX(), e.getY(), view, 2, getBtnCode(e)));

        // Keyboard
        view.setFocusTraversable(true);
        view.requestFocus();
        scene.setOnKeyPressed(e -> sendKey(3, e.getCode()));
        scene.setOnKeyReleased(e -> sendKey(4, e.getCode()));
    }

    private void sendMouse(double x, double y, ImageView view, int action, int btn) {
        double w = view.getBoundsInLocal().getWidth();
        double h = view.getBoundsInLocal().getHeight();
        if (w > 0 && h > 0) {
            networkClient.sendControl(new ControlPayload(action, (float) (x / w), (float) (y / h), btn, 0));
        }
    }

    private void sendKey(int action, KeyCode key) {
        int code = KeyMapper.toAwtKeyCode(key);
        if (code != -1)
            networkClient.sendControl(new ControlPayload(action, 0, 0, 0, code));
    }

    private int getBtnCode(javafx.scene.input.MouseEvent e) {
        if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY)
            return 1;
        if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY)
            return 3;
        return 2;
    }

    private void toggleMic() {
        isMicOn = !isMicOn;
        if (isMicOn) {
            btnMic.setText("🎙 Mic: ON");
            btnMic.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-font-weight: bold;");
            if (audioRecorder != null)
                audioRecorder.start();
        } else {
            btnMic.setText("🔇 Mic: OFF");
            btnMic.setStyle("-fx-background-color: #cc0000; -fx-text-fill: white; -fx-font-weight: bold;");
            if (audioRecorder != null)
                audioRecorder.stop();
        }
    }

    private void startMicAuto() {
        if (isMicOn && audioRecorder != null)
            audioRecorder.start();
    }

    private void startClipboardWorker() {
        if (networkClient != null) {
            ClipboardWorker worker = new ClipboardWorker(networkClient);
            Thread t = new Thread(worker);
            t.setDaemon(true);
            t.start();
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.show();
    }

    private void applyCSS(Scene scene) {
        try {
            scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        } catch (Exception e) {
            System.err.println("CSS not found");
        }
    }

    private String generateRandomId() {
        return String.valueOf(100000000 + new Random().nextInt(900000000));
    }

    private String generateRandomPass() {
        return String.valueOf(1000 + new Random().nextInt(9000));
    }

    public static void main(String[] args) {
        launch(args);
    }
}