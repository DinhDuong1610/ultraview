package server.handler;

import codec.KryoSerializer;
import protocol.media.VideoPacket;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import server.core.ServerContext;

import java.net.InetSocketAddress;

public class UdpServerHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private final java.util.concurrent.ConcurrentHashMap<String, Integer> udpCounters = new java.util.concurrent.ConcurrentHashMap<>();

    private void logUdpRelay(VideoPacket video, InetSocketAddress targetAddr, int bytes) {
        String sender = video.getSenderId();
        int c = udpCounters.merge(sender, 1, Integer::sum);

        if (c % 30 == 0) {
            String partner = ServerContext.getPartner(sender);
            System.out.printf("[UDP-RELAY] sender=%s partner=%s -> %s bytes=%d count=%d%n",
                    sender, partner, targetAddr, bytes, c);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
        byte[] data = new byte[packet.content().readableBytes()];
        packet.content().readBytes(data);

        Object obj;
        try {
            obj = KryoSerializer.deserialize(data);
        } catch (Exception e) {
            return;
        }

        if (obj instanceof VideoPacket) {
            VideoPacket video = (VideoPacket) obj;
            String senderId = video.getSenderId();

            if (senderId != null) {
                InetSocketAddress senderAddr = packet.sender();
                InetSocketAddress oldAddr = ServerContext.getUdpAddress(senderId);

                if (oldAddr == null || !oldAddr.equals(senderAddr)) {
                    ServerContext.registerUdp(senderId, senderAddr);
                    System.out.println("UDP REGISTERED: User " + senderId + " at " + senderAddr);
                }
            }

            // target thật = partner đã được server pair
            String targetId = ServerContext.getPartner(senderId);
            if (targetId == null)
                return;

            InetSocketAddress targetAddr = ServerContext.getUdpAddress(targetId);
            if (targetAddr == null)
                return;

            // relay
            DatagramPacket relayPacket = new DatagramPacket(
                    Unpooled.wrappedBuffer(data),
                    targetAddr);
            logUdpRelay(video, targetAddr, data.length);
            ctx.writeAndFlush(relayPacket);

        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // cause.printStackTrace();
    }
}