package server;

import protocol.ChatMessage;
import protocol.ConnectRequestPacket;
import protocol.ConnectResponsePacket;
import protocol.DisconnectPacket;
import protocol.LoginRequest;
import protocol.NetworkPacket;
import protocol.PacketType;
import protocol.PeerInfoPacket;
import protocol.StartStreamPacket;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// SimpleChannelInboundHandler tự động giải phóng bộ nhớ sau khi đọc xong
public class ServerHandler extends SimpleChannelInboundHandler<NetworkPacket> {

    private static final Map<String, Channel> clients = new ConcurrentHashMap<>();
    // Map lưu Mật khẩu (để xác thực)
    private static final Map<String, String> passwords = new ConcurrentHashMap<>();

    // ChannelGroup giúp quản lý tự động việc client ngắt kết nối thì xóa khỏi nhóm
    private static final ChannelGroup allChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // Khi có client mới kết nối (TCP handshake thành công)
        allChannels.add(ctx.channel());
        System.out.println("Client connected: " + ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String userId = getClientId(ctx.channel());

        if (userId != null) {
            System.out.println("Client disconnected: " + userId);

            // 1. Xóa khỏi danh sách online
            clients.remove(userId);
            passwords.remove(userId);

            // 2. Báo cho tất cả các client khác biết
            NetworkPacket packet = new NetworkPacket(PacketType.DISCONNECT_NOTICE, new DisconnectPacket(userId));
            for (Channel ch : clients.values()) {
                ch.writeAndFlush(packet);
            }

            updateClientListUI(userId, "", false);
        }
        super.channelInactive(ctx);
    }

    private void updateClientListUI(String userId, String ip, boolean isAdd) {
        javafx.application.Platform.runLater(() -> {
            if (isAdd) {
                server.ServerApp.connectedClients.add(new server.ServerApp.ClientModel(userId, ip, "Online"));
            } else {
                server.ServerApp.connectedClients.removeIf(c -> c.getId().equals(userId));
            }
        });
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, NetworkPacket packet) throws Exception {
        // Xử lý gói tin dựa trên Type
        switch (packet.getType()) {
            case LOGIN_REQUEST:
                LoginRequest login = (LoginRequest) packet.getPayload();
                clients.put(login.getUserId(), ctx.channel());

                // LƯU PASSWORD
                passwords.put(login.getUserId(), login.getPassword());

                // IP Address lấy từ Channel
                String clientIp = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress();

                System.out.println("User logged in: " + login.getUserId() + " from " + clientIp);

                // CẬP NHẬT UI SERVER
                updateClientListUI(login.getUserId(), clientIp, true);

                System.out.println("User logged in: " + login.getUserId() + " | Pass: " + login.getPassword());
                ctx.writeAndFlush(new NetworkPacket(PacketType.LOGIN_RESPONSE, "OK"));
                break;

            case CONNECT_REQUEST:
                handleConnectRequest(ctx, packet);
                break;
            case CHAT_MESSAGE:
                handleChat(ctx, packet);
                break;
            case CONTROL_SIGNAL:
                handleControl(ctx, packet);
                break;
            case CLIPBOARD_DATA:
                handleClipboard(ctx, packet);
                break;
            case FILE_REQ:
            case FILE_CHUNK:
                forwardFilePacket(ctx, packet);
                break;
            case FILE_OFFER: // Chuyển tiếp lời mời file
            case FILE_ACCEPT: // Chuyển tiếp chấp nhận tải
                // Dùng chung hàm forward (chuyển tiếp cho người kia)
                forwardPacket(ctx, packet);
                break;
            case AUDIO_DATA: // <--- THÊM MỚI
                forwardPacket(ctx, packet);
                break;
            default:
                System.out.println("Unknown packet type: " + packet.getType());
        }
    }

    private void handleConnectRequest(ChannelHandlerContext ctx, NetworkPacket packet) {
        ConnectRequestPacket req = (ConnectRequestPacket) packet.getPayload();
        String targetId = req.getTargetId();
        String inputPass = req.getTargetPass();

        // 1. Kiểm tra ID có tồn tại không
        if (!clients.containsKey(targetId)) {
            ctx.writeAndFlush(new NetworkPacket(PacketType.CONNECT_RESPONSE,
                    new ConnectResponsePacket(false, "ID không tồn tại hoặc chưa online!")));
            return;
        }

        // 2. Kiểm tra Mật khẩu
        String realPass = passwords.get(targetId);
        if (realPass == null || !realPass.equals(inputPass)) {
            ctx.writeAndFlush(new NetworkPacket(PacketType.CONNECT_RESPONSE,
                    new ConnectResponsePacket(false, "Sai mật khẩu!")));
            return;
        }

        // 3. THÀNH CÔNG -> Xử lý 2 việc:

        // Việc A: Báo cho người điều khiển (B) biết là OK
        ctx.writeAndFlush(new NetworkPacket(PacketType.CONNECT_RESPONSE,
                new ConnectResponsePacket(true, "Kết nối thành công!")));

        // --- [LOGIC P2P MỚI: TRAO ĐỔI 2 CHIỀU] ---

        // Lấy thông tin UDP của cả 2 máy
        String userIdA = getClientId(ctx.channel()); // Người điều khiển
        String userIdB = targetId; // Người bị điều khiển

        InetSocketAddress udpA = UdpServerHandler.udpClients.get(userIdA);
        InetSocketAddress udpB = UdpServerHandler.udpClients.get(userIdB);

        // 1. Gửi IP của B cho A (Để A biết B ở đâu - dù A chủ yếu nhận)
        if (udpB != null) {
            System.out.println("P2P: Gửi IP của B (" + udpB + ") cho A.");
            PeerInfoPacket infoB = new PeerInfoPacket(udpB.getAddress().getHostAddress(), udpB.getPort());
            ctx.writeAndFlush(new NetworkPacket(PacketType.PEER_INFO, infoB));
        }

        // 2. [QUAN TRỌNG] Gửi IP của A cho B (Để B biết đường bắn Video thẳng sang A)
        Channel channelB = clients.get(userIdB); // Lấy kết nối TCP của B
        if (channelB != null && channelB.isActive() && udpA != null) {
            System.out.println("P2P: Gửi IP của A (" + udpA + ") cho B.");
            PeerInfoPacket infoA = new PeerInfoPacket(udpA.getAddress().getHostAddress(), udpA.getPort());
            channelB.writeAndFlush(new NetworkPacket(PacketType.PEER_INFO, infoA));
        } else {
            System.err.println("P2P WARN: Không thể gửi IP của A cho B (Do B mất kết nối hoặc A chưa ĐK UDP).");
        }
        // ----------------------------------------

        // Việc B: Ra lệnh cho máy bị điều khiển (A) bắt đầu Share màn hình tới B
        // Lưu ý: Gửi ID của người điều khiển (người gửi packet này) cho A biết
        String controllerId = getClientId(ctx.channel()); // Hàm phụ trợ lấy ID từ channel

        Channel targetChannel = clients.get(targetId);
        targetChannel.writeAndFlush(new NetworkPacket(PacketType.START_STREAM,
                new StartStreamPacket(controllerId)));
    }

    // Hàm phụ trợ để tìm ID từ Channel (cần duyệt map, hơi chậm tí nhưng OK cho
    // demo)
    private String getClientId(Channel channel) {
        for (Map.Entry<String, Channel> entry : clients.entrySet()) {
            if (entry.getValue() == channel)
                return entry.getKey();
        }
        return "Unknown";
    }

    private void handleChat(ChannelHandlerContext ctx, NetworkPacket packet) {
        if (packet.getPayload() instanceof ChatMessage) {
            ChatMessage chatMsg = (ChatMessage) packet.getPayload();
            String targetId = chatMsg.getReceiverId();

            if (targetId != null && clients.containsKey(targetId)) {
                // Tìm thấy người nhận -> Chuyển tiếp gói tin
                Channel targetChannel = clients.get(targetId);
                targetChannel.writeAndFlush(packet);
                System.out.println("Forwarded chat from " + chatMsg.getSenderId() + " to " + targetId);
            } else {
                System.out.println("Receiver not found: " + targetId);
            }
        }
    }

    private void handleControl(ChannelHandlerContext ctx, NetworkPacket packet) {
        // Gói tin này cần được chuyển tới máy bị điều khiển
        // Nhưng NetworkPacket hiện tại chưa có trường "targetId".
        // ĐỂ ĐƠN GIẢN: Ta sẽ tạm thời gửi Broadcast hoặc Client phải gửi kèm target.

        // Cách sửa nhanh nhất: Ta sẽ mượn tạm logic Chat để định tuyến,
        // hoặc nâng cấp NetworkPacket. Nhưng để không sửa nhiều code cũ:
        // Ta quy ước: ControlPayload sẽ được gửi kèm 1 gói tin CHAT_MESSAGE đặc biệt?
        // KHÔNG, cách đó tà đạo quá.

        // GIẢI PHÁP CHUẨN: Client A và Client B đã biết nhau qua phiên UDP.
        // Server TCP cũng nên biết A đang kết nối B.
        // Nhưng để nhanh, ta sẽ Broadcast gói tin này cho tất cả (trừ người gửi).
        // (Đây là giải pháp tạm thời cho demo 2 máy).

        for (Channel ch : clients.values()) {
            if (ch != ctx.channel()) { // Không gửi lại cho chính mình
                ch.writeAndFlush(packet);
            }
        }
    }

    private void handleClipboard(ChannelHandlerContext ctx, NetworkPacket packet) {
        // Chuyển tiếp cho các máy khác (trừ chính mình)
        for (Channel ch : clients.values()) {
            if (ch != ctx.channel()) {
                ch.writeAndFlush(packet);
            }
        }
    }

    private void forwardFilePacket(ChannelHandlerContext ctx, NetworkPacket packet) {
        for (Channel ch : clients.values()) {
            if (ch != ctx.channel()) {
                ch.writeAndFlush(packet);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Khi có lỗi xảy ra (ví dụ client tắt ngang xương)
        cause.printStackTrace();
        ctx.close();
    }

    // Hàm chuyển tiếp (Broadcast cho đối tác)
    private void forwardPacket(ChannelHandlerContext ctx, NetworkPacket packet) {
        // Cách đơn giản nhất cho 2 máy: Gửi cho tất cả client khác trừ người gửi
        for (Channel ch : clients.values()) {
            if (ch != ctx.channel()) {
                ch.writeAndFlush(packet);
            }
        }
    }

}