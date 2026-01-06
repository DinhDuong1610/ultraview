package client.service.ai;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.Psapi;

import java.awt.Rectangle;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;

import java.io.File;

public class WindowSensor {

    public static String getActiveWindowTitle() {
        WinDef.HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        if (hwnd == null)
            return "";

        char[] buffer = new char[2048];
        User32.INSTANCE.GetWindowText(hwnd, buffer, 1024);
        return new String(buffer).trim();
    }

    public static Rectangle getActiveWindowRect() {
        WinDef.HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        if (hwnd == null)
            return null;

        WinDef.RECT rect = new WinDef.RECT();
        User32.INSTANCE.GetWindowRect(hwnd, rect);

        int width = rect.right - rect.left;
        int height = rect.bottom - rect.top;

        if (width <= 0 || height <= 0)
            return null;

        return new Rectangle(rect.left, rect.top, width, height);
    }

    public static String getActiveProcessName() {
        WinDef.HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        if (hwnd == null)
            return "";

        IntByReference pid = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, pid);

        WinNT.HANDLE process = Kernel32.INSTANCE.OpenProcess(
                WinNT.PROCESS_QUERY_LIMITED_INFORMATION,
                false,
                pid.getValue());
        if (process == null)
            return "";

        try {
            char[] path = new char[1024];
            IntByReference size = new IntByReference(path.length);

            boolean ok = Kernel32.INSTANCE.QueryFullProcessImageName(process, 0, path, size);
            if (!ok)
                return "";

            String fullPath = new String(path, 0, size.getValue());
            String name = new File(fullPath).getName();
            return name.toLowerCase();
        } finally {
            Kernel32.INSTANCE.CloseHandle(process);
        }
    }

}