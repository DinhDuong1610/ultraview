package client.core;

import protocol.ControlPayload;

import java.awt.*;
import java.awt.event.InputEvent;

public class ControlExecutor {
    private Robot robot;
    private Dimension screenSize;

    public ControlExecutor() {
        try {
            robot = new Robot();
            screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        } catch (AWTException e) {
            e.printStackTrace();
        }
    }

    public void execute(ControlPayload payload) {
        if (robot == null)
            return;

        int realX = (int) (payload.getX() * screenSize.getWidth());
        int realY = (int) (payload.getY() * screenSize.getHeight());

        switch (payload.getActionType()) {
            case 0: // MOUSE MOVE
                robot.mouseMove(realX, realY);
                break;
            case 1: // MOUSE PRESS
                robot.mouseMove(realX, realY);
                int btnMaskPress = getButtonMask(payload.getButton());
                if (btnMaskPress != 0)
                    robot.mousePress(btnMaskPress);
                break;
            case 2: // MOUSE RELEASE
                int btnMaskRelease = getButtonMask(payload.getButton());
                if (btnMaskRelease != 0)
                    robot.mouseRelease(btnMaskRelease);
                break;

            // --- THÊM PHẦN BÀN PHÍM ---
            case 3: // KEY PRESS
                try {
                    int keyCode = payload.getKeyCode();
                    if (keyCode != -1)
                        robot.keyPress(keyCode);
                } catch (IllegalArgumentException e) {
                    System.err.println("Invalid Key Code: " + payload.getKeyCode());
                }
                break;
            case 4: // KEY RELEASE
                try {
                    int keyCode = payload.getKeyCode();
                    if (keyCode != -1)
                        robot.keyRelease(keyCode);
                } catch (IllegalArgumentException e) {
                }
                break;
        }
    }

    private int getButtonMask(int btn) {
        if (btn == 1)
            return InputEvent.BUTTON1_DOWN_MASK; // Chuột trái
        if (btn == 2)
            return InputEvent.BUTTON2_DOWN_MASK; // Chuột giữa
        if (btn == 3)
            return InputEvent.BUTTON3_DOWN_MASK; // Chuột phải
        return 0;
    }
}