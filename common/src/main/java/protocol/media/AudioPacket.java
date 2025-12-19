package protocol.media;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AudioPacket {
    private byte[] data;
    private int length;
}