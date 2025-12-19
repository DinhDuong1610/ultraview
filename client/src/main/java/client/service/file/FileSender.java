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

    public void sendFileOffer(File file) {
        if (!networkClient.isConnected())
            return;

        pendingFiles.put(file.getName(), file);

        FileOfferPacket offer = new FileOfferPacket(file.getName(), file.length());
        networkClient.sendTcpPacket(new NetworkPacket(PacketType.FILE_OFFER, offer));
    }

    public void startFileStream(String fileName) {
        File file = pendingFiles.get(fileName);
        if (file == null || !file.exists())
            return;

        new Thread(() -> {
            try {
                System.out.println("Starting file transfer: " + file.getName());

                FileReqPacket req = new FileReqPacket(file.getName(), file.length());
                networkClient.sendTcpPacket(new NetworkPacket(PacketType.FILE_REQ, req));

                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;

                    while ((bytesRead = fis.read(buffer)) != -1) {
                        byte[] chunkData = new byte[bytesRead];
                        System.arraycopy(buffer, 0, chunkData, 0, bytesRead);

                        boolean isLast = (fis.available() == 0);

                        FileChunkPacket chunk = new FileChunkPacket(chunkData, bytesRead, isLast);
                        networkClient.sendTcpPacket(new NetworkPacket(PacketType.FILE_CHUNK, chunk));

                        Thread.sleep(1);
                    }
                }
                System.out.println("File sent successfully: " + fileName);

                pendingFiles.remove(fileName);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void sendFileAccept(String fileName) {
        if (networkClient.isConnected()) {
            networkClient.sendTcpPacket(new NetworkPacket(PacketType.FILE_ACCEPT, new FileAcceptPacket(fileName)));
        }
    }
}