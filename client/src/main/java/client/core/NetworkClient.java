package client.core;

import java.io.File;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import codec.KryoSerializer;
import codec.NettyKryoDecoder;
import codec.NettyKryoEncoder;
import protocol.AudioPacket;
import protocol.ChatMessage;
import protocol.ClipboardPacket;
import protocol.ConnectRequestPacket;
import protocol.ControlPayload;
import protocol.FileAcceptPacket;
import protocol.FileChunkPacket;
import protocol.FileOfferPacket;
import protocol.FileReqPacket;
import protocol.LoginRequest;
import protocol.NetworkPacket;
import protocol.PacketType;
import protocol.VideoPacket;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

public class NetworkClient {

    private String host;
    private int port;
    private Channel tcpChannel; // Đổi tên biến channel cũ thành tcpChannel cho rõ
    private Channel udpChannel; // Kênh UDP mới
    private Map<String, File> pendingFiles = new ConcurrentHashMap<>();

    // Biến lưu địa chỉ P2P của đối tác
    private InetSocketAddress peerAddress;

    // Cờ bật/tắt chế độ P2P (Để sau này làm nút giả lập lỗi)
    private volatile boolean p2pEnabled = true;

    public NetworkClient(String host, int port) {
        this.host = host;
        this.port = port;

        ClientHandler.onPeerInfoReceived = (peerPacket) -> {
            this.peerAddress = new InetSocketAddress(peerPacket.getHost(), peerPacket.getPort());
            System.out.println(">>> [SUCCESS] Đã nhận được địa chỉ P2P của đối tác: " + this.peerAddress);
        };
    }

    public void connect(String userId, String password) {
        // Chạy kết nối trong luồng riêng để không treo UI
        new Thread(() -> {
            EventLoopGroup group = new NioEventLoopGroup();
            try {
                Bootstrap b = new Bootstrap();
                b.group(group)
                        .channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            public void initChannel(SocketChannel ch) {
                                // Pipeline giống hệt Server: Decoder -> Encoder -> Handler
                                ch.pipeline().addLast(new NettyKryoDecoder());
                                ch.pipeline().addLast(new NettyKryoEncoder());
                                ch.pipeline().addLast(new ClientHandler());
                            }
                        });

                // Kết nối đến server
                ChannelFuture f = b.connect(host, port).sync();
                tcpChannel = f.channel();
                System.out.println("Connected to server!");

                // Gửi ngay gói tin Login sau khi kết nối
                sendLogin(userId, password);

                // Giữ kết nối
                f.channel().closeFuture().sync();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                group.shutdownGracefully();
            }
        }).start();

        connectUdp(userId);
    }

    private void connectUdp(String deviceId) {
        new Thread(() -> {
            EventLoopGroup group = new NioEventLoopGroup();
            try {
                Bootstrap b = new Bootstrap();
                b.group(group)
                        .channel(NioDatagramChannel.class)

                        // --- CẤU HÌNH QUAN TRỌNG (FIX LỖI 2048 BYTES) ---
                        // 1. Tăng bộ nhớ đệm của hệ điều hành (Kernel buffer) lên 1MB
                        .option(ChannelOption.SO_RCVBUF, 1024 * 1024)

                        // 2. Tăng bộ nhớ đệm của Netty (Application buffer) lên 65KB
                        // Đây là dòng quyết định việc Netty có đọc hết gói tin hay không
                        .option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(65535))
                        // ------------------------------------------------

                        .handler(new UdpClientHandler());

                ChannelFuture f = b.bind(0).sync();
                udpChannel = f.channel();

                int localPort = ((InetSocketAddress) udpChannel.localAddress()).getPort();
                System.out.println("UDP Client bound to local port: " + localPort);

                // Gửi tin đăng ký
                Thread.sleep(500);
                System.out.println("Sending UDP Registration packet for ID: " + deviceId);
                sendVideoPacket(new VideoPacket(deviceId, null, new byte[0], 0, 0, 0, 0));

                f.channel().closeFuture().sync();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                group.shutdownGracefully();
            }
        }).start();
    }

    public void setP2PEnabled(boolean enabled) {
        this.p2pEnabled = enabled;
        System.out.println(">>> Switch Mode: " + (enabled ? "P2P" : "RELAY"));
    }

    public void requestControl(String targetId, String targetPass) {
        if (tcpChannel != null && tcpChannel.isActive()) {
            ConnectRequestPacket req = new ConnectRequestPacket(targetId, targetPass);
            tcpChannel.writeAndFlush(new NetworkPacket(PacketType.CONNECT_REQUEST, req));
        }
    }

    private void sendLogin(String userId, String password) {
        LoginRequest login = new LoginRequest(userId, "My Computer");
        NetworkPacket packet = new NetworkPacket(PacketType.LOGIN_REQUEST, new LoginRequest(userId, password));
        tcpChannel.writeAndFlush(packet);
    }

    public void sendChat(String senderId, String targetId, String message) {
        if (tcpChannel != null && tcpChannel.isActive()) {
            ChatMessage chat = new ChatMessage(senderId, targetId, message);
            NetworkPacket packet = new NetworkPacket(PacketType.CHAT_MESSAGE, chat);
            tcpChannel.writeAndFlush(packet);
        }
    }

    // public void sendVideoPacket(VideoPacket packet) {
    // if (udpChannel != null && udpChannel.isActive()) {
    // try {
    // byte[] bytes = KryoSerializer.serialize(packet);
    // if (bytes.length > 60000) {
    // /* ... */ return;
    // }

    // // --- SỬA ĐOẠN NÀY ---
    // // Đảm bảo host là địa chỉ IP chính xác mà Server đang bind (127.0.0.1 nếu
    // test
    // // local)
    // // Nếu bạn truyền "localhost", hãy thử đổi thành "127.0.0.1" khi khởi tạo
    // // NetworkClient
    // DatagramPacket datagram = new DatagramPacket(
    // io.netty.buffer.Unpooled.wrappedBuffer(bytes),
    // new InetSocketAddress(host, port + 1) // Port 8081
    // );
    // // --------------------

    // udpChannel.writeAndFlush(datagram);
    // } catch (Exception e) {
    // e.printStackTrace();
    // }
    // }
    // }

    public void sendVideoPacket(VideoPacket packet) {
        if (udpChannel != null && udpChannel.isActive()) {
            try {
                byte[] bytes = KryoSerializer.serialize(packet);
                if (bytes.length > 60000)
                    return;

                InetSocketAddress target;

                // LOGIC CHUYỂN MẠCH THÔNG MINH
                // --- THÊM LOG DEBUG TẠI ĐÂY ---
                if (p2pEnabled && peerAddress != null) {
                    target = peerAddress;
                    System.out.println("DEBUG: Gửi P2P tới " + target); // Mở cái này nếu muốn soi kỹ
                } else {
                    target = new InetSocketAddress(host, port + 1);

                    System.out.println("DEBUG: Fallback to Server (Vì chưa có địa chỉ Peer)");

                }

                DatagramPacket datagram = new DatagramPacket(
                        io.netty.buffer.Unpooled.wrappedBuffer(bytes),
                        target);

                udpChannel.writeAndFlush(datagram);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void sendControl(ControlPayload payload) {
        if (tcpChannel != null && tcpChannel.isActive()) {
            NetworkPacket packet = new NetworkPacket(PacketType.CONTROL_SIGNAL, payload);
            tcpChannel.writeAndFlush(packet);
        }
    }

    public void sendClipboard(String text) {
        if (tcpChannel != null && tcpChannel.isActive()) {
            NetworkPacket packet = new NetworkPacket(PacketType.CLIPBOARD_DATA, new ClipboardPacket(text));
            tcpChannel.writeAndFlush(packet);
        }
    }

    public void sendFile(File file) {
        if (tcpChannel == null || !tcpChannel.isActive())
            return;

        new Thread(() -> {
            try {
                // 1. Gửi gói tin mở đầu (Header)
                System.out.println("Starting file transfer: " + file.getName());
                FileReqPacket req = new FileReqPacket(file.getName(), file.length());
                tcpChannel.writeAndFlush(new NetworkPacket(PacketType.FILE_REQ, req));

                // 2. Mở file và cắt nhỏ
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192]; // Cắt mỗi miếng 8KB
                    int bytesRead;

                    while ((bytesRead = fis.read(buffer)) != -1) {
                        // Copy dữ liệu thực vào mảng mới (nếu miếng cuối < 8KB)
                        byte[] chunkData = new byte[bytesRead];
                        System.arraycopy(buffer, 0, chunkData, 0, bytesRead);

                        boolean isLast = (fis.available() == 0);

                        FileChunkPacket chunk = new FileChunkPacket(chunkData, bytesRead, isLast);
                        tcpChannel.writeAndFlush(new NetworkPacket(PacketType.FILE_CHUNK, chunk));

                        // Nghỉ 1ms để tránh làm ngập mạng (Flow control đơn giản)
                        Thread.sleep(1);
                    }
                }
                System.out.println("File sent successfully!");

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // HÀM 1: Gửi lời mời (Offer) - Được gọi khi bấm nút Đính kèm
    public void sendFileOffer(File file) {
        if (tcpChannel == null || !tcpChannel.isActive())
            return;

        // Lưu lại để tí nữa gửi thật
        pendingFiles.put(file.getName(), file);

        // Gửi gói tin chào mời
        FileOfferPacket offer = new FileOfferPacket(file.getName(), file.length());
        tcpChannel.writeAndFlush(new NetworkPacket(PacketType.FILE_OFFER, offer));
    }

    // HÀM 2: Gửi nội dung thật (Stream) - Được gọi khi nhận FILE_ACCEPT
    public void startFileStream(String fileName) {
        File file = pendingFiles.get(fileName);
        if (file == null || !file.exists())
            return;

        // Gọi lại logic gửi chunk cũ (bạn copy hàm sendFile cũ vào đây và đổi tên)
        new Thread(() -> {
            try {
                // 1. Gửi Header
                FileReqPacket req = new FileReqPacket(file.getName(), file.length());
                tcpChannel.writeAndFlush(new NetworkPacket(PacketType.FILE_REQ, req));

                // 2. Gửi Chunk
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        byte[] chunkData = new byte[bytesRead];
                        System.arraycopy(buffer, 0, chunkData, 0, bytesRead);
                        boolean isLast = (fis.available() == 0);

                        FileChunkPacket chunk = new FileChunkPacket(chunkData, bytesRead, isLast);
                        tcpChannel.writeAndFlush(new NetworkPacket(PacketType.FILE_CHUNK, chunk));
                        Thread.sleep(1);
                    }
                }
                System.out.println("File sent: " + fileName);
                // Xóa khỏi danh sách chờ sau khi gửi xong
                pendingFiles.remove(fileName);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // HÀM 3: Gửi tín hiệu chấp nhận (Accept) - Người nhận gọi hàm này
    public void sendFileAccept(String fileName) {
        if (tcpChannel != null) {
            tcpChannel.writeAndFlush(new NetworkPacket(PacketType.FILE_ACCEPT, new FileAcceptPacket(fileName)));
        }
    }

    public void sendAudio(byte[] data, int length) {
        if (tcpChannel != null && tcpChannel.isActive()) {
            // Copy mảng byte chính xác theo độ dài để tránh gửi dữ liệu rác
            byte[] exactData = new byte[length];
            System.arraycopy(data, 0, exactData, 0, length);

            AudioPacket audio = new AudioPacket(exactData, length);
            tcpChannel.writeAndFlush(new NetworkPacket(PacketType.AUDIO_DATA, audio));
        }
    }

}
