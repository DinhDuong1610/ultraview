package protocol;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileOfferPacket {
    private String fileName;
    private long fileSize;
}