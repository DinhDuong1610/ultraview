package client.service.input;

import protocol.input.ControlPayload;
import java.awt.*;
import java.awt.event.InputEvent;

public class ControlExecutor {
    private static Robot robot;
    private static Dimension screenSize;

    static {
        try {
            robot = new Robot();
            screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        } catch (AWTException e) {
            e.printStackTrace();
        }
    }

    public static void execute(ControlPayload payload) {
        if (robot == null)
            return;

        int type = payload.getActionType(); // 0: Move, 1: Press, 2: Release, 3: KeyPress, 4: KeyRelease

        switch (type) {
            case 0: // Mouse Move
                int x = (int) (payload.getX() * screenSize.getWidth());
                int y = (int) (payload.getY() * screenSize.getHeight());
                robot.mouseMove(x, y);
                break;
            case 1: // Mouse Press
                int btnPress = getMask(payload.getButton());
                if (btnPress != 0)
                    robot.mousePress(btnPress);
                break;
            case 2: // Mouse Release
                int btnRel = getMask(payload.getButton());
                if (btnRel != 0)
                    robot.mouseRelease(btnRel);
                break;
            case 3: // Key Press
                if (payload.getKeyCode() != -1)
                    robot.keyPress(payload.getKeyCode());
                break;
            case 4: // Key Release
                if (payload.getKeyCode() != -1)
                    robot.keyRelease(payload.getKeyCode());
                break;
        }
    }

    private static int getMask(int btn) {
        if (btn == 1)
            return InputEvent.BUTTON1_DOWN_MASK;
        if (btn == 2)
            return InputEvent.BUTTON2_DOWN_MASK;
        if (btn == 3)
            return InputEvent.BUTTON3_DOWN_MASK;
        return 0;
    }
}