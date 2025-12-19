package protocol.auth;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DisconnectPacket {
    private String disconnectedId;
}