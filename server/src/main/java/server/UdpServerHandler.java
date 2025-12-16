package server;

import codec.KryoSerializer;
import protocol.VideoPacket;
import io.netty.buffer.Unpooled; // <--- QUAN TRỌNG: Thêm import này
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UdpServerHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Map<String, InetSocketAddress> udpClients = new ConcurrentHashMap<>();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
        // 1. Đọc dữ liệu từ gói tin vào mảng byte
        byte[] data = new byte[packet.content().readableBytes()];
        packet.content().readBytes(data);

        try {
            // 2. Giải mã
            Object obj = KryoSerializer.deserialize(data);

            if (obj instanceof VideoPacket) {
                VideoPacket videoPacket = (VideoPacket) obj;
                String senderId = videoPacket.getSenderId();
                String targetId = videoPacket.getTargetId();

                // Lưu địa chỉ người gửi
                udpClients.put(senderId, packet.sender());

                // 3. Chuyển tiếp (Relay)
                if (targetId != null && !targetId.isEmpty()) {
                    InetSocketAddress targetAddr = udpClients.get(targetId);

                    if (targetAddr != null) {
                        // System.out.println("-> Relaying video from " + senderId + " to " + targetId);

                        // --- SỬA LỖI TẠI ĐÂY ---
                        // Thay vì gửi packet.content() (đã bị đọc hết), ta gửi gói tin mới chứa mảng
                        // 'data'
                        ctx.writeAndFlush(new DatagramPacket(
                                Unpooled.wrappedBuffer(data), // Đóng gói lại mảng byte nguyên vẹn
                                targetAddr));
                        // -----------------------

                    } else {
                        // In lỗi nếu Client B chưa đăng ký UDP
                        // (Lỗi này sẽ hiện nếu bạn Share trước khi Client B kịp chạy xong)
                        System.err.println("Target not found: " + targetId);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // In lỗi ra để biết tại sao không vào được channelRead0
        System.err.println("Lỗi UDP Handler: " + cause.getMessage());
        cause.printStackTrace();
        // ctx.close(); // Đừng close vội, cứ để xem lỗi là gì
    }
}