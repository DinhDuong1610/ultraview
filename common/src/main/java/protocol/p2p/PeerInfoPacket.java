package protocol.p2p;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PeerInfoPacket {
    private String host;
    private int port;
}