package protocol;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoPacket {
    private String senderId;
    private String targetId;
    private byte[] data; // Dữ liệu của mảnh (Chunk data)
    private long timestamp; // Thời gian gửi (để tính trễ nếu cần)

    // --- THÊM CÁC TRƯỜNG CHUNKING ---
    private long frameId; // ID của khung hình (Các mảnh cùng 1 ảnh sẽ có chung ID này)
    private int chunkIndex; // Số thứ tự mảnh (0, 1, 2...)
    private int totalChunks; // Tổng số mảnh của ảnh này
}