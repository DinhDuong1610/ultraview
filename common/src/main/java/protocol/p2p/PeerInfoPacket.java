package protocol.p2p;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PeerInfoPacket {
    private String host; // Địa chỉ IP (Ví dụ: 192.168.1.5)
    private int port; // Port UDP (Ví dụ: 61234)
}