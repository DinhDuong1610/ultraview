package server;

import codec.NettyKryoDecoder;
import codec.NettyKryoEncoder;
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

    public void start() throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            // --- 1. TCP Server (Giữ nguyên code cũ) ---
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(new NettyKryoDecoder());
                            ch.pipeline().addLast(new NettyKryoEncoder());
                            ch.pipeline().addLast(new ServerHandler());
                        }
                    });

            ChannelFuture fTCP = b.bind(port).sync(); // Port 8080
            System.out.println("TCP Server started on port " + port);

            // --- 2. UDP Server (Thêm mới) ---
            // UDP dùng Bootstrap (không phải ServerBootstrap) vì không có kết nối con
            Bootstrap udpBootstrap = new Bootstrap();
            udpBootstrap.group(workerGroup)
                    .channel(NioDatagramChannel.class)

                    // THÊM DÒNG NÀY ĐỂ MỞ RỘNG BỘ NHỚ NHẬN UDP LÊN 65KB
                    .option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(65535))

                    .handler(new UdpServerHandler());

            ChannelFuture fUDP = udpBootstrap.bind(port + 1).sync();
            System.out.println("UDP Server started on port " + (port + 1));

            // Chờ cả 2 cổng đóng
            fTCP.channel().closeFuture().sync();
            fUDP.channel().closeFuture().sync();

        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception {
        int port = 8080; // Bạn có thể đổi port nếu muốn
        new RemoteControlServer(port).start();
    }
}