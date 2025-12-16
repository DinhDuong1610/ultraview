package client.service.file;

import client.network.handler.ClientHandler;
import protocol.file.FileChunkPacket;
import protocol.file.FileReqPacket;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

public class FileReceiver {

    private FileOutputStream fos;
    private File currentFile;
    private long totalSize;
    private long receivedSize;

    // Cờ trạng thái: Đang chờ người dùng chọn thư mục
    private boolean isWaitingForUser = false;

    // Hàng đợi để lưu tạm các gói tin Chunk bị đến sớm
    private final Queue<FileChunkPacket> chunkBuffer = new LinkedList<>();

    // 1. Nhận yêu cầu -> Bật chế độ chờ
    public void prepareReceive(FileReqPacket req) {
        this.isWaitingForUser = true;
        this.chunkBuffer.clear(); // Xóa bộ nhớ đệm cũ
        this.totalSize = req.getFileSize();
        this.receivedSize = 0;

        // Chưa tạo file vội, chỉ lưu thông tin
        System.out.println(">>> Đang chờ người dùng chọn nơi lưu file: " + req.getFileName());
    }

    // 2. Người dùng đã chọn xong thư mục -> Bắt đầu ghi
    public void startReceiving(FileReqPacket req, File destinationDir) {
        try {
            if (!destinationDir.exists())
                destinationDir.mkdirs();

            currentFile = new File(destinationDir, req.getFileName());
            fos = new FileOutputStream(currentFile);

            System.out.println(">>> Bắt đầu ghi file vào: " + currentFile.getAbsolutePath());

            // Xả hàng đợi: Ghi tất cả các gói tin đã nhận trong lúc chờ
            synchronized (chunkBuffer) {
                while (!chunkBuffer.isEmpty()) {
                    processChunk(chunkBuffer.poll());
                }
                isWaitingForUser = false; // Tắt chế độ chờ
            }

        } catch (IOException e) {
            e.printStackTrace();
            isWaitingForUser = false;
        }
    }

    // 3. Người dùng hủy chọn -> Xóa bộ nhớ đệm
    public void cancelReceive() {
        isWaitingForUser = false;
        chunkBuffer.clear();
        System.out.println(">>> Đã hủy nhận file.");
    }

    // 4. Nhận Chunk từ mạng
    public void receiveChunk(FileChunkPacket chunk) {
        synchronized (chunkBuffer) {
            // Nếu đang chờ người dùng hoặc chưa có file -> Lưu vào bộ nhớ đệm
            if (isWaitingForUser || fos == null) {
                chunkBuffer.add(chunk);
            } else {
                // Nếu đã ổn định -> Ghi trực tiếp
                processChunk(chunk);
            }
        }
    }

    // Hàm ghi thực sự xuống đĩa
    private void processChunk(FileChunkPacket chunk) {
        if (fos == null)
            return;

        try {
            fos.write(chunk.getData(), 0, chunk.getLength());
            receivedSize += chunk.getLength();

            if (chunk.isLast() || receivedSize >= totalSize) {
                closeStream();
                System.out.println(">>> Nhận file hoàn tất!");

                if (ClientHandler.onFileTransferSuccess != null) {
                    ClientHandler.onFileTransferSuccess.accept("Đã lưu file tại: " + currentFile.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            closeStream();
        }
    }

    private void closeStream() {
        try {
            if (fos != null) {
                fos.close();
                fos = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}