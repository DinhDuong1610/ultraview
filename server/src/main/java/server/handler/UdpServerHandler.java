package server.handler;

import codec.KryoSerializer;
import protocol.media.VideoPacket;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import server.core.ServerContext; // Import Context để lấy địa chỉ đích

import java.net.InetSocketAddress;

public class UdpServerHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
        // 1. Lấy dữ liệu thô
        byte[] data = new byte[packet.content().readableBytes()];
        packet.content().readBytes(data);

        // 2. Giải mã
        Object obj;
        try {
            obj = KryoSerializer.deserialize(data);
        } catch (Exception e) {
            return; // Bỏ qua gói tin lỗi
        }

        // 3. Xử lý VideoPacket
        if (obj instanceof VideoPacket) {
            VideoPacket video = (VideoPacket) obj;
            String senderId = video.getSenderId();

            // --- A. ĐĂNG KÝ ĐỊA CHỈ UDP (Cho P2P) ---
            if (senderId != null) {
                InetSocketAddress senderAddr = packet.sender();
                InetSocketAddress oldAddr = ServerContext.getUdpAddress(senderId);

                if (oldAddr == null || !oldAddr.equals(senderAddr)) {
                    ServerContext.registerUdp(senderId, senderAddr);
                    System.out.println("UDP REGISTERED: User " + senderId + " at " + senderAddr);
                }
            }

            // --- B. LOGIC RELAY (CHUYỂN TIẾP) - PHẦN QUAN TRỌNG ĐÃ BỊ THIẾU ---
            // Nếu Client gửi lên Server, nghĩa là họ muốn nhờ Server chuyển giúp (Relay
            // Mode)

            String targetId = video.getTargetId(); // Lấy ID người nhận từ gói tin

            if (targetId != null) {
                // Tra cứu địa chỉ UDP của người nhận
                InetSocketAddress targetAddr = ServerContext.getUdpAddress(targetId);

                if (targetAddr != null) {
                    // Tạo gói tin mới để bắn sang người nhận
                    // Lưu ý: Phải dùng Unpooled.wrappedBuffer để bọc lại mảng byte gốc
                    DatagramPacket relayPacket = new DatagramPacket(
                            Unpooled.wrappedBuffer(data), // Dữ liệu y nguyên
                            targetAddr // Địa chỉ người nhận
                    );

                    ctx.writeAndFlush(relayPacket);
                    // System.out.println("-> Relaying video to " + targetId); // Mở nếu muốn debug
                    // (sẽ rất spam)
                }
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // cause.printStackTrace();
    }
}