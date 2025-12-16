package protocol;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConnectResponsePacket {
    private boolean success;
    private String message;
}
