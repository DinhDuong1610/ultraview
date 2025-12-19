package client.service.input;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;

import client.network.NetworkClient;

public class ClipboardWorker implements Runnable {
    private final NetworkClient networkClient;
    private final Clipboard sysClipboard;
    private String lastContent = "";
    private boolean isRunning = true;

    public static boolean isReceiving = false;

    public ClipboardWorker(NetworkClient networkClient) {
        this.networkClient = networkClient;
        this.sysClipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    }

    @Override
    public void run() {
        while (isRunning) {
            try {
                Thread.sleep(1000);

                if (isReceiving) {
                    isReceiving = false;
                    lastContent = getClipboardText();
                    continue;
                }

                String currentContent = getClipboardText();

                if (currentContent != null && !currentContent.equals(lastContent)) {
                    lastContent = currentContent;
                    System.out.println("Detected Copy: " + currentContent);

                    networkClient.sendClipboard(currentContent);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private String getClipboardText() {
        try {
            if (sysClipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                return (String) sysClipboard.getData(DataFlavor.stringFlavor);
            }
        } catch (Exception e) {
        }
        return null;
    }

    public void stop() {
        isRunning = false;
    }
}