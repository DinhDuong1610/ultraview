package client.service.audio;

import javax.sound.sampled.*;

import client.network.NetworkClient;

public class AudioRecorder implements Runnable {
    private NetworkClient networkClient;
    private TargetDataLine microphone;
    private boolean isRecording = false;

    private final AudioFormat format = new AudioFormat(8000.0f, 16, 1, true, true);

    public AudioRecorder(NetworkClient client) {
        this.networkClient = client;
    }

    public void start() {
        if (isRecording)
            return;
        isRecording = true;
        new Thread(this).start();
        System.out.println(">>> Microphone Started.");
    }

    public void stop() {
        isRecording = false;
        if (microphone != null) {
            microphone.stop();
            microphone.close();
        }
        System.out.println(">>> Microphone Stopped.");
    }

    @Override
    public void run() {
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                System.err.println("Microphone not supported!");
                return;
            }

            microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(format);
            microphone.start();

            byte[] buffer = new byte[1024];
            while (isRecording) {
                int bytesRead = microphone.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    networkClient.sendAudio(buffer, bytesRead);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}