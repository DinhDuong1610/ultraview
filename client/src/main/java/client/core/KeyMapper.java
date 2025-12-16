package client.core;

import javafx.scene.input.KeyCode;
import java.awt.event.KeyEvent;

public class KeyMapper {
    // Hàm chuyển đổi từ JavaFX KeyCode sang AWT KeyEvent
    public static int toAwtKeyCode(KeyCode fxKey) {
        // 1. Chữ cái (A-Z) và Số (0-9) thường giống nhau
        String name = fxKey.getName().toUpperCase();

        // 2. Xử lý các phím đặc biệt
        switch (fxKey) {
            case ENTER:
                return KeyEvent.VK_ENTER;
            case BACK_SPACE:
                return KeyEvent.VK_BACK_SPACE;
            case TAB:
                return KeyEvent.VK_TAB;
            case SPACE:
                return KeyEvent.VK_SPACE;
            case ESCAPE:
                return KeyEvent.VK_ESCAPE;
            case CONTROL:
                return KeyEvent.VK_CONTROL;
            case SHIFT:
                return KeyEvent.VK_SHIFT;
            case ALT:
                return KeyEvent.VK_ALT;
            case CAPS:
                return KeyEvent.VK_CAPS_LOCK;
            case UP:
                return KeyEvent.VK_UP;
            case DOWN:
                return KeyEvent.VK_DOWN;
            case LEFT:
                return KeyEvent.VK_LEFT;
            case RIGHT:
                return KeyEvent.VK_RIGHT;
            case DELETE:
                return KeyEvent.VK_DELETE;
            case HOME:
                return KeyEvent.VK_HOME;
            case END:
                return KeyEvent.VK_END;
            case PAGE_UP:
                return KeyEvent.VK_PAGE_UP;
            case PAGE_DOWN:
                return KeyEvent.VK_PAGE_DOWN;
            case F1:
                return KeyEvent.VK_F1;
            case F2:
                return KeyEvent.VK_F2;
            case F3:
                return KeyEvent.VK_F3;
            case F4:
                return KeyEvent.VK_F4;
            case F5:
                return KeyEvent.VK_F5;
            case F6:
                return KeyEvent.VK_F6;
            case F7:
                return KeyEvent.VK_F7;
            case F8:
                return KeyEvent.VK_F8;
            case F9:
                return KeyEvent.VK_F9;
            case F10:
                return KeyEvent.VK_F10;
            case F11:
                return KeyEvent.VK_F11;
            case F12:
                return KeyEvent.VK_F12;
            // ... thêm các phím khác nếu cần
            default:
                // Thử mapping tự động cho chữ và số
                int code = fxKey.getCode();
                if ((code >= 65 && code <= 90) || (code >= 48 && code <= 57)) {
                    return code;
                }
                return -1; // Không hỗ trợ hoặc không tìm thấy
        }
    }
}
