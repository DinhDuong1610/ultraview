package protocol.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileChunkPacket {
    private byte[] data;
    private int length;
    private boolean isLast;
}