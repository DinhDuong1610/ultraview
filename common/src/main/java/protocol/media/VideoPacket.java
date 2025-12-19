package protocol.media;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoPacket {
    private String senderId;
    private String targetId;
    private byte[] data;
    private long timestamp;

    private long frameId;
    private int chunkIndex;
    private int totalChunks;
}