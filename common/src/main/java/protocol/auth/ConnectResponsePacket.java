package protocol.auth;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConnectResponsePacket {
    private boolean success;
    private String message;
    private String sessionId;
    private String peerHost;
    private int peerControlPort;
}
