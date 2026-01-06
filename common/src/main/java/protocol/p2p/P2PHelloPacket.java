package protocol.p2p;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class P2PHelloPacket {
    private String fromId;
    private String sessionId;
}
