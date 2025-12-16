package client.core;

import protocol.ClipboardPacket;
import javafx.application.Platform;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;

public class ClipboardWorker implements Runnable {
    private final NetworkClient networkClient;
    private final Clipboard sysClipboard;
    private String lastContent = "";
    private boolean isRunning = true;

    // Cờ để đánh dấu: Nếu chính ta vừa nhận dữ liệu từ mạng về clipboard
    // thì đừng gửi ngược lại mạng nữa (Tránh vòng lặp vô tận)
    public static boolean isReceiving = false;

    public ClipboardWorker(NetworkClient networkClient) {
        this.networkClient = networkClient;
        this.sysClipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    }

    @Override
    public void run() {
        while (isRunning) {
            try {
                // Kiểm tra mỗi 1 giây
                Thread.sleep(1000);

                if (isReceiving) {
                    isReceiving = false;
                    // Cập nhật lastContent để không gửi lại cái vừa nhận
                    lastContent = getClipboardText();
                    continue;
                }

                String currentContent = getClipboardText();

                // Nếu nội dung thay đổi và không rỗng
                if (currentContent != null && !currentContent.equals(lastContent)) {
                    lastContent = currentContent;
                    System.out.println("Detected Copy: " + currentContent);

                    // Gửi đi
                    networkClient.sendClipboard(currentContent);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Lấy text từ Clipboard hệ thống
    private String getClipboardText() {
        try {
            if (sysClipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                return (String) sysClipboard.getData(DataFlavor.stringFlavor);
            }
        } catch (Exception e) {
            // Clipboard có thể bị busy bởi app khác, bỏ qua
        }
        return null;
    }

    public void stop() {
        isRunning = false;
    }
}