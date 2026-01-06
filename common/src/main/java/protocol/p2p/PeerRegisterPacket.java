package protocol.p2p;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PeerRegisterPacket {
    private int controlPort;
}
