package client.core;

import javax.sound.sampled.*;

public class AudioPlayer {
    private SourceDataLine speakers;
    private final AudioFormat format = new AudioFormat(8000.0f, 16, 1, true, true);

    public AudioPlayer() {
        init();
    }

    private void init() {
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                System.err.println("Speakers not supported!");
                return;
            }
            speakers = (SourceDataLine) AudioSystem.getLine(info);
            speakers.open(format);
            speakers.start();
            System.out.println(">>> Speakers Ready.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void play(byte[] data) {
        if (speakers != null && speakers.isOpen()) {
            speakers.write(data, 0, data.length);
        }
    }

    public void stop() {
        if (speakers != null) {
            speakers.stop();
            speakers.close();
        }
    }
}