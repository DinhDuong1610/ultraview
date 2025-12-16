package protocol.media;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AudioPacket {
    private byte[] data; // Dữ liệu âm thanh thô (PCM)
    private int length; // Độ dài thực tế
}