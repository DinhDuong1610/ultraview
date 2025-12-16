package client;

import client.core.*;
import protocol.ControlPayload;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.Random;

public class ClientApp extends Application {

    private NetworkClient networkClient;
    private Stage primaryStage;
    private BorderPane mainLayout; // Layout gốc

    // --- CÁC VIEW (Màn hình con) ---
    private VBox dashboardView; // Màn hình kết nối (2 cột)
    private VBox chatView; // Màn hình Chat & File

    // --- UI Components ---
    private TextField partnerIdField;
    private PasswordField partnerPassField;
    private Button btnShareScreen;

    // Chat Components
    private ListView<ChatMessageModel> chatListView;
    private ObservableList<ChatMessageModel> chatMessages;
    private TextField messageField;

    private AudioRecorder audioRecorder;
    private Button btnMic;
    private boolean isMicOn = true; // Mặc định bật

    // Info
    private final String myId = generateRandomId();
    private final String myPass = generateRandomPass();

    private ScreenSender currentSender;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        // Init Data
        chatMessages = FXCollections.observableArrayList();

        // Auto Connect
        autoConnectServer();

        // Build UI (Menu + Main Layout)
        buildMainLayout();

        // Callbacks
        setupCallbacks();
    }

    private void autoConnectServer() {
        new Thread(() -> {
            try {
                networkClient = new NetworkClient("127.0.0.1", 8080);
                networkClient.connect(myId, myPass);
                startClipboardWorker();
                addSystemMessage(">>> Đã kết nối Server. ID của bạn: " + myId);
                if (audioRecorder == null) {
                    audioRecorder = new AudioRecorder(networkClient);
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Lỗi kết nối Server!");
                    alert.show();
                });
            }
        }).start();
    }

    private void buildMainLayout() {
        mainLayout = new BorderPane();
        mainLayout.getStyleClass().add("root");

        // --- 1. MENU BAR (Điều hướng) ---
        MenuBar menuBar = new MenuBar();

        Menu menuView = new Menu("Chế độ xem (View)");
        MenuItem itemDashboard = new MenuItem("Bảng điều khiển (Kết nối)");
        MenuItem itemChat = new MenuItem("Trò chuyện & File");

        // Logic chuyển màn hình
        itemDashboard.setOnAction(e -> mainLayout.setCenter(dashboardView));
        itemChat.setOnAction(e -> {
            mainLayout.setCenter(chatView);
            // Auto focus vào ô chat
            if (messageField != null)
                messageField.requestFocus();
        });

        menuView.getItems().addAll(itemDashboard, itemChat);

        Menu menuFile = new Menu("File");
        MenuItem itemExit = new MenuItem("Thoát");
        itemExit.setOnAction(e -> {
            Platform.exit();
            System.exit(0);
        });
        menuFile.getItems().add(itemExit);

        menuBar.getMenus().addAll(menuFile, menuView);
        mainLayout.setTop(menuBar);

        // --- 2. INIT CÁC VIEW CON ---
        createDashboardView(); // Khởi tạo giao diện 2 cột
        createChatView(); // Khởi tạo giao diện Chat + File

        // Mặc định hiện Dashboard
        mainLayout.setCenter(dashboardView);

        // --- 3. FOOTER (Trạng thái) ---
        // --- 3. FOOTER ---
        HBox footer = new HBox(15);
        footer.setPadding(new Insets(8, 15, 8, 15));
        footer.setStyle("-fx-background-color: #007acc;");
        footer.setAlignment(Pos.CENTER_LEFT);

        // Nút Mic
        btnMic = new Button("🎙 Mic: ON");
        btnMic.setStyle(
                "-fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");

        btnMic.setOnAction(e -> toggleMic());

        Label lblStatus = new Label("Ready to connect (Secure connection)");
        lblStatus.setStyle("-fx-text-fill: white;");

        // Đẩy lblStatus sang phải
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        footer.getChildren().addAll(btnMic, spacer, lblStatus);
        mainLayout.setBottom(footer);

        Scene scene = new Scene(mainLayout, 900, 600);
        applyCSS(scene);
        primaryStage.setTitle("UltraViewer Clone Pro - Dark Mode");
        primaryStage.setScene(scene);
        primaryStage.show();

        primaryStage.setOnCloseRequest(e -> {
            Platform.exit();
            System.exit(0);
        });
    }

    // --- VIEW 1: DASHBOARD (2 Cột - UltraViewer Style) ---
    private void createDashboardView() {
        dashboardView = new VBox(20);
        dashboardView.setPadding(new Insets(20));

        HBox splitBox = new HBox();
        VBox.setVgrow(splitBox, Priority.ALWAYS);

        // === CỘT TRÁI: ALLOW REMOTE CONTROL ===
        VBox leftPane = new VBox(20);
        leftPane.getStyleClass().add("left-card");
        leftPane.setPrefWidth(450);
        HBox.setHgrow(leftPane, Priority.ALWAYS);

        Label lblAllow = new Label("Cho phép điều khiển");
        lblAllow.getStyleClass().add("header-blue");

        // ID & Pass (Read only)
        VBox idBox = new VBox(5, new Label("ID của bạn"), createReadOnlyField(myId));
        VBox passBox = new VBox(5, new Label("Mật khẩu"), createReadOnlyField(myPass));

        leftPane.getChildren().addAll(lblAllow, new Label("Gửi ID/Pass cho đối tác:"), new Separator(), idBox, passBox);

        // === CỘT PHẢI: CONTROL REMOTE ===
        VBox rightPane = new VBox(20);
        rightPane.getStyleClass().add("right-card");
        rightPane.setPrefWidth(450);
        HBox.setHgrow(rightPane, Priority.ALWAYS);

        Label lblControl = new Label("Điều khiển máy khác");
        lblControl.getStyleClass().add("header-orange");

        partnerIdField = new TextField();
        partnerIdField.setPromptText("Nhập Partner ID");
        partnerIdField.getStyleClass().add("big-input");

        partnerPassField = new PasswordField();
        partnerPassField.setPromptText("Nhập Mật khẩu");
        partnerPassField.getStyleClass().add("big-input");

        btnShareScreen = new Button("Bắt đầu điều khiển");
        btnShareScreen.getStyleClass().add("connect-btn");
        btnShareScreen.setPrefHeight(40);
        btnShareScreen.setMaxWidth(Double.MAX_VALUE);

        btnShareScreen.setOnAction(e -> {
            String targetId = partnerIdField.getText().trim();
            String targetPass = partnerPassField.getText().trim();
            if (targetId.isEmpty() || targetPass.isEmpty()) {
                showAlert("Thiếu thông tin", "Vui lòng nhập ID và Mật khẩu!");
                return;
            }
            btnShareScreen.setDisable(true);
            btnShareScreen.setText("Đang kết nối...");
            networkClient.requestControl(targetId, targetPass);
        });

        rightPane.getChildren().addAll(lblControl, new Label("Nhập thông tin đối tác:"), new Separator(),
                new Label("Partner ID"), partnerIdField,
                new Label("Mật khẩu"), partnerPassField,
                btnShareScreen);

        splitBox.getChildren().addAll(leftPane, rightPane);
        dashboardView.getChildren().add(splitBox);
    }

    // --- VIEW 2: CHAT & FILE (Messenger Style) ---
    private void createChatView() {
        chatView = new VBox(10);
        chatView.setPadding(new Insets(10));
        chatView.setStyle("-fx-background-color: #1e1e1e;");

        // 1. HEADER CHAT
        Label chatHeader = new Label("Trò chuyện & Truyền tệp");
        chatHeader.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: white;");
        chatView.getChildren().add(chatHeader);

        // 2. LIST VIEW (Bong bóng chat)
        chatListView = new ListView<>(chatMessages);
        chatListView.getStyleClass().add("chat-list");
        VBox.setVgrow(chatListView, Priority.ALWAYS);
        setupChatBubbleFactory(); // Setup giao diện bong bóng

        // 3. INPUT BOX (Gộp Nút File + Input + Nút Gửi)
        HBox inputBox = new HBox(10);
        inputBox.setAlignment(Pos.CENTER_LEFT);
        inputBox.getStyleClass().add("chat-input-box"); // Class CSS mới bo tròn
        inputBox.setPadding(new Insets(5, 10, 5, 5));
        inputBox.setPrefHeight(50);

        // Nút Đính kèm File (Icon kẹp giấy)
        Button btnAttach = new Button("📎");
        btnAttach.getStyleClass().add("attach-btn");
        btnAttach.setTooltip(new Tooltip("Gửi file..."));

        // Ô nhập tin nhắn
        messageField = new TextField();
        messageField.setPromptText("Nhập tin nhắn...");
        messageField.getStyleClass().add("chat-input-field"); // Class CSS làm trong suốt
        HBox.setHgrow(messageField, Priority.ALWAYS);

        // Nút Gửi tin nhắn
        Button btnSend = new Button("➤"); // Icon máy bay giấy
        btnSend.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #007acc; -fx-font-size: 20px; -fx-cursor: hand;");

        // Logic Gửi Chat
        Runnable sendAction = () -> {
            String msg = messageField.getText().trim();
            // Lấy ID đối tác từ bên Dashboard (vì nó là chung)
            String target = partnerIdField.getText().trim();

            if (!msg.isEmpty() && !target.isEmpty()) {
                networkClient.sendChat(myId, target, msg);
                addChatMessage(new ChatMessageModel(msg, true, false));
                messageField.clear();
                chatListView.scrollTo(chatMessages.size() - 1);
            } else if (target.isEmpty()) {
                addSystemMessage("⚠ Chưa kết nối với ai. Hãy nhập ID đối tác bên Menu 'Kết nối'.");
            }
        };

        btnSend.setOnAction(e -> sendAction.run());
        messageField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER)
                sendAction.run();
        });

        btnAttach.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            File file = fileChooser.showOpenDialog(primaryStage);
            if (file != null) {
                // Thay vì gửi luôn, ta dùng sendFileOffer
                networkClient.sendFileOffer(file);

                // Hiện bong bóng phía mình
                String sizeStr = file.length() / 1024 + " KB";
                chatMessages.add(new ChatMessageModel(file.getName(), sizeStr, true));
                chatListView.scrollTo(chatMessages.size() - 1);
            }
        });

        inputBox.getChildren().addAll(btnAttach, messageField, btnSend);
        chatView.getChildren().addAll(chatListView, inputBox);
    }

    // --- HELPER: TẠO BONG BÓNG CHAT ---
    private void setupChatBubbleFactory() {
        chatListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(ChatMessageModel item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }

                HBox rowBox = new HBox();

                // --- 1. TIN NHẮN TEXT & SYSTEM ---
                if (item.type == MsgType.TEXT || item.type == MsgType.SYSTEM) {
                    Label lblMessage = new Label(item.content);

                    // CẤU HÌNH QUAN TRỌNG ĐỂ XUỐNG DÒNG
                    lblMessage.setWrapText(true);
                    lblMessage.setMaxWidth(350); // Giới hạn chiều rộng tối đa, quá thì xuống dòng

                    // Style class cơ bản
                    lblMessage.getStyleClass().add("chat-message-label");

                    if (item.isMe) {
                        // Căn phải
                        rowBox.setAlignment(Pos.CENTER_RIGHT);
                        lblMessage.getStyleClass().add("chat-bubble-me");
                    } else if (item.type == MsgType.SYSTEM) {
                        // Căn giữa
                        rowBox.setAlignment(Pos.CENTER);
                        lblMessage.getStyleClass().clear(); // Xóa style mặc định
                        lblMessage.getStyleClass().add("chat-system-label");
                    } else {
                        // Căn trái
                        rowBox.setAlignment(Pos.CENTER_LEFT);
                        lblMessage.getStyleClass().add("chat-bubble-partner");
                    }

                    rowBox.getChildren().add(lblMessage);
                }

                // --- 2. TIN NHẮN FILE (FILE OFFER) ---
                else if (item.type == MsgType.FILE_OFFER) {
                    // Logic hiển thị File Bubble giữ nguyên như trước, chỉ chỉnh lại căn lề chút
                    // xíu
                    VBox bubble = new VBox(5);
                    bubble.getStyleClass().add("file-bubble");
                    bubble.setPrefWidth(220); // File bubble fix cứng chiều rộng cho đẹp

                    HBox fileInfo = new HBox(10);
                    fileInfo.setAlignment(Pos.CENTER_LEFT);
                    Label icon = new Label("📄");
                    icon.setStyle("-fx-font-size: 24px; -fx-text-fill: #fff;");

                    VBox details = new VBox(2);
                    Label nameLbl = new Label(item.content);
                    nameLbl.getStyleClass().add("file-name");
                    nameLbl.setWrapText(false); // Tên file dài quá thì tự ... (mặc định của Label)

                    Label sizeLbl = new Label(item.subInfo);
                    sizeLbl.getStyleClass().add("file-size");
                    details.getChildren().addAll(nameLbl, sizeLbl);

                    fileInfo.getChildren().addAll(icon, details);
                    bubble.getChildren().add(fileInfo);

                    if (!item.isMe) {
                        Button btnDownload = new Button("⬇ Tải xuống");
                        btnDownload.getStyleClass().add("download-btn");
                        btnDownload.setMaxWidth(Double.MAX_VALUE);
                        btnDownload.setOnAction(e -> {
                            btnDownload.setDisable(true);
                            btnDownload.setText("Đang tải...");
                            networkClient.sendFileAccept(item.content);
                        });
                        bubble.getChildren().add(btnDownload);
                    } else {
                        Label sentLbl = new Label("Đã gửi yêu cầu...");
                        sentLbl.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11px;");
                        bubble.getChildren().add(sentLbl);
                    }

                    if (item.isMe)
                        rowBox.setAlignment(Pos.CENTER_RIGHT);
                    else
                        rowBox.setAlignment(Pos.CENTER_LEFT);

                    rowBox.getChildren().add(bubble);
                }

                setGraphic(rowBox);
            }
        });
    }

    // --- HELPER METHODS ---
    private TextField createReadOnlyField(String text) {
        TextField tf = new TextField(text);
        tf.setEditable(false);
        tf.getStyleClass().add("big-input");
        tf.setStyle("-fx-background-color: #2d2d30; -fx-text-fill: #87cefa; -fx-font-weight: bold;");
        return tf;
    }

    // Enum loại tin nhắn
    enum MsgType {
        TEXT, FILE_OFFER, SYSTEM
    }

    private static class ChatMessageModel {
        MsgType type;
        String content; // Nội dung text hoặc Tên file
        String subInfo; // Kích thước file (nếu là file)
        boolean isMe;

        // Constructor cho Text
        public ChatMessageModel(String msg, boolean isMe, boolean isSystem) {
            this.type = isSystem ? MsgType.SYSTEM : MsgType.TEXT;
            this.content = msg;
            this.isMe = isMe;
        }

        // Constructor cho File
        public ChatMessageModel(String fileName, String fileSize, boolean isMe) {
            this.type = MsgType.FILE_OFFER;
            this.content = fileName;
            this.subInfo = fileSize;
            this.isMe = isMe;
        }
    }

    private void addChatMessage(ChatMessageModel message) {
        Platform.runLater(() -> {
            chatMessages.add(message);
            chatListView.scrollTo(chatMessages.size() - 1);
        });
    }

    private void addSystemMessage(String msg) {
        addChatMessage(new ChatMessageModel(msg, false, true));
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.show();
    }

    private String generateRandomId() {
        return String.valueOf(100000000 + new Random().nextInt(900000000));
    }

    private String generateRandomPass() {
        return String.valueOf(1000 + new Random().nextInt(9000));
    }

    private void startClipboardWorker() {
        if (networkClient == null)
            return;
        ClipboardWorker worker = new ClipboardWorker(networkClient);
        Thread t = new Thread(worker);
        t.setDaemon(true);
        t.start();
    }

    private void applyCSS(Scene scene) {
        try {
            scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        } catch (Exception e) {
            System.err.println("CSS not found");
        }
    }

    // --- CALLBACKS ---
    private void setupCallbacks() {
        // Chat
        ClientHandler.setOnMessageReceived(msg -> {
            if (msg.contains("]: ")) {
                // Nhảy thông báo nếu đang ở tab Dashboard
                if (mainLayout.getCenter() == dashboardView) {
                    // TODO: Có thể làm icon chuông rung rung (Nâng cao)
                }
                addChatMessage(new ChatMessageModel(msg.substring(msg.indexOf("]: ") + 3), false, false));
            } else {
                addChatMessage(new ChatMessageModel(msg, false, false));
            }
        });

        ClientHandler.onStartStreaming = (controllerId) -> {
            Platform.runLater(() -> {
                partnerIdField.setText(controllerId);
                addSystemMessage(">>> Đang được điều khiển bởi ID: " + controllerId);

                // Lưu lại instance để tí nữa stop được
                new Thread(() -> {
                    if (currentSender != null)
                        currentSender.stopStreaming(); // Stop cái cũ nếu có
                    currentSender = new ScreenSender(networkClient, myId, controllerId);
                    currentSender.startStreaming();
                }).start();

                startMicAuto();
            });
        };

        // --- XỬ LÝ NGẮT KẾT NỐI (MỚI) ---
        // --- XỬ LÝ NGẮT KẾT NỐI ---
        ClientHandler.onPartnerDisconnect = (disconnectedId) -> {
            String currentPartner = partnerIdField.getText().trim();

            // Chỉ xử lý nếu người thoát đúng là người mình đang kết nối
            if (!currentPartner.isEmpty() && currentPartner.equals(disconnectedId)) {
                Platform.runLater(() -> {
                    // TRƯỜNG HỢP 1: Mình đang xem màn hình họ (Controller)
                    if (remoteStage != null) {
                        remoteStage.close();
                        remoteStage = null;
                        showAlert("Ngắt kết nối", "Máy đối tác (" + disconnectedId + ") đã kết thúc phiên!");
                    }

                    // TRƯỜNG HỢP 2: Họ đang xem màn hình mình (Controlled)
                    if (currentSender != null) {
                        currentSender.stopStreaming();
                        currentSender = null;

                        // Xóa ID đối tác khỏi ô nhập để tránh hiểu nhầm
                        partnerIdField.clear();

                        addSystemMessage(">>> Đối tác (" + disconnectedId + ") đã ngắt kết nối.");
                        showAlert("Thông báo", "Người điều khiển đã thoát.");
                    }

                    // Reset nút bấm
                    btnShareScreen.setDisable(false);
                    btnShareScreen.setText("Bắt đầu điều khiển");
                });
            }
        };

        // Connect Result
        ClientHandler.onConnectResult = (res) -> {
            Platform.runLater(() -> {
                btnShareScreen.setDisable(false);
                btnShareScreen.setText("Bắt đầu điều khiển");
                if (res.isSuccess()) {
                    addSystemMessage(">>> Kết nối thành công! Đang chờ hình ảnh...");
                    // Tự động chuyển sang tab Chat để người dùng thấy
                    mainLayout.setCenter(chatView);
                    startMicAuto();
                } else {
                    showAlert("Lỗi kết nối", res.getMessage());
                }

            });
        };

        // 1. Nhận Lời mời File (Người nhận)
        ClientHandler.onFileOffer = (offer) -> {
            String sizeStr = offer.getFileSize() / 1024 + " KB";
            addChatMessage(new ChatMessageModel(offer.getFileName(), sizeStr, false));
        };

        // 2. Nhận Lệnh Chấp nhận (Người gửi) -> Bắt đầu bắn Data
        ClientHandler.onFileAccepted = (fileName) -> {
            // Core Logic: Bắt đầu stream file
            networkClient.startFileStream(fileName);
            Platform.runLater(() -> addSystemMessage(">>> Đối tác đã chấp nhận tải file: " + fileName));
        };

        // 3. Tải xong (Người nhận)
        ClientHandler.onFileTransferSuccess = (msg) -> {
            addSystemMessage(">>> " + msg + " (Kiểm tra thư mục Downloads)");
        };

        // ...

        // --- Trong logic Nút Đính kèm (createChatView) ---

        // Remote Window
        UdpClientHandler.setOnImageReceived(image -> Platform.runLater(() -> showRemoteWindow(image)));
    }

    // --- REMOTE WINDOW (Giữ nguyên logic) ---
    private Stage remoteStage;
    private ImageView remoteView;

    private void showRemoteWindow(Image image) {
        if (remoteStage == null || !remoteStage.isShowing()) {
            remoteStage = new Stage();
            remoteView = new ImageView();
            remoteView.setPreserveRatio(true);
            remoteView.setFitWidth(1024);
            StackPane root = new StackPane(remoteView);
            root.setStyle("-fx-background-color: black;");
            root.setAlignment(Pos.CENTER);
            Scene scene = new Scene(root, 1024, 768);
            setupInputEvents(remoteView, scene);
            remoteStage.setTitle("Remote View - " + partnerIdField.getText());
            remoteStage.setScene(scene);
            remoteStage.show();
            remoteStage.setOnCloseRequest(e -> remoteStage = null);
        }
        remoteView.setImage(image);
    }

    private void setupInputEvents(ImageView view, Scene scene) {
        view.setOnMouseMoved(e -> sendMouse(e.getX(), e.getY(), view, 0, 0));
        view.setOnMouseDragged(e -> sendMouse(e.getX(), e.getY(), view, 0, 0));
        view.setOnMousePressed(e -> {
            int btn = e.getButton() == javafx.scene.input.MouseButton.PRIMARY ? 1
                    : e.getButton() == javafx.scene.input.MouseButton.SECONDARY ? 3 : 2;
            sendMouse(e.getX(), e.getY(), view, 1, btn);
        });
        view.setOnMouseReleased(e -> {
            int btn = e.getButton() == javafx.scene.input.MouseButton.PRIMARY ? 1
                    : e.getButton() == javafx.scene.input.MouseButton.SECONDARY ? 3 : 2;
            sendMouse(e.getX(), e.getY(), view, 2, btn);
        });
        view.setFocusTraversable(true);
        view.requestFocus();
        scene.setOnKeyPressed(e -> {
            int awtCode = KeyMapper.toAwtKeyCode(e.getCode());
            if (awtCode != -1)
                networkClient.sendControl(new ControlPayload(3, 0, 0, 0, awtCode));
            e.consume();
        });
        scene.setOnKeyReleased(e -> {
            int awtCode = KeyMapper.toAwtKeyCode(e.getCode());
            if (awtCode != -1)
                networkClient.sendControl(new ControlPayload(4, 0, 0, 0, awtCode));
            e.consume();
        });
    }

    private void sendMouse(double x, double y, ImageView view, int action, int btn) {
        double w = view.getBoundsInLocal().getWidth();
        double h = view.getBoundsInLocal().getHeight();
        if (w == 0 || h == 0)
            return;
        networkClient.sendControl(new ControlPayload(action, (float) (x / w), (float) (y / h), btn, 0));
    }

    // --- LOGIC TOGGLE MIC ---
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
        // Chỉ bật nếu trạng thái đang là ON
        if (isMicOn && audioRecorder != null) {
            audioRecorder.start();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}