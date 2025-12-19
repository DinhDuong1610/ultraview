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

        int type = payload.getActionType();

        switch (type) {
            case 0:
                int x = (int) (payload.getX() * screenSize.getWidth());
                int y = (int) (payload.getY() * screenSize.getHeight());
                robot.mouseMove(x, y);
                break;
            case 1:
                int btnPress = getMask(payload.getButton());
                if (btnPress != 0)
                    robot.mousePress(btnPress);
                break;
            case 2:
                int btnRel = getMask(payload.getButton());
                if (btnRel != 0)
                    robot.mouseRelease(btnRel);
                break;
            case 3:
                if (payload.getKeyCode() != -1)
                    robot.keyPress(payload.getKeyCode());
                break;
            case 4:
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