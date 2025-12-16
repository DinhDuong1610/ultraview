package protocol;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ControlPayload {
    // Loại hành động: 0=Move, 1=Press, 2=Release
    private int actionType;

    // Tọa độ chuẩn hóa (0.0 -> 1.0)
    // Ví dụ: x=0.5 nghĩa là giữa màn hình.
    // Dùng cách này để không lo 2 máy có độ phân giải khác nhau.
    private float x;
    private float y;

    private int button; // 1=Left, 2=Middle, 3=Right
    private int keyCode; // Dùng cho bàn phím (sẽ làm sau)
}
