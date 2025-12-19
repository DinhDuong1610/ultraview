package protocol.media;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StartStreamPacket {
    private String targetId;
}