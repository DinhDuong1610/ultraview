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

            String targetId = video.getTargetId();

            if (targetId != null) {
                InetSocketAddress targetAddr = ServerContext.getUdpAddress(targetId);

                if (targetAddr != null) {
                    DatagramPacket relayPacket = new DatagramPacket(
                            Unpooled.wrappedBuffer(data),
                            targetAddr);

                    ctx.writeAndFlush(relayPacket);
                    System.out.println("-> Relaying data to " + targetId);
                }
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // cause.printStackTrace();
    }
}