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
import client.network.p2p.PeerControlServer;
import client.network.p2p.SessionState;
import protocol.media.StartStreamPacket;

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

    private SessionState sessionState;
    private PeerControlServer peerControlServer;
    private int peerControlPort;

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

    // ===== Zoom/Fit state =====
    private ScrollPane remoteScroll;
    private StackPane remoteContainer;
    private BorderPane remoteRoot;

    private enum ZoomMode {
        MANUAL, FIT
    }

    private ZoomMode zoomMode = ZoomMode.FIT;

    private double zoom = 1.0;

    private Label lblZoom; // hiển thị % zoom

    @Override
    public void start(Stage stage) throws InterruptedException {
        this.primaryStage = stage;

        initServices();

        sessionState = new SessionState();

        peerControlServer = new PeerControlServer(sessionState);
        peerControlPort = peerControlServer.start();
        System.out.println("PeerControlServer listening on port: " + peerControlPort);

        initControllers();

        buildMainLayout();

        setupNetworkCallbacks();
        setupControllerEvents();

        connectToServer();
    }

    private void initServices() {
        networkClient = new NetworkClient("192.168.1.8", 8080);

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
        ClientHandler.onLoginSuccess = () -> {
            networkClient.sendPeerRegister(peerControlPort);
        };

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
                    String partnerId = dashboardController.getTargetId();
                    sessionState.set(res.getSessionId(), partnerId);

                    // connect P2P control trực tiếp tới target
                    networkClient.connectPeerControl(res.getPeerHost(), res.getPeerControlPort(), res.getSessionId());

                    mainLayout.setCenter(chatController.getView());
                    startMicAuto();
                } else {
                    showAlert("Lỗi kết nối", res.getMessage());
                }
            });
        };

        ClientHandler.onStartStreaming = (stream) -> {
            String controllerId = stream.getControllerId();
            String sid = stream.getSessionId();

            // target biết session để validate hello
            sessionState.set(sid, controllerId);

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

                    networkClient.closePeerControl();
                    sessionState.clear();

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

    // private void showRemoteWindow(Image image) {
    // if (remoteStage == null || !remoteStage.isShowing()) {
    // remoteStage = new Stage();
    // remoteView = new ImageView();
    // remoteView.setPreserveRatio(true);
    // remoteView.setFitWidth(1150);

    // StackPane root = new StackPane(remoteView);
    // root.setStyle("-fx-background-color: black;");
    // root.setAlignment(Pos.CENTER);

    // Scene scene = new Scene(root, 1150, 768);
    // setupInputEvents(remoteView, scene);

    // remoteStage.setTitle("Remote Control - " +
    // dashboardController.getTargetId());
    // remoteStage.setScene(scene);
    // remoteStage.show();
    // remoteStage.setOnCloseRequest(e -> remoteStage = null);
    // }
    // remoteView.setImage(image);
    // }

    private void showRemoteWindow(Image image) {
        if (remoteStage == null || !remoteStage.isShowing()) {
            remoteStage = new Stage();

            // 1) ImageView: KHÔNG setFitWidth cố định nữa
            remoteView = new ImageView();
            remoteView.setPreserveRatio(true);
            remoteView.setSmooth(true);
            remoteView.setCache(true);

            // 2) Container để canh giữa
            remoteContainer = new StackPane(remoteView);
            remoteContainer.setStyle("-fx-background-color: black;");
            remoteContainer.setAlignment(Pos.CENTER);
            remoteContainer.setMinSize(0, 0);

            // 3) ScrollPane để scroll khi zoom lớn
            remoteScroll = new ScrollPane(remoteContainer);
            remoteScroll.setFitToWidth(true);
            remoteScroll.setFitToHeight(true);
            remoteScroll.setPannable(false); // tránh conflict với drag remote
            remoteScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            remoteScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

            // 4) Toolbar zoom
            Button btnFit = new Button("Fit");
            Button btnActual = new Button("1:1");
            Button btnMinus = new Button("−");
            Button btnPlus = new Button("+");
            lblZoom = new Label("100%");
            lblZoom.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

            HBox toolbar = new HBox(10, btnFit, btnActual, btnMinus, btnPlus, new Separator(), lblZoom);
            toolbar.setPadding(new Insets(8));
            toolbar.setAlignment(Pos.CENTER_LEFT);
            toolbar.setStyle("-fx-background-color: #222;");

            remoteRoot = new BorderPane();
            remoteRoot.setTop(toolbar);
            remoteRoot.setCenter(remoteScroll);

            Scene scene = new Scene(remoteRoot, 1150, 768);

            // input events (chuột/phím)
            setupInputEvents(remoteView, scene);

            // zoom handlers: Ctrl + scroll
            installZoomHandlers(scene);

            // Fit khi resize viewport (nếu đang FIT)
            remoteScroll.viewportBoundsProperty().addListener((obs, oldV, newV) -> {
                if (zoomMode == ZoomMode.FIT)
                    applyFitZoom();
            });

            // Buttons
            btnFit.setOnAction(e -> {
                zoomMode = ZoomMode.FIT;
                applyFitZoom();
            });

            btnActual.setOnAction(e -> {
                zoomMode = ZoomMode.MANUAL;
                setZoom(1.0);
                centerScroll();
            });

            btnPlus.setOnAction(e -> {
                zoomMode = ZoomMode.MANUAL;
                setZoom(zoom * 1.25);
            });

            btnMinus.setOnAction(e -> {
                zoomMode = ZoomMode.MANUAL;
                setZoom(zoom / 1.25);
            });

            remoteStage.setTitle("Remote Control - " + dashboardController.getTargetId());
            remoteStage.setScene(scene);
            remoteStage.show();

            remoteStage.setOnCloseRequest(e -> remoteStage = null);
        }

        // Update image
        remoteView.setImage(image);

        // Nếu đang FIT thì fit lại mỗi khi ảnh đổi (quan trọng)
        if (zoomMode == ZoomMode.FIT) {
            applyFitZoom();
        } else {
            // manual: giữ zoom hiện tại, chỉ cập nhật label
            updateZoomLabel();
        }
    }

    private void setZoom(double z) {
        zoom = clamp(z, 0.05, 8.0);

        if (remoteContainer != null) {
            remoteContainer.setScaleX(zoom);
            remoteContainer.setScaleY(zoom);
        } else if (remoteView != null) {
            remoteView.setScaleX(zoom);
            remoteView.setScaleY(zoom);
        }

        updateZoomLabel();
    }

    private void applyFitZoom() {
        if (remoteView == null)
            return;
        Image img = remoteView.getImage();
        if (img == null)
            return;

        if (remoteScroll == null || remoteScroll.getViewportBounds() == null)
            return;

        double vw = remoteScroll.getViewportBounds().getWidth();
        double vh = remoteScroll.getViewportBounds().getHeight();
        if (vw <= 0 || vh <= 0)
            return;

        double iw = img.getWidth();
        double ih = img.getHeight();
        if (iw <= 0 || ih <= 0)
            return;

        double sx = vw / iw;
        double sy = vh / ih;

        // FIT: thấy toàn bộ, giữ tỉ lệ
        double scale = Math.min(sx, sy);

        setZoom(scale);
        centerScroll();
    }

    private void centerScroll() {
        if (remoteScroll == null)
            return;
        remoteScroll.setHvalue(0.5);
        remoteScroll.setVvalue(0.5);
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private void updateZoomLabel() {
        if (lblZoom != null) {
            int percent = (int) Math.round(zoom * 100);
            lblZoom.setText(percent + "%");
        }
    }

    private void installZoomHandlers(Scene scene) {
        scene.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, e -> {
            if (!e.isControlDown())
                return;

            e.consume();
            zoomMode = ZoomMode.MANUAL;

            double factor = (e.getDeltaY() > 0) ? 1.1 : (1 / 1.1);

            zoomAt(e.getX(), e.getY(), factor);
        });
    }

    private void zoomAt(double sceneX, double sceneY, double factor) {
        if (remoteScroll == null || remoteContainer == null || remoteView == null) {
            setZoom(zoom * factor);
            return;
        }

        // 1) Lưu lại viewport bounds
        var viewport = remoteScroll.getViewportBounds();
        double vpW = viewport.getWidth();
        double vpH = viewport.getHeight();
        if (vpW <= 0 || vpH <= 0) {
            setZoom(zoom * factor);
            return;
        }

        // 2) Kích thước content hiện tại (sau scale)
        double contentWBefore = getContentWidth();
        double contentHBefore = getContentHeight();
        if (contentWBefore <= 0 || contentHBefore <= 0) {
            setZoom(zoom * factor);
            return;
        }

        // 3) Tính tỉ lệ con trỏ đang trỏ vào đâu trong content (0..1)
        // sceneX/sceneY -> chuyển về tọa độ trong ScrollPane viewport
        // Cách đơn giản và ổn định: dùng tỉ lệ dựa trên hvalue/vvalue
        double mouseXInViewport = sceneX - remoteScroll.localToScene(remoteScroll.getBoundsInLocal()).getMinX();
        double mouseYInViewport = sceneY - remoteScroll.localToScene(remoteScroll.getBoundsInLocal()).getMinY();

        double hVal = remoteScroll.getHvalue();
        double vVal = remoteScroll.getVvalue();

        // offset hiện tại trong content
        double maxHBefore = Math.max(0, contentWBefore - vpW);
        double maxVBefore = Math.max(0, contentHBefore - vpH);

        double offsetXBefore = hVal * maxHBefore;
        double offsetYBefore = vVal * maxVBefore;

        // tọa độ tuyệt đối trong content mà con trỏ đang “chỉ”
        double targetXInContent = offsetXBefore + clamp(mouseXInViewport, 0, vpW);
        double targetYInContent = offsetYBefore + clamp(mouseYInViewport, 0, vpH);

        double rx = (contentWBefore == 0) ? 0 : (targetXInContent / contentWBefore);
        double ry = (contentHBefore == 0) ? 0 : (targetYInContent / contentHBefore);

        // 4) Apply zoom mới
        double newZoom = clamp(zoom * factor, 0.05, 8.0);
        setZoom(newZoom);

        // cần layout lại để size content cập nhật
        remoteScroll.layout();

        double contentWAfter = getContentWidth();
        double contentHAfter = getContentHeight();

        double maxHAfter = Math.max(0, contentWAfter - vpW);
        double maxVAfter = Math.max(0, contentHAfter - vpH);

        // 5) Set hvalue/vvalue sao cho điểm rx/ry vẫn nằm dưới con trỏ
        double newOffsetX = rx * contentWAfter - clamp(mouseXInViewport, 0, vpW);
        double newOffsetY = ry * contentHAfter - clamp(mouseYInViewport, 0, vpH);

        double newH = (maxHAfter == 0) ? 0.5 : clamp(newOffsetX / maxHAfter, 0, 1);
        double newV = (maxVAfter == 0) ? 0.5 : clamp(newOffsetY / maxVAfter, 0, 1);

        remoteScroll.setHvalue(newH);
        remoteScroll.setVvalue(newV);
    }

    private double getContentWidth() {
        // remoteContainer wrap remoteView: size content ~ boundsInLocal * scale
        // dùng layoutBounds của remoteContainer sẽ ổn hơn vì StackPane co giãn
        return remoteContainer.getLayoutBounds().getWidth();
    }

    private double getContentHeight() {
        return remoteContainer.getLayoutBounds().getHeight();
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

        // Double click: FIT <-> 1:1
        // view.setOnMouseClicked(e -> {
        // if (e.getClickCount() == 2) {
        // if (zoomMode == ZoomMode.FIT) {
        // zoomMode = ZoomMode.MANUAL;
        // setZoom(1.0);
        // centerScroll();
        // } else {
        // zoomMode = ZoomMode.FIT;
        // applyFitZoom();
        // }
        // e.consume();
        // }
        // });

    }

    // private void sendMouse(double x, double y, ImageView view, int action, int
    // btn) {
    // double w = view.getBoundsInLocal().getWidth();
    // double h = view.getBoundsInLocal().getHeight();
    // if (w > 0 && h > 0) {
    // networkClient.sendControl(new ControlPayload(action, (float) (x / w), (float)
    // (y / h), btn, 0));
    // }
    // }

    private void sendMouse(double x, double y, ImageView view, int action, int btn) {
        Image img = view.getImage();
        if (img == null)
            return;

        double iw = img.getWidth();
        double ih = img.getHeight();
        if (iw <= 0 || ih <= 0)
            return;

        // IMPORTANT:
        // event.getX()/getY() là tọa độ local của ImageView (không bị ảnh hưởng bởi
        // scaleX/scaleY theo cách gây lệch)
        // => normalize trực tiếp theo kích thước ảnh gốc
        float nx = (float) (x / iw);
        float ny = (float) (y / ih);

        // clamp để tránh gửi >1 khi click ra ngoài vùng ảnh
        if (nx < 0)
            nx = 0;
        if (nx > 1)
            nx = 1;
        if (ny < 0)
            ny = 0;
        if (ny > 1)
            ny = 1;

        networkClient.sendControl(new ControlPayload(action, nx, ny, btn, 0));
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