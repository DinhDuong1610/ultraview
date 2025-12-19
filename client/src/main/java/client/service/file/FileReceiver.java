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

    private boolean isWaitingForUser = false;

    private final Queue<FileChunkPacket> chunkBuffer = new LinkedList<>();

    public void prepareReceive(FileReqPacket req) {
        this.isWaitingForUser = true;
        this.chunkBuffer.clear();
        this.totalSize = req.getFileSize();
        this.receivedSize = 0;

        System.out.println(">>> Đang chờ người dùng chọn nơi lưu file: " + req.getFileName());
    }

    public void startReceiving(FileReqPacket req, File destinationDir) {
        try {
            if (!destinationDir.exists())
                destinationDir.mkdirs();

            currentFile = new File(destinationDir, req.getFileName());
            fos = new FileOutputStream(currentFile);

            System.out.println(">>> Bắt đầu ghi file vào: " + currentFile.getAbsolutePath());

            synchronized (chunkBuffer) {
                while (!chunkBuffer.isEmpty()) {
                    processChunk(chunkBuffer.poll());
                }
                isWaitingForUser = false;
            }

        } catch (IOException e) {
            e.printStackTrace();
            isWaitingForUser = false;
        }
    }

    public void cancelReceive() {
        isWaitingForUser = false;
        chunkBuffer.clear();
        System.out.println(">>> Đã hủy nhận file.");
    }

    public void receiveChunk(FileChunkPacket chunk) {
        synchronized (chunkBuffer) {
            if (isWaitingForUser || fos == null) {
                chunkBuffer.add(chunk);
            } else {
                processChunk(chunk);
            }
        }
    }

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