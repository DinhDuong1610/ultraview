package client.network.handler;

import codec.KryoSerializer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import javafx.application.Platform;
import javafx.scene.image.Image;
import protocol.media.VideoPacket;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class UdpClientHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    public static Consumer<Image> onImageReceived;

    public static void setOnImageReceived(Consumer<Image> listener) {
        onImageReceived = listener;
    }

    private static final Map<Long, Map<Integer, byte[]>> frameBuffer = new ConcurrentHashMap<>();
    private static final Map<Long, Long> frameTimestamps = new ConcurrentHashMap<>();
    private static long lastProcessedFrameId = -1;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket datagram) throws Exception {
        byte[] data = new byte[datagram.content().readableBytes()];
        datagram.content().readBytes(data);

        VideoPacket packet;
        try {
            Object obj = KryoSerializer.deserialize(data);

            if (obj instanceof VideoPacket) {
                packet = (VideoPacket) obj;
            } else {
                return;
            }
        } catch (Exception e) {
            System.err.println("Lỗi giải mã UDP: " + e.getMessage());
            return;
        }

        if (packet.getData() == null || packet.getData().length == 0 || packet.getTotalChunks() == 0) {
            return;
        }

        long frameId = packet.getFrameId();
        int chunkIndex = packet.getChunkIndex();
        int totalChunks = packet.getTotalChunks();

        if (frameId < lastProcessedFrameId)
            return;

        frameBuffer.putIfAbsent(frameId, new ConcurrentHashMap<>());
        frameBuffer.get(frameId).put(chunkIndex, packet.getData());
        frameTimestamps.putIfAbsent(frameId, System.currentTimeMillis());

        if (frameBuffer.get(frameId).size() == totalChunks) {
            assembleAndDisplay(frameId, totalChunks);
        } else {
            long firstReceived = frameTimestamps.get(frameId);
            if (System.currentTimeMillis() - firstReceived > 2000) {
                frameBuffer.remove(frameId);
                frameTimestamps.remove(frameId);
            }
        }
    }

    private void assembleAndDisplay(long frameId, int totalChunks) {
        try {
            Map<Integer, byte[]> chunks = frameBuffer.get(frameId);
            int totalSize = 0;
            for (int i = 0; i < totalChunks; i++) {
                if (!chunks.containsKey(i))
                    return;
                totalSize += chunks.get(i).length;
            }

            byte[] fullImage = new byte[totalSize];
            int currentPos = 0;
            for (int i = 0; i < totalChunks; i++) {
                byte[] chunk = chunks.get(i);
                System.arraycopy(chunk, 0, fullImage, currentPos, chunk.length);
                currentPos += chunk.length;
            }

            ByteArrayInputStream bis = new ByteArrayInputStream(fullImage);
            Image image = new Image(bis);

            if (onImageReceived != null) {
                Platform.runLater(() -> onImageReceived.accept(image));
            }

            lastProcessedFrameId = frameId;
            frameBuffer.remove(frameId);
            frameTimestamps.remove(frameId);

            frameBuffer.keySet().removeIf(id -> id < lastProcessedFrameId);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
    }
}