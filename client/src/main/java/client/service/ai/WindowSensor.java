package client.service.ai;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;

import java.awt.Rectangle;

public class WindowSensor {

    // Lấy tiêu đề cửa sổ đang focus
    public static String getActiveWindowTitle() {
        WinDef.HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        if (hwnd == null)
            return "";

        char[] buffer = new char[2048];
        User32.INSTANCE.GetWindowText(hwnd, buffer, 1024);
        return new String(buffer).trim();
    }

    // Lấy tọa độ cửa sổ đang focus
    public static Rectangle getActiveWindowRect() {
        WinDef.HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        if (hwnd == null)
            return null;

        WinDef.RECT rect = new WinDef.RECT();
        User32.INSTANCE.GetWindowRect(hwnd, rect);

        // Chuyển đổi RECT của Win32 sang Rectangle của Java
        int width = rect.right - rect.left;
        int height = rect.bottom - rect.top;

        if (width <= 0 || height <= 0)
            return null;

        return new Rectangle(rect.left, rect.top, width, height);
    }
}