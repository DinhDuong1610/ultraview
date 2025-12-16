package protocol;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileAcceptPacket {
    private String fileName;
}