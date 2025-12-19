package server;

import codec.NettyKryoDecoder;
import codec.NettyKryoEncoder;
import server.handler.ServerHandler;
import server.handler.UdpServerHandler;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.FixedRecvByteBufAllocator;

public class RemoteControlServer {

    private final int port;

    public RemoteControlServer(int port) {
        this.port = port;
    }

    public void run() throws Exception {
        // BossGroup: Chấp nhận kết nối mới
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        // WorkerGroup: Xử lý dữ liệu (IO)
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            // --- 1. TCP Server ---
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            // Pipeline xử lý gói tin TCP
                            ch.pipeline().addLast(new NettyKryoDecoder());
                            ch.pipeline().addLast(new NettyKryoEncoder());
                            ch.pipeline().addLast(new ServerHandler());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture fTCP = b.bind(port).sync();
            System.out.println("TCP Server started on port " + port);

            // --- 2. UDP Server ---
            Bootstrap udpBootstrap = new Bootstrap();
            udpBootstrap.group(workerGroup)
                    .channel(NioDatagramChannel.class)
                    .option(ChannelOption.SO_RCVBUF, 1024 * 1024)
                    .option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(65535))
                    .handler(new UdpServerHandler());

            ChannelFuture fUDP = udpBootstrap.bind(port + 1).sync();
            System.out.println("UDP Server started on port " + (port + 1));

            fTCP.channel().closeFuture().sync();
            fUDP.channel().closeFuture().sync();

        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception {
        int port = 8080;
        new RemoteControlServer(port).run();
    }
}