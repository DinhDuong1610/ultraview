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
// Import Common
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

    // --- 1. CORE SERVICES ---
    private NetworkClient networkClient; // Core mạng
    private FileSender fileSender; // Logic gửi file
    private FileReceiver fileReceiver;
    private AudioRecorder audioRecorder; // Logic Mic
    private ScreenSender currentSender; // Logic quay màn hình

    // --- 2. UI CONTROLLERS ---
    private DashboardController dashboardController;
    private ChatController chatController;

    // --- 3. UI COMPONENTS ---
    private Stage primaryStage;
    private BorderPane mainLayout;
    private Button btnMic; // Nút Mic ở Footer

    // --- 4. STATE & INFO ---
    private boolean isMicOn = true;
    private final String myId = generateRandomId();
    private final String myPass = generateRandomPass();

    // Remote View (Cửa sổ hiển thị màn hình đối tác)
    private Stage remoteStage;
    private ImageView remoteView;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        // A. Khởi tạo Services (Network & Logic)
        initServices();

        // B. Khởi tạo UI Controllers
        initControllers();

        // C. Dựng Layout chính
        buildMainLayout();

        // D. Thiết lập các sự kiện (Wiring)
        setupNetworkCallbacks(); // Network -> UI
        setupControllerEvents(); // UI -> Network

        // E. Bắt đầu kết nối Server (Chạy ngầm)
        connectToServer();
    }

    private void initServices() {
        // Tạo NetworkClient trước (chưa connect vội)
        networkClient = new NetworkClient("192.168.1.8", 8080);

        // Các Service phụ thuộc vào NetworkClient
        fileSender = new FileSender(networkClient);
        fileReceiver = new FileReceiver();
        audioRecorder = new AudioRecorder(networkClient);
    }

    private void initControllers() {
        // Dashboard quản lý việc nhập ID/Pass
        dashboardController = new DashboardController(myId, myPass);

        // Chat quản lý tin nhắn và file (cần FileSender để gửi file)
        chatController = new ChatController(primaryStage, fileSender);
    }

    private void connectToServer() {
        new Thread(() -> {
            try {
                // Kết nối tới Server
                networkClient.connect(myId, myPass);

                // Chạy Clipboard Sync
                startClipboardWorker();

                // Platform.runLater(
                // () -> chatController.addMessage(">>> Đã kết nối Server. ID của bạn: " + myId,
                // false, true));
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Lỗi kết nối", e.getMessage()));
                // e.printStackTrace();
            }
        }).start();
    }

    private void buildMainLayout() {
        mainLayout = new BorderPane();
        mainLayout.getStyleClass().add("root");

        // --- MENU BAR ---
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

        // Điều hướng giữa các View
        itemDashboard.setOnAction(e -> mainLayout.setCenter(dashboardController.getView()));
        itemChat.setOnAction(e -> {
            mainLayout.setCenter(chatController.getView());
            chatController.focusInput();
        });

        menuView.getItems().addAll(itemDashboard, itemChat);
        menuBar.getMenus().add(menuView);
        mainLayout.setTop(menuBar);

        // --- CENTER ---
        // Mặc định hiện Dashboard
        mainLayout.setCenter(dashboardController.getView());

        // --- FOOTER ---
        HBox footer = new HBox(15);
        footer.setPadding(new Insets(8, 15, 8, 15));
        footer.setStyle("-fx-background-color: #007acc;");
        footer.setAlignment(Pos.CENTER_LEFT);

        btnMic = new Button("🎙 Mic: ON");
        btnMic.setStyle(
                "-fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        btnMic.setOnAction(e -> toggleMic());

        Label lblStatus = new Label("Ready (Secured connection)");
        lblStatus.setStyle("-fx-text-fill: white;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        footer.getChildren().addAll(btnMic, spacer, lblStatus);
        mainLayout.setBottom(footer);

        // --- SCENE ---
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

    // --- [QUAN TRỌNG] WIRING: UI CONTROLLER -> NETWORK ---
    private void setupControllerEvents() {

        // 1. Khi bấm nút Connect ở Dashboard
        dashboardController.setOnConnectRequest(() -> {
            String targetId = dashboardController.getTargetId();
            String targetPass = dashboardController.getTargetPass();

            if (targetId.isEmpty() || targetPass.isEmpty()) {
                showAlert("Thiếu thông tin", "Vui lòng nhập ID và Mật khẩu!");
                return;
            }

            // Đồng bộ P2P Mode lần cuối
            networkClient.setP2PEnabled(dashboardController.isP2PSelected());

            // Cập nhật UI sang trạng thái "Đang kết nối..."
            dashboardController.setConnectingState(true);

            // Gửi yêu cầu kết nối
            networkClient.requestControl(targetId, targetPass);
        });

        // 2. Khi bấm nút Gửi tin nhắn ở Chat
        chatController.setOnSendText((msg) -> {
            String target = dashboardController.getTargetId();
            if (target.isEmpty()) {
                // chatController.addMessage("⚠ Vui lòng nhập ID đối tác ở màn hình chính
                // trước.", false, true);
            } else {
                networkClient.sendChat(myId, target, msg);
            }
        });

        // 3. Khi bấm nút chuyển chế độ P2P
        dashboardController.setOnP2PToggle(() -> {
            networkClient.setP2PEnabled(dashboardController.isP2PSelected());
        });
    }

    // --- [QUAN TRỌNG] WIRING: NETWORK -> UI UPDATE ---
    private void setupNetworkCallbacks() {

        // 1. Nhận Chat
        ClientHandler.onMessageReceived = (msg) -> {
            // Parse tin nhắn để biết của ai
            boolean isSystem = !msg.contains("]: ");
            ChatMessageModel model = new ChatMessageModel(msg, false, isSystem);
            chatController.addMessage(model);

            // Notification nếu đang không ở màn hình chat
            if (mainLayout.getCenter() != chatController.getView()) {
                // TODO: Play sound or badge
            }
        };

        // 2. Kết quả Kết nối (Thành công/Thất bại)
        ClientHandler.onConnectResult = (res) -> {
            Platform.runLater(() -> {
                dashboardController.setConnectingState(false); // Reset nút bấm

                if (res.isSuccess()) {
                    // chatController.addMessage(">>> Kết nối thành công! Đang chờ hình ảnh...",
                    // false, true);
                    mainLayout.setCenter(chatController.getView()); // Chuyển sang tab Chat
                    startMicAuto();
                } else {
                    showAlert("Lỗi kết nối", res.getMessage());
                }
            });
        };

        // 3. Bị điều khiển (Start Streaming)
        ClientHandler.onStartStreaming = (controllerId) -> {
            Platform.runLater(() -> {
                dashboardController.setTargetId(controllerId);
                // chatController.addMessage(">>> Đang được điều khiển bởi ID: " + controllerId,
                // false, true);

                // Bắt đầu quay màn hình gửi đi
                new Thread(() -> {
                    if (currentSender != null)
                        currentSender.stopStreaming();
                    currentSender = new ScreenSender(networkClient, myId, controllerId);
                    currentSender.startStreaming();
                }).start();

                startMicAuto();
            });
        };

        // 4. Đối tác ngắt kết nối
        ClientHandler.onPartnerDisconnect = (disconnectedId) -> {
            String currentPartner = dashboardController.getTargetId();
            if (!currentPartner.isEmpty() && currentPartner.equals(disconnectedId)) {
                Platform.runLater(() -> {
                    closeRemoteWindow(); // Đóng cửa sổ xem
                    if (currentSender != null) { // Tắt quay màn hình
                        currentSender.stopStreaming();
                        currentSender = null;
                    }

                    dashboardController.setTargetId("");
                    dashboardController.setConnectingState(false);
                    // chatController.addMessage(">>> Đối tác đã ngắt kết nối.", false, true);
                    showAlert("Thông báo", "Phiên làm việc đã kết thúc.");
                });
            }
        };

        // 5. File Offer (Nhận lời mời file)
        ClientHandler.onFileOffer = (offer) -> {
            String sizeStr = offer.getFileSize() / 1024 + " KB";
            chatController.addFileMessage(offer.getFileName(), sizeStr, false);
        };

        // 6. File Accepted (Mình gửi đi)
        ClientHandler.onFileAccepted = (fileName) -> {
            fileSender.startFileStream(fileName);
            // Platform.runLater(() -> chatController.addMessage(">>> Đối tác chấp nhận tải:
            // " + fileName, false, true));
        };

        // 7. File Success (Mình nhận xong)
        ClientHandler.onFileTransferSuccess = (msg) -> {
            // chatController.addMessage(">>> " + msg, false, true);
        };

        // A. Khi nhận Header file
        ClientHandler.onFileReq = (req) -> {
            // [MỚI] Báo cho Receiver biết để bật chế độ "Hứng & Chờ"
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
                            // [MỚI] Chọn xong -> Bắt đầu xả hàng đợi và ghi file
                            if (fileReceiver != null) {
                                fileReceiver.startReceiving(req, selectedDir);
                            }
                        } else {
                            // Bấm Hủy chọn thư mục
                            if (fileReceiver != null)
                                fileReceiver.cancelReceive();
                            chatController.addMessage(new ChatMessageModel("Đã hủy chọn thư mục lưu.", false, true));
                        }
                    } else {
                        // Bấm Từ chối ngay từ đầu
                        if (fileReceiver != null)
                            fileReceiver.cancelReceive();
                        chatController.addMessage(new ChatMessageModel("Đã từ chối nhận file.", false, true));
                    }
                });
            });
        };

        // B. Khi nhận dữ liệu file
        ClientHandler.onFileChunk = (chunk) -> {
            if (fileReceiver != null)
                fileReceiver.receiveChunk(chunk);
        };

        // C. Sửa logic thông báo thành công (Uncomment)
        ClientHandler.onFileTransferSuccess = (msg) -> {
            // UNCOMMENT DÒNG NÀY
            chatController.addMessage(new ChatMessageModel(msg, false, true));
        };

        // 8. Nhận hình ảnh UDP (Remote View)
        UdpClientHandler.setOnImageReceived(image -> Platform.runLater(() -> showRemoteWindow(image)));

    }

    // ---

    // REMOTE VIEW
    // WINDOW LOGIC---

    private void showRemoteWindow(Image image) {
        if (remoteStage == null || !remoteStage.isShowing()) {
            remoteStage = new Stage();
            remoteView = new ImageView();
            remoteView.setPreserveRatio(true);
            remoteView.setFitWidth(1600);

            StackPane root = new StackPane(remoteView);
            root.setStyle("-fx-background-color: black;");
            root.setAlignment(Pos.CENTER);

            Scene scene = new Scene(root, 1600, 1200);
            setupInputEvents(remoteView, scene);

            remoteStage.setTitle("Remote Control - " +
                    dashboardController.getTargetId());
            remoteStage.setScene(scene);
            remoteStage.show();
            remoteStage.setOnCloseRequest(e -> remoteStage = null);
        }
        remoteView.setImage(image);
    }

    // --- REMOTE VIEW WINDOW LOGIC ---
    // private void showRemoteWindow(Image image) {
    // if (remoteStage == null || !remoteStage.isShowing()) {
    // remoteStage = new Stage();
    // remoteView = new ImageView();

    // // Giữ tỷ lệ khung hình (để hình không bị méo)
    // remoteView.setPreserveRatio(true);

    // // BỎ DÒNG NÀY: remoteView.setFitWidth(1024);
    // // Thay vào đó, ta sẽ bind kích thước ở dưới

    // StackPane root = new StackPane(remoteView);
    // root.setStyle("-fx-background-color: black;");
    // root.setAlignment(Pos.CENTER);

    // // Tạo Scene (Kích thước ban đầu không quan trọng lắm vì sẽ phóng to ngay)
    // Scene scene = new Scene(root, 1024, 768);

    // // --- [QUAN TRỌNG] BINDING KÍCH THƯỚC ---
    // // Tự động thay đổi kích thước ảnh khi cửa sổ thay đổi
    // remoteView.fitWidthProperty().bind(scene.widthProperty());
    // remoteView.fitHeightProperty().bind(scene.heightProperty());
    // // ----------------------------------------

    // setupInputEvents(remoteView, scene);

    // remoteStage.setTitle("Remote Control - " +
    // dashboardController.getTargetId());
    // remoteStage.setScene(scene);

    // // --- LỰA CHỌN CHẾ ĐỘ HIỂN THỊ ---

    // // Cách 1: Phóng to tối đa (Vẫn hiện thanh tiêu đề và Taskbar) -> KHUYÊN DÙNG
    // remoteStage.setMaximized(true);

    // // Cách 2: Full Screen hoàn toàn (Tràn viền, che mất Taskbar)
    // // remoteStage.setFullScreen(true);
    // // remoteStage.setFullScreenExitHint("Nhấn ESC để thoát chế độ toàn màn
    // hình");

    // remoteStage.show();
    // remoteStage.setOnCloseRequest(e -> remoteStage = null);
    // }
    // remoteView.setImage(image);
    // }

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

    // --- OTHER HELPERS ---
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