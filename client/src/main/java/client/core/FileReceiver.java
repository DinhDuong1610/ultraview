package client.core;

import protocol.FileChunkPacket;
import protocol.FileReqPacket;
import javafx.application.Platform;
import javafx.scene.control.Alert;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileReceiver {
    private FileOutputStream fos;
    private String currentFileName;
    private long totalReceived = 0;
    private long fileSize = 0;

    // Thư mục lưu file: Mặc định là thư mục Downloads của máy
    private final String SAVE_DIR = System.getProperty("user.home") + "/Downloads/";

    public void handleRequest(FileReqPacket req) {
        try {
            if (fos != null)
                fos.close(); // Đóng file cũ nếu đang dở

            this.currentFileName = req.getFileName();
            this.fileSize = req.getFileSize();
            this.totalReceived = 0;

            File file = new File(SAVE_DIR + "Remote_" + currentFileName);
            fos = new FileOutputStream(file);

            System.out.println("Receiving file: " + file.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void handleChunk(FileChunkPacket chunk) {
        if (fos == null)
            return;

        try {
            fos.write(chunk.getData());
            totalReceived += chunk.getLength();

            if (chunk.isLast()) {
                fos.close();
                fos = null;
                System.out.println("File received complete: " + currentFileName);

                // Hiện thông báo lên màn hình
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("File Transfer");
                    alert.setHeaderText("Nhận file thành công!");
                    alert.setContentText("Đã lưu tại: " + SAVE_DIR + "Remote_" + currentFileName);
                    alert.show();
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}