package client.ui.controller;

import client.service.file.FileSender;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.function.Consumer;

public class ChatController {

    private VBox view;
    private ListView<ChatMessageModel> chatListView;
    private ObservableList<ChatMessageModel> chatMessages;
    private TextField messageField;

    // Dependencies
    private final Stage stage;
    private final FileSender fileSender;

    // Callbacks
    private Consumer<String> onSendText; // Báo cho ClientApp biết user muốn gửi text

    public ChatController(Stage stage, FileSender fileSender) {
        this.stage = stage;
        this.fileSender = fileSender;
        this.chatMessages = FXCollections.observableArrayList();
        createView();
    }

    private void createView() {
        view = new VBox(10);
        view.setPadding(new Insets(10));
        view.setStyle("-fx-background-color: #1e1e1e;");

        Label chatHeader = new Label("Trò chuyện & Truyền tệp");
        chatHeader.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: white;");
        view.getChildren().add(chatHeader);

        // Chat List
        chatListView = new ListView<>(chatMessages);
        chatListView.getStyleClass().add("chat-list");
        VBox.setVgrow(chatListView, Priority.ALWAYS);
        setupChatBubbleFactory();

        // Input Box
        HBox inputBox = new HBox(10);
        inputBox.setAlignment(Pos.CENTER_LEFT);
        inputBox.getStyleClass().add("chat-input-box");
        inputBox.setPadding(new Insets(5));
        inputBox.setPrefHeight(50);

        Button btnAttach = new Button("📎");
        btnAttach.getStyleClass().add("attach-btn");

        btnAttach.setOnAction(e -> handleAttachFile());

        messageField = new TextField();
        messageField.setPromptText("Nhập tin nhắn...");
        messageField.getStyleClass().add("chat-input-field");
        HBox.setHgrow(messageField, Priority.ALWAYS);

        Button btnSend = new Button("➤");
        btnSend.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #007acc; -fx-font-size: 20px; -fx-cursor: hand;");

        btnSend.setOnAction(e -> handleSendText());
        messageField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER)
                handleSendText();
        });

        inputBox.getChildren().addAll(btnAttach, messageField, btnSend);
        view.getChildren().addAll(chatListView, inputBox);
    }

    public VBox getView() {
        return view;
    }

    public void focusInput() {
        if (messageField != null)
            messageField.requestFocus();
    }

    public void setOnSendText(Consumer<String> callback) {
        this.onSendText = callback;
    }

    // --- LOGIC HANDLERS ---

    private void handleSendText() {
        String msg = messageField.getText().trim();
        if (!msg.isEmpty() && onSendText != null) {
            onSendText.accept(msg);
            // Add message to UI (Optimistic UI update)
            addMessage(new ChatMessageModel(msg, true, false));
            messageField.clear();
        }
    }

    private void handleAttachFile() {
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(stage);
        if (file != null && fileSender != null) {
            fileSender.sendFileOffer(file);
            String sizeStr = file.length() / 1024 + " KB";
            addMessage(new ChatMessageModel(file.getName(), sizeStr, true));
        }
    }

    // --- PUBLIC METHODS (Để ClientApp gọi cập nhật UI) ---

    public void addMessage(ChatMessageModel chatMessageModel) {
        Platform.runLater(() -> {
            chatMessages.add(chatMessageModel);
            chatListView.scrollTo(chatMessages.size() - 1);
        });
    }

    public void addFileMessage(String fileName, String sizeStr, boolean isMe) {
        Platform.runLater(() -> {
            chatMessages.add(new ChatMessageModel(fileName, sizeStr, isMe));
            chatListView.scrollTo(chatMessages.size() - 1);
        });
    }

    // --- HELPER CLASSES (Model & Factory) ---
    // (Copy đoạn setupChatBubbleFactory và ChatMessageModel từ ClientApp vào đây)
    // Để ngắn gọn, bạn có thể copy y nguyên 2 cái đó vào cuối file này.

    private void setupChatBubbleFactory() {
        chatListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(ChatMessageModel item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }

                HBox rowBox = new HBox();
                if (item.type == MsgType.TEXT || item.type == MsgType.SYSTEM) {
                    Label lbl = new Label(item.content);
                    lbl.setWrapText(true);
                    lbl.setMaxWidth(350);
                    lbl.getStyleClass().add("chat-message-label");

                    if (item.isMe) {
                        rowBox.setAlignment(Pos.CENTER_RIGHT);
                        lbl.getStyleClass().add("chat-bubble-me");
                    } else if (item.type == MsgType.SYSTEM) {
                        rowBox.setAlignment(Pos.CENTER);
                        lbl.getStyleClass().clear();
                        lbl.getStyleClass().add("chat-system-label");
                    } else {
                        rowBox.setAlignment(Pos.CENTER_LEFT);
                        lbl.getStyleClass().add("chat-bubble-partner");
                    }
                    rowBox.getChildren().add(lbl);
                } else if (item.type == MsgType.FILE_OFFER) {
                    // Logic file bubble
                    VBox bubble = new VBox(5);
                    bubble.getStyleClass().add("file-bubble");
                    bubble.setPrefWidth(220);
                    Label nameLbl = new Label("📄 " + item.content);
                    nameLbl.getStyleClass().add("file-name");
                    Label sizeLbl = new Label(item.subInfo);
                    sizeLbl.getStyleClass().add("file-size");
                    bubble.getChildren().addAll(nameLbl, sizeLbl);

                    if (!item.isMe) {
                        Button btnDown = new Button("⬇ Tải xuống");
                        btnDown.getStyleClass().add("download-btn");
                        btnDown.setOnAction(e -> {
                            btnDown.setDisable(true);
                            btnDown.setText("Đã tải");
                            if (fileSender != null)
                                fileSender.sendFileAccept(item.content);
                        });
                        bubble.getChildren().add(btnDown);
                    }
                    rowBox.setAlignment(item.isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                    rowBox.getChildren().add(bubble);
                }
                setGraphic(rowBox);
            }
        });
    }

    enum MsgType {
        TEXT, FILE_OFFER, SYSTEM
    }

    public static class ChatMessageModel {
        MsgType type;
        String content;
        String subInfo;
        boolean isMe;

        public ChatMessageModel(String msg, boolean isMe, boolean isSystem) {
            this.type = isSystem ? MsgType.SYSTEM : MsgType.TEXT;
            this.content = msg;
            this.isMe = isMe;
        }

        public ChatMessageModel(String name, String size, boolean isMe) {
            this.type = MsgType.FILE_OFFER;
            this.content = name;
            this.subInfo = size;
            this.isMe = isMe;
        }
    }
}