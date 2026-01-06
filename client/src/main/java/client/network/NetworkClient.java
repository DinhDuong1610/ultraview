package client.network;

import client.network.handler.ClientHandler;
import client.network.handler.UdpClientHandler;
import codec.NettyKryoDecoder;
import codec.NettyKryoEncoder;
import codec.KryoSerializer; // Import Serializer

// Import các gói tin từ cấu trúc mới
import protocol.auth.*;
import protocol.chat.ChatMessage;
import protocol.core.NetworkPacket;
import protocol.core.PacketType;
import protocol.input.ClipboardPacket;
import protocol.input.ControlPayload;
import protocol.media.AudioPacket;
import protocol.media.VideoPacket;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import client.network.p2p.PeerControlClient;
import protocol.p2p.P2PHelloPacket;
import protocol.p2p.PeerRegisterPacket;

import java.net.InetSocketAddress;

public class NetworkClient {

    private String host;
    private int port;
    private Channel tcpChannel;
    private Channel udpChannel;
    private String myId;
    private final PeerControlClient peerControlClient = new PeerControlClient();

    private InetSocketAddress peerAddress;
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
        this.myId = userId;

        new Thread(() -> {
            EventLoopGroup group = new NioEventLoopGroup();
            try {
                Bootstrap b = new Bootstrap();
                b.group(group)
                        .channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            public void initChannel(SocketChannel ch) {
                                ch.pipeline().addLast(new NettyKryoDecoder());
                                ch.pipeline().addLast(new NettyKryoEncoder());
                                ch.pipeline().addLast(new ClientHandler());
                            }
                        });

                ChannelFuture f = b.connect(host, port).sync();
                tcpChannel = f.channel();
                System.out.println("Connected to TCP server!");

                sendLogin(userId, password);

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
                        .option(ChannelOption.SO_RCVBUF, 1024 * 1024)
                        .option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(65535))
                        .handler(new UdpClientHandler());

                ChannelFuture f = b.bind(0).sync();
                udpChannel = f.channel();

                int localPort = ((InetSocketAddress) udpChannel.localAddress()).getPort();
                System.out.println("UDP Client bound to local port: " + localPort);

                Thread.sleep(500);
                sendVideoPacket(new VideoPacket(deviceId, null, new byte[0], 0, 0, 0, 0));

                f.channel().closeFuture().sync();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                group.shutdownGracefully();
            }
        }).start();
    }

    public boolean isConnected() {
        return tcpChannel != null && tcpChannel.isActive();
    }

    public void sendTcpPacket(NetworkPacket packet) {
        if (isConnected()) {
            tcpChannel.writeAndFlush(packet);
        }
    }

    public void sendPeerRegister(int controlPort) {
        sendTcpPacket(new NetworkPacket(PacketType.PEER_REGISTER, new PeerRegisterPacket(controlPort)));
    }

    public void connectPeerControl(String host, int port, String sessionId) {
        new Thread(() -> {
            try {
                peerControlClient.connect(host, port);
                peerControlClient.send(new NetworkPacket(PacketType.P2P_HELLO,
                        new P2PHelloPacket(myId, sessionId)));
                System.out.println("P2P Control connected: " + host + ":" + port);
            } catch (Exception e) {
                System.out.println("P2P Control connect failed -> fallback TCP. " + e.getMessage());
            }
        }).start();
    }

    public boolean isPeerControlActive() {
        return peerControlClient.isActive();
    }

    public void closePeerControl() {
        peerControlClient.close();
    }

    public void setP2PEnabled(boolean enabled) {
        this.p2pEnabled = enabled;
        System.out.println(">>> Switch Mode: " + (enabled ? "P2P" : "RELAY"));
    }

    public void requestControl(String targetId, String targetPass) {
        sendTcpPacket(new NetworkPacket(PacketType.CONNECT_REQUEST, new ConnectRequestPacket(targetId, targetPass)));
    }

    private void sendLogin(String userId, String password) {
        sendTcpPacket(new NetworkPacket(PacketType.LOGIN_REQUEST, new LoginRequest(userId, password)));
    }

    public void sendChat(String senderId, String targetId, String message) {
        sendTcpPacket(new NetworkPacket(PacketType.CHAT_MESSAGE, new ChatMessage(senderId, targetId, message)));
    }

    public void sendControl(ControlPayload payload) {
        NetworkPacket p = new NetworkPacket(PacketType.CONTROL_SIGNAL, payload);
        if (isPeerControlActive())
            peerControlClient.send(p);
        else
            sendTcpPacket(p);
    }

    public void sendClipboard(String text) {
        NetworkPacket p = new NetworkPacket(PacketType.CLIPBOARD_DATA, new ClipboardPacket(text));
        if (isPeerControlActive())
            peerControlClient.send(p);
        else
            sendTcpPacket(p);
    }

    public void sendAudio(byte[] data, int length) {
        byte[] exactData = new byte[length];
        System.arraycopy(data, 0, exactData, 0, length);
        NetworkPacket p = new NetworkPacket(PacketType.AUDIO_DATA, new AudioPacket(exactData, length));
        if (isPeerControlActive())
            peerControlClient.send(p);
        else
            sendTcpPacket(p);
    }

    public void sendVideoPacket(VideoPacket packet) {
        if (udpChannel != null && udpChannel.isActive()) {
            try {
                byte[] bytes = KryoSerializer.serialize(packet);
                if (bytes.length > 60000)
                    return;

                InetSocketAddress target;

                if (p2pEnabled && peerAddress != null) {
                    target = peerAddress;
                } else {
                    target = new InetSocketAddress(host, port + 1);
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
}