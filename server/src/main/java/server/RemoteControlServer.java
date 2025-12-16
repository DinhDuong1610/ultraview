package server;

import codec.NettyKryoDecoder;
import codec.NettyKryoEncoder;
import server.handler.ServerHandler; // <--- Import Handler từ package mới
import server.handler.UdpServerHandler; // <--- Import Handler UDP từ package mới

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

    // Đổi tên từ start() thành run() để khớp với ServerApp
    public void run() throws Exception {
        // BossGroup: Chấp nhận kết nối mới
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        // WorkerGroup: Xử lý dữ liệu (IO)
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            // --- 1. TCP Server (Port 8080) ---
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            // Pipeline xử lý gói tin TCP
                            ch.pipeline().addLast(new NettyKryoDecoder()); // Giải mã Byte -> Object
                            ch.pipeline().addLast(new NettyKryoEncoder()); // Mã hóa Object -> Byte
                            ch.pipeline().addLast(new ServerHandler()); // Logic chính (MVC Controller)
                        }
                    })
                    // Tùy chỉnh TCP
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture fTCP = b.bind(port).sync();
            System.out.println("TCP Server started on port " + port);

            // --- 2. UDP Server (Port 8081) ---
            Bootstrap udpBootstrap = new Bootstrap();
            udpBootstrap.group(workerGroup)
                    .channel(NioDatagramChannel.class)

                    // Tăng bộ đệm nhận dữ liệu để tránh drop gói tin Video lớn
                    .option(ChannelOption.SO_RCVBUF, 1024 * 1024) // Kernel Buffer 1MB
                    .option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(65535)) // Netty Buffer 64KB

                    .handler(new UdpServerHandler()); // Logic xử lý UDP

            ChannelFuture fUDP = udpBootstrap.bind(port + 1).sync();
            System.out.println("UDP Server started on port " + (port + 1));

            // Giữ cho ứng dụng chạy cho đến khi socket đóng
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