package client.network.handler;

import client.service.audio.AudioPlayer;
import client.service.input.ControlExecutor;
import protocol.core.NetworkPacket;
import protocol.core.PacketType;

// Import đầy đủ các gói tin
import protocol.auth.ConnectResponsePacket;
import protocol.auth.DisconnectPacket;
import protocol.chat.ChatMessage;
import protocol.file.FileAcceptPacket;
import protocol.file.FileChunkPacket;
import protocol.file.FileOfferPacket;
import protocol.file.FileReqPacket;
import protocol.input.ClipboardPacket;
import protocol.input.ControlPayload;
import protocol.media.AudioPacket;
import protocol.media.StartStreamPacket;
import protocol.p2p.PeerInfoPacket;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import javafx.application.Platform;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.function.Consumer;

public class ClientHandler extends SimpleChannelInboundHandler<NetworkPacket> {

    public static Consumer<String> onMessageReceived;
    public static Consumer<ConnectResponsePacket> onConnectResult;
    public static Consumer<String> onStartStreaming;
    public static Consumer<PeerInfoPacket> onPeerInfoReceived;
    public static Consumer<String> onPartnerDisconnect;

    public static Consumer<FileOfferPacket> onFileOffer;
    public static Consumer<String> onFileAccepted;
    public static Consumer<String> onFileTransferSuccess;
    public static Consumer<FileReqPacket> onFileReq;
    public static Consumer<FileChunkPacket> onFileChunk;

    private static final AudioPlayer audioPlayer = new AudioPlayer();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, NetworkPacket packet) throws Exception {
        PacketType type = packet.getType();

        switch (type) {
            case CONNECT_RESPONSE:
                if (onConnectResult != null)
                    onConnectResult.accept((ConnectResponsePacket) packet.getPayload());
                break;

            case CHAT_MESSAGE:
                ChatMessage chat = (ChatMessage) packet.getPayload();
                if (onMessageReceived != null)
                    onMessageReceived.accept(chat.getMessage());
                break;

            case START_STREAM:
                StartStreamPacket stream = (StartStreamPacket) packet.getPayload();
                if (onStartStreaming != null)
                    onStartStreaming.accept(stream.getTargetId());
                break;

            case DISCONNECT_NOTICE:
                if (packet.getPayload() instanceof DisconnectPacket) {
                    DisconnectPacket disPacket = (DisconnectPacket) packet.getPayload();
                    if (onPartnerDisconnect != null) {
                        onPartnerDisconnect.accept(disPacket.getDisconnectedId());
                    }
                }
                break;

            case PEER_INFO:
                if (onPeerInfoReceived != null)
                    onPeerInfoReceived.accept((PeerInfoPacket) packet.getPayload());
                break;

            case FILE_OFFER:
                if (onFileOffer != null)
                    onFileOffer.accept((FileOfferPacket) packet.getPayload());
                break;

            case FILE_ACCEPT:
                FileAcceptPacket accept = (FileAcceptPacket) packet.getPayload();
                if (onFileAccepted != null) {
                    onFileAccepted.accept(accept.getFileName());
                }
                break;

            case FILE_REQ:
                FileReqPacket req = (FileReqPacket) packet.getPayload();
                if (onFileReq != null)
                    onFileReq.accept(req);
                break;

            case FILE_CHUNK:
                FileChunkPacket chunk = (FileChunkPacket) packet.getPayload();
                if (onFileChunk != null)
                    onFileChunk.accept(chunk);
                break;

            case CONTROL_SIGNAL:
                ControlPayload control = (ControlPayload) packet.getPayload();
                ControlExecutor.execute(control);
                break;

            case AUDIO_DATA:
                AudioPacket audio = (AudioPacket) packet.getPayload();
                audioPlayer.play(audio.getData());
                break;

            case CLIPBOARD_DATA:
                ClipboardPacket clip = (ClipboardPacket) packet.getPayload();
                String text = clip.getContent();

                Platform.runLater(() -> {
                    try {
                        StringSelection selection = new StringSelection(text);
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                    } catch (IllegalStateException ex) {
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                break;

            default:
                break;
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}