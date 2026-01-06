package server.handler;

import protocol.auth.*;
import protocol.chat.ChatMessage;
import protocol.core.*;
import protocol.media.StartStreamPacket;
import protocol.p2p.PeerInfoPacket;
import protocol.p2p.PeerRegisterPacket;
import java.util.UUID;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import server.core.ServerContext;

import java.net.InetSocketAddress;

public class ServerHandler extends SimpleChannelInboundHandler<NetworkPacket> {

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ServerContext.allChannels.add(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String userId = ServerContext.getClientIdByChannel(ctx.channel());
        if (userId != null) {
            System.out.println("Client disconnected: " + userId);

            // 1) unpair và lấy partner (nếu có)
            String partnerId = ServerContext.unpair(userId);

            // 2) remove user
            ServerContext.removeClient(userId);

            // 3) báo partner (ưu tiên báo trực tiếp)
            if (partnerId != null) {
                Channel partnerCh = ServerContext.getClientChannel(partnerId);
                if (partnerCh != null) {
                    partnerCh.writeAndFlush(new NetworkPacket(
                            PacketType.DISCONNECT_NOTICE,
                            new DisconnectPacket(userId)));
                }
            }

            // (tuỳ bạn) broadcast để UI server/client update danh sách
            NetworkPacket packet = new NetworkPacket(PacketType.DISCONNECT_NOTICE, new DisconnectPacket(userId));
            ServerContext.allChannels.writeAndFlush(packet);
        }
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, NetworkPacket packet) throws Exception {
        switch (packet.getType()) {
            case LOGIN_REQUEST:
                handleLogin(ctx, (LoginRequest) packet.getPayload());
                break;
            case CONNECT_REQUEST:
                handleConnect(ctx, (ConnectRequestPacket) packet.getPayload());
                break;
            case CHAT_MESSAGE:
                handleForward(ctx, packet, ((ChatMessage) packet.getPayload()).getReceiverId());
                break;
            case PEER_REGISTER:
                PeerRegisterPacket reg = (PeerRegisterPacket) packet.getPayload();
                String uid = ServerContext.getClientIdByChannel(ctx.channel());
                if (uid != null) {
                    ServerContext.setControlPort(uid, reg.getControlPort());
                    System.out.println("Peer control port registered: " + uid + " -> " + reg.getControlPort());
                }
                break;
            case CONTROL_SIGNAL:
            case CLIPBOARD_DATA:
            case FILE_REQ:
            case FILE_CHUNK:
            case FILE_OFFER:
            case FILE_ACCEPT:
            case AUDIO_DATA:
                forwardToPartner(ctx, packet);
                break;
            default:
                System.out.println("Unknown packet: " + packet.getType());
        }
    }

    private void handleLogin(ChannelHandlerContext ctx, LoginRequest req) {
        if (ServerContext.isOnline(req.getUserId())) {
            // ID đã online -> từ chối
            ctx.writeAndFlush(new NetworkPacket(PacketType.LOGIN_RESPONSE, "DUPLICATE_ID"));
            ctx.close();
            return;
        }

        String ip = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress();
        ServerContext.addClient(req.getUserId(), req.getPassword(), ctx.channel(), ip);
        System.out.println("User Login: " + req.getUserId());
        ctx.writeAndFlush(new NetworkPacket(PacketType.LOGIN_RESPONSE, "OK"));
    }

    private void handleConnect(ChannelHandlerContext ctx, ConnectRequestPacket req) {
        String targetId = req.getTargetId();

        if (!ServerContext.isOnline(targetId)) {
            sendConnectResponse(ctx, false, "ID không tồn tại hoặc chưa online!", null, null, 0);
            return;
        }
        if (!ServerContext.checkPassword(targetId, req.getTargetPass())) {
            sendConnectResponse(ctx, false, "Sai mật khẩu!", null, null, 0);
            return;
        }

        String myId = ServerContext.getClientIdByChannel(ctx.channel());
        if (myId == null) {
            sendConnectResponse(ctx, false, "Bạn chưa đăng nhập hợp lệ!", null, null, 0);
            return;
        }

        Integer targetControlPort = ServerContext.getControlPort(targetId);
        if (targetControlPort == null || targetControlPort <= 0) {
            sendConnectResponse(ctx, false, "Target chưa sẵn sàng P2P control (chưa register port)!", null, null, 0);
            return;
        }

        // tạo sessionId
        String sessionId = UUID.randomUUID().toString();

        // pair 1-1
        if (!ServerContext.pair(myId, targetId, sessionId)) {
            sendConnectResponse(ctx, false, "Một trong hai ID đang bận hoặc đã kết nối!", null, null, 0);
            return;
        }

        // peerHost = IP nhìn thấy từ server (LAN/VPN OK)
        Channel targetCh = ServerContext.getClientChannel(targetId);
        String peerHost = ((InetSocketAddress) targetCh.remoteAddress()).getAddress().getHostAddress();

        // trả cho controller A
        sendConnectResponse(ctx, true, "Kết nối thành công!", sessionId, peerHost, targetControlPort);

        // báo target B bắt đầu stream + nhận sessionId
        targetCh.writeAndFlush(new NetworkPacket(PacketType.START_STREAM,
                new StartStreamPacket(myId, sessionId)));

        // giữ nguyên P2P UDP handshake (video)
        performP2PHandshake(ctx, targetId);
    }

    private void performP2PHandshake(ChannelHandlerContext ctxA, String targetIdB) {
        String idA = ServerContext.getClientIdByChannel(ctxA.channel());

        InetSocketAddress udpA = ServerContext.getUdpAddress(idA);
        InetSocketAddress udpB = ServerContext.getUdpAddress(targetIdB);
        Channel channelB = ServerContext.getClientChannel(targetIdB);

        if (udpB != null) {
            ctxA.writeAndFlush(new NetworkPacket(PacketType.PEER_INFO,
                    new PeerInfoPacket(udpB.getAddress().getHostAddress(), udpB.getPort())));
        }

        if (udpA != null && channelB != null) {
            channelB.writeAndFlush(new NetworkPacket(PacketType.PEER_INFO,
                    new PeerInfoPacket(udpA.getAddress().getHostAddress(), udpA.getPort())));
        }
    }

    private void forwardToPartner(ChannelHandlerContext ctx, NetworkPacket packet) {
        String senderId = ServerContext.getClientIdByChannel(ctx.channel());
        if (senderId == null)
            return;

        String partnerId = ServerContext.getPartner(senderId);
        if (partnerId == null)
            return;

        Channel partnerCh = ServerContext.getClientChannel(partnerId);
        if (partnerCh != null)
            partnerCh.writeAndFlush(packet);
    }

    private void handleForward(ChannelHandlerContext ctx, NetworkPacket packet, String targetId) {
        Channel target = ServerContext.getClientChannel(targetId);
        if (target != null) {
            target.writeAndFlush(packet);
        }
    }

    private void broadcastToOthers(ChannelHandlerContext ctx, NetworkPacket packet) {
        for (Channel ch : ServerContext.allChannels) {
            if (ch != ctx.channel()) {
                ch.writeAndFlush(packet);
            }
        }
    }

    private void sendConnectResponse(ChannelHandlerContext ctx, boolean success, String msg,
            String sessionId, String peerHost, int peerPort) {
        ctx.writeAndFlush(new NetworkPacket(PacketType.CONNECT_RESPONSE,
                new ConnectResponsePacket(success, msg, sessionId, peerHost, peerPort)));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // cause.printStackTrace();
        ctx.close();
    }
}