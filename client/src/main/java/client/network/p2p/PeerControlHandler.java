package client.network.p2p;

import client.service.audio.AudioPlayer;
import client.service.input.ControlExecutor;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import javafx.application.Platform;
import protocol.core.NetworkPacket;
import protocol.core.PacketType;
import protocol.input.ClipboardPacket;
import protocol.input.ControlPayload;
import protocol.media.AudioPacket;
import protocol.p2p.P2PHelloPacket;

import java.awt.*;
import java.awt.datatransfer.StringSelection;

public class PeerControlHandler extends SimpleChannelInboundHandler<NetworkPacket> {

    private final SessionState sessionState;
    private final AudioPlayer audioPlayer = new AudioPlayer();
    private boolean authed = false;

    public PeerControlHandler(SessionState sessionState) {
        this.sessionState = sessionState;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, NetworkPacket packet) {
        if (!authed) {
            if (packet.getType() != PacketType.P2P_HELLO) {
                ctx.close();
                return;
            }
            P2PHelloPacket hello = (P2PHelloPacket) packet.getPayload();
            if (!sessionState.isValid(hello.getSessionId(), hello.getFromId())) {
                ctx.close();
                return;
            }
            authed = true;
            return;
        }

        switch (packet.getType()) {
            case CONTROL_SIGNAL:
                ControlExecutor.execute((ControlPayload) packet.getPayload());
                break;

            case CLIPBOARD_DATA:
                ClipboardPacket clip = (ClipboardPacket) packet.getPayload();
                String text = clip.getContent();
                // set clipboard (JavaFX app nhưng clipboard dùng AWT OK)
                Platform.runLater(() -> {
                    StringSelection s = new StringSelection(text);
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(s, s);
                });
                break;

            case AUDIO_DATA:
                AudioPacket audio = (AudioPacket) packet.getPayload();
                audioPlayer.play(audio.getData());
                break;

            default:
                // ignore
                break;
        }
    }
}
