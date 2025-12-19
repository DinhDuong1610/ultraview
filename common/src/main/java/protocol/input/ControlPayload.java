package protocol.input;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ControlPayload {
    private int actionType;

    private float x;
    private float y;

    private int button;
    private int keyCode;
}
