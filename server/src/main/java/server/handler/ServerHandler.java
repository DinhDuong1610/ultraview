package server.handler;

import protocol.auth.*;
import protocol.chat.ChatMessage;
import protocol.core.*;
import protocol.media.StartStreamPacket;
import protocol.p2p.PeerInfoPacket;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import server.core.ServerContext;

import java.net.InetSocketAddress;

public class ServerHandler extends SimpleChannelInboundHandler<NetworkPacket> {

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ServerContext.allChannels.add(ctx.channel());
        System.out.println("New Connection: " + ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String userId = ServerContext.getClientIdByChannel(ctx.channel());
        if (userId != null) {
            System.out.println("Client disconnected: " + userId);
            ServerContext.removeClient(userId);

            // Broadcast báo ngắt kết nối cho các máy khác
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
            // Các gói tin cần Broadcast (Gửi cho tất cả trừ mình)
            case CONTROL_SIGNAL:
            case CLIPBOARD_DATA:
            case FILE_REQ:
            case FILE_CHUNK:
            case FILE_OFFER:
            case FILE_ACCEPT:
            case AUDIO_DATA:
                broadcastToOthers(ctx, packet);
                break;
            default:
                System.out.println("Unknown packet: " + packet.getType());
        }
    }

    // --- LOGIC HANDLERS ---

    private void handleLogin(ChannelHandlerContext ctx, LoginRequest req) {
        String ip = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress();
        ServerContext.addClient(req.getUserId(), req.getPassword(), ctx.channel(), ip);

        System.out.println("User Logged in: " + req.getUserId());
        ctx.writeAndFlush(new NetworkPacket(PacketType.LOGIN_RESPONSE, "OK"));
    }

    private void handleConnect(ChannelHandlerContext ctx, ConnectRequestPacket req) {
        String targetId = req.getTargetId();

        // 1. Validate
        if (!ServerContext.isOnline(targetId)) {
            sendConnectResponse(ctx, false, "ID không tồn tại hoặc chưa online!");
            return;
        }
        if (!ServerContext.checkPassword(targetId, req.getTargetPass())) {
            sendConnectResponse(ctx, false, "Sai mật khẩu!");
            return;
        }

        // 2. Success Logic
        sendConnectResponse(ctx, true, "Kết nối thành công!");

        // 3. P2P Handshake (Logic phức tạp đã được gom gọn)
        performP2PHandshake(ctx, targetId);

        // 4. Trigger Stream
        String myId = ServerContext.getClientIdByChannel(ctx.channel());
        Channel targetChannel = ServerContext.getClientChannel(targetId);
        if (targetChannel != null) {
            targetChannel.writeAndFlush(new NetworkPacket(PacketType.START_STREAM, new StartStreamPacket(myId)));
        }
    }

    private void performP2PHandshake(ChannelHandlerContext ctxA, String targetIdB) {
        String idA = ServerContext.getClientIdByChannel(ctxA.channel());

        InetSocketAddress udpA = ServerContext.getUdpAddress(idA);
        InetSocketAddress udpB = ServerContext.getUdpAddress(targetIdB);
        Channel channelB = ServerContext.getClientChannel(targetIdB);

        // Gửi IP của B cho A
        if (udpB != null) {
            System.out.println("P2P: Gửi Info B -> A");
            ctxA.writeAndFlush(new NetworkPacket(PacketType.PEER_INFO,
                    new PeerInfoPacket(udpB.getAddress().getHostAddress(), udpB.getPort())));
        }

        // Gửi IP của A cho B
        if (udpA != null && channelB != null) {
            System.out.println("P2P: Gửi Info A -> B");
            channelB.writeAndFlush(new NetworkPacket(PacketType.PEER_INFO,
                    new PeerInfoPacket(udpA.getAddress().getHostAddress(), udpA.getPort())));
        }
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

    private void sendConnectResponse(ChannelHandlerContext ctx, boolean success, String msg) {
        ctx.writeAndFlush(new NetworkPacket(PacketType.CONNECT_RESPONSE, new ConnectResponsePacket(success, msg)));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}