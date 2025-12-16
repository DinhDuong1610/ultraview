package protocol;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private String senderId; // Ai gửi?
    private String receiverId; // Gửi cho ai? (Nếu null thì gửi cho Server hoặc Broadcast)
    private String message; // Nội dung: "Hello world"
}