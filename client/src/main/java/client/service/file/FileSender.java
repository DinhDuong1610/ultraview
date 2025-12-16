package client.service.file;

import client.network.NetworkClient;
import protocol.core.NetworkPacket;
import protocol.core.PacketType;
import protocol.file.*;

import java.io.File;
import java.io.FileInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FileSender {

    private final NetworkClient networkClient;
    private final Map<String, File> pendingFiles = new ConcurrentHashMap<>();

    public FileSender(NetworkClient networkClient) {
        this.networkClient = networkClient;
    }

    // 1. Gửi lời mời (Offer)
    public void sendFileOffer(File file) {
        if (!networkClient.isConnected())
            return;

        // Lưu lại file vào danh sách chờ
        pendingFiles.put(file.getName(), file);

        // Gửi gói tin chào mời
        FileOfferPacket offer = new FileOfferPacket(file.getName(), file.length());
        networkClient.sendTcpPacket(new NetworkPacket(PacketType.FILE_OFFER, offer));
    }

    // 2. Bắt đầu gửi file (Khi đối phương chấp nhận)
    public void startFileStream(String fileName) {
        File file = pendingFiles.get(fileName);
        if (file == null || !file.exists())
            return;

        new Thread(() -> {
            try {
                System.out.println("Starting file transfer: " + file.getName());

                // A. Gửi Header (Thông tin file)
                FileReqPacket req = new FileReqPacket(file.getName(), file.length());
                networkClient.sendTcpPacket(new NetworkPacket(PacketType.FILE_REQ, req));

                // B. Đọc file và cắt nhỏ (Chunking)
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192]; // 8KB per chunk
                    int bytesRead;

                    while ((bytesRead = fis.read(buffer)) != -1) {
                        byte[] chunkData = new byte[bytesRead];
                        System.arraycopy(buffer, 0, chunkData, 0, bytesRead);

                        boolean isLast = (fis.available() == 0);

                        FileChunkPacket chunk = new FileChunkPacket(chunkData, bytesRead, isLast);
                        networkClient.sendTcpPacket(new NetworkPacket(PacketType.FILE_CHUNK, chunk));

                        // Flow Control: Nghỉ 1ms để tránh nghẽn mạng
                        Thread.sleep(1);
                    }
                }
                System.out.println("File sent successfully: " + fileName);

                // Xóa khỏi danh sách chờ
                pendingFiles.remove(fileName);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // 3. Gửi lệnh chấp nhận (khi mình là người nhận file)
    public void sendFileAccept(String fileName) {
        if (networkClient.isConnected()) {
            networkClient.sendTcpPacket(new NetworkPacket(PacketType.FILE_ACCEPT, new FileAcceptPacket(fileName)));
        }
    }
}