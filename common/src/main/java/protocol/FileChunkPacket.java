package protocol;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileChunkPacket {
    private byte[] data;
    private int length; // Kích thước thực của chunk này
    private boolean isLast; // Có phải miếng cuối cùng không?
}