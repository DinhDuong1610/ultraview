package client.network.p2p;

import codec.NettyKryoDecoder;
import codec.NettyKryoEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import protocol.core.NetworkPacket;

public class PeerControlClient {
    private EventLoopGroup group;
    private Channel ch;

    public void connect(String host, int port) throws InterruptedException {
        group = new NioEventLoopGroup();
        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new NettyKryoDecoder());
                        p.addLast(new NettyKryoEncoder());
                    }
                })
                .option(ChannelOption.TCP_NODELAY, true);

        ch = b.connect(host, port).sync().channel();
    }

    public boolean isActive() {
        return ch != null && ch.isActive();
    }

    public void send(NetworkPacket packet) {
        if (isActive())
            ch.writeAndFlush(packet);
    }

    public void close() {
        try {
            if (ch != null)
                ch.close();
        } finally {
            if (group != null)
                group.shutdownGracefully();
        }
    }
}
