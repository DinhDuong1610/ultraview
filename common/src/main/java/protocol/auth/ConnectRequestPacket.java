package protocol.auth;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConnectRequestPacket {
    private String targetId;
    private String targetPass;
}
