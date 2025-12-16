package client.core;

import protocol.AudioPacket;
import protocol.ChatMessage;
import protocol.ClipboardPacket;
import protocol.ConnectResponsePacket;
import protocol.ControlPayload;
import protocol.DisconnectPacket;
import protocol.FileAcceptPacket;
import protocol.FileChunkPacket;
import protocol.FileOfferPacket;
import protocol.FileReqPacket;
import protocol.NetworkPacket;
import protocol.PacketType;
import protocol.PeerInfoPacket;
import protocol.StartStreamPacket;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import javafx.application.Platform;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.function.Consumer;

public class ClientHandler extends SimpleChannelInboundHandler<NetworkPacket> {

    // --- CÁC CALLBACK ĐỂ GỌI VỀ UI ---
    public static Consumer<String> onMessageReceived;
    public static Consumer<String> onStartStreaming;
    public static Consumer<ConnectResponsePacket> onConnectResult;

    // File Transfer
    public static Consumer<FileOfferPacket> onFileOffer;
    public static Consumer<String> onFileAccepted;
    public static Consumer<String> onFileTransferSuccess;

    // Disconnect
    public static Consumer<String> onPartnerDisconnect;

    public static Consumer<PeerInfoPacket> onPeerInfoReceived;

    // --- CÁC WORKER XỬ LÝ LOGIC NGẦM ---
    private static ControlExecutor executor = new ControlExecutor();
    private static FileReceiver fileReceiver = new FileReceiver();
    private static AudioPlayer audioPlayer = new AudioPlayer();

    public static void setOnMessageReceived(Consumer<String> listener) {
        onMessageReceived = listener;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, NetworkPacket packet) throws Exception {

        // 1. CHAT MESSAGE
        if (packet.getType() == PacketType.CHAT_MESSAGE) {
            ChatMessage msg = (ChatMessage) packet.getPayload();
            String text = "[" + msg.getSenderId() + "]: " + msg.getMessage();
            if (onMessageReceived != null) {
                Platform.runLater(() -> onMessageReceived.accept(text));
            }
        }

        else if (packet.getType() == PacketType.PEER_INFO) {
            PeerInfoPacket peer = (PeerInfoPacket) packet.getPayload();
            if (onPeerInfoReceived != null) {
                onPeerInfoReceived.accept(peer);
            }
        }

        // 2. LỆNH START STREAM (Máy bị điều khiển)
        else if (packet.getType() == PacketType.START_STREAM) {
            StartStreamPacket p = (StartStreamPacket) packet.getPayload();
            if (onStartStreaming != null) {
                onStartStreaming.accept(p.getTargetId());
            }
        }

        // 3. KẾT QUẢ KẾT NỐI (Máy điều khiển)
        else if (packet.getType() == PacketType.CONNECT_RESPONSE) {
            ConnectResponsePacket p = (ConnectResponsePacket) packet.getPayload();
            if (onConnectResult != null) {
                onConnectResult.accept(p);
            }
        }

        // 4. ĐIỀU KHIỂN CHUỘT/PHÍM
        else if (packet.getType() == PacketType.CONTROL_SIGNAL) {
            if (packet.getPayload() instanceof ControlPayload) {
                ControlPayload payload = (ControlPayload) packet.getPayload();
                executor.execute(payload);
            }
        }

        // 5. CLIPBOARD
        else if (packet.getType() == PacketType.CLIPBOARD_DATA) {
            ClipboardPacket clip = (ClipboardPacket) packet.getPayload();
            String text = clip.getContent();
            ClipboardWorker.isReceiving = true;
            Platform.runLater(() -> {
                try {
                    StringSelection selection = new StringSelection(text);
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                } catch (IllegalStateException ex) {
                    System.err.println("Clipboard busy, skipping update.");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        // 6. TRUYỀN FILE
        else if (packet.getType() == PacketType.FILE_OFFER) {
            FileOfferPacket offer = (FileOfferPacket) packet.getPayload();
            if (onFileOffer != null)
                Platform.runLater(() -> onFileOffer.accept(offer));
        } else if (packet.getType() == PacketType.FILE_ACCEPT) {
            FileAcceptPacket accept = (FileAcceptPacket) packet.getPayload();
            if (onFileAccepted != null)
                onFileAccepted.accept(accept.getFileName());
        } else if (packet.getType() == PacketType.FILE_REQ) {
            FileReqPacket req = (FileReqPacket) packet.getPayload();
            fileReceiver.handleRequest(req);
        } else if (packet.getType() == PacketType.FILE_CHUNK) {
            FileChunkPacket chunk = (FileChunkPacket) packet.getPayload();
            fileReceiver.handleChunk(chunk);
            if (chunk.isLast() && onFileTransferSuccess != null) {
                Platform.runLater(() -> onFileTransferSuccess.accept("Đã tải xong file!"));
            }
        }

        // 7. ÂM THANH (VOICE)
        else if (packet.getType() == PacketType.AUDIO_DATA) {
            AudioPacket audio = (AudioPacket) packet.getPayload();
            if (audioPlayer != null) {
                audioPlayer.play(audio.getData());
            }
        }

        // 8. NGẮT KẾT NỐI (QUAN TRỌNG: Đây là đoạn bạn có thể bị thiếu)
        else if (packet.getType() == PacketType.DISCONNECT_NOTICE) {
            DisconnectPacket p = (DisconnectPacket) packet.getPayload();
            if (onPartnerDisconnect != null) {
                // Đẩy ra UI để đóng cửa sổ
                Platform.runLater(() -> onPartnerDisconnect.accept(p.getDisconnectedId()));
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}