package client.network.p2p;

import codec.NettyKryoDecoder;
import codec.NettyKryoEncoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.net.InetSocketAddress;

public class PeerControlServer {
    private final SessionState sessionState;

    private EventLoopGroup boss;
    private EventLoopGroup worker;
    private Channel serverCh;
    private int port;

    public PeerControlServer(SessionState sessionState) {
        this.sessionState = sessionState;
    }

    public int start() throws InterruptedException {
        boss = new NioEventLoopGroup(1);
        worker = new NioEventLoopGroup();

        ServerBootstrap b = new ServerBootstrap();
        b.group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new NettyKryoDecoder());
                        p.addLast(new NettyKryoEncoder());
                        p.addLast(new PeerControlHandler(sessionState));
                    }
                })
                .childOption(ChannelOption.TCP_NODELAY, true);

        serverCh = b.bind(0).sync().channel();
        port = ((InetSocketAddress) serverCh.localAddress()).getPort();
        return port;
    }

    public int getPort() {
        return port;
    }

    public void stop() {
        try {
            if (serverCh != null)
                serverCh.close();
        } finally {
            if (boss != null)
                boss.shutdownGracefully();
            if (worker != null)
                worker.shutdownGracefully();
        }
    }
}
