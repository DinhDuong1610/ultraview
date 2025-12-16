package client.core;

import client.core.NetworkClient;
import protocol.VideoPacket;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ScreenSender {

    private NetworkClient networkClient;
    private String myId;
    private String targetId;
    private ScheduledExecutorService executor;
    private boolean isStreaming = false;
    private Robot robot;
    private Rectangle screenRect;

    // Tăng giới hạn chunk lên mức an toàn (50KB để trừ hao Header Kryo)
    private static final int MAX_CHUNK_SIZE = 45000;
    private long frameIdCounter = 0; // ID tăng dần cho mỗi khung hình

    public ScreenSender(NetworkClient networkClient, String myId, String targetId) {
        this.networkClient = networkClient;
        this.myId = myId;
        this.targetId = targetId;
        try {
            this.screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            this.robot = new Robot();
        } catch (AWTException e) {
            e.printStackTrace();
        }
    }

    public void startStreaming() {
        if (isStreaming)
            return;
        isStreaming = true;
        executor = Executors.newSingleThreadScheduledExecutor();

        // Tăng tốc độ chụp lên (40ms ~ 25 FPS). Nếu mạng LAN tốt có thể để 33ms (30
        // FPS)
        executor.scheduleAtFixedRate(this::captureAndSend, 0, 40, TimeUnit.MILLISECONDS);
        System.out.println("Started High-Quality streaming to " + targetId);
    }

    public void stopStreaming() {
        isStreaming = false;
        if (executor != null)
            executor.shutdownNow();
    }

    private void captureAndSend() {
        try {
            // 1. Chụp màn hình gốc (Full HD / 2K)
            BufferedImage capture = robot.createScreenCapture(screenRect);

            // 2. Nén JPEG (Chất lượng cao 0.7 - 0.8)
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            ImageWriter writer = writers.next();
            ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
            writer.setOutput(ios);

            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.5f); // Chất lượng 70% (Nét hơn nhiều so với 50%)

            writer.write(null, new IIOImage(capture, null, null), param);
            ios.close();
            writer.dispose();

            byte[] fullImageData = baos.toByteArray();

            // 3. CHUNKING (Cắt nhỏ)
            int totalLength = fullImageData.length;
            int totalChunks = (int) Math.ceil((double) totalLength / MAX_CHUNK_SIZE);
            long currentFrameId = frameIdCounter++;

            for (int i = 0; i < totalChunks; i++) {
                int start = i * MAX_CHUNK_SIZE;
                int end = Math.min(totalLength, start + MAX_CHUNK_SIZE);

                // Copy mảng byte con
                byte[] chunkData = Arrays.copyOfRange(fullImageData, start, end);

                // Tạo gói tin
                VideoPacket packet = new VideoPacket(
                        myId, targetId, chunkData, System.currentTimeMillis(),
                        currentFrameId, i, totalChunks);

                // Gửi đi
                networkClient.sendVideoPacket(packet);
                // System.out.println("Sender: Đang gửi Frame " + currentFrameId + " Chunk " +
                // i);

                // Sleep cực ngắn để tránh tràn bộ đệm UDP mạng (Congestion Control thô sơ)
                // Nếu mạng LAN thì không cần, nhưng mạng Wifi yếu thì nên có
                // Thread.sleep(0, 500); // 500 nanoseconds
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}