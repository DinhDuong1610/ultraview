package protocol;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data // Tự sinh Getter/Setter/ToString
@NoArgsConstructor // Bắt buộc phải có cho Kryo
@AllArgsConstructor // Constructor đầy đủ tham số
public class LoginRequest {
    private String userId;
    private String password; // Tên máy tính (ví dụ: "Laptop của Đính")
}