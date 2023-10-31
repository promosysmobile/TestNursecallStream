package com.cre8iot.testnursecallstream;

import static android.content.Context.AUDIO_SERVICE;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.Equalizer;
import android.media.audiofx.NoiseSuppressor;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class InputStreamServer {
    private static final int frequency = 44100;
    private static final int channelConfiguration = AudioFormat.CHANNEL_CONFIGURATION_MONO;
    //static final int channelConfiguration = AudioFormat.CHANNEL_IN_STEREO;
    static final int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
    boolean isRecording;
    private int recBufSize;
    private AudioRecord audioRecord;
    private ServerSocket sockfd;
    private Socket connfd;


    public InputStreamServer(final Context ctx, final int port) {
        recBufSize = AudioRecord.getMinBufferSize(frequency, channelConfiguration, audioEncoding);
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            //audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, frequency, channelConfiguration, audioEncoding, recBufSize);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, frequency, channelConfiguration, audioEncoding, recBufSize);
            AcousticEchoCanceler myAcousticCanceler = AcousticEchoCanceler.create(audioRecord.getAudioSessionId());
            myAcousticCanceler.setEnabled(true);

            Log.i("InputStreamServer", "acousticEchoAvailable: " + AcousticEchoCanceler.isAvailable());

            /*
            AudioManager audioManager = (AudioManager) ctx.getSystemService(AUDIO_SERVICE);
            int audioSessionId = audioManager.generateAudioSessionId();
            //int audioSessionId = audioRecord.getAudioSessionId();
            Equalizer equalizer = new Equalizer(0, audioSessionId);
            Log.i("InputStreamServer", "audioSessionId: " + audioSessionId);
            equalizer.setEnabled(true);
            short bandIndex = 0;
            short gainLevel = myGain;

            if (gainLevel < equalizer.getBandLevelRange()[0]) {
                gainLevel = equalizer.getBandLevelRange()[0];
            } else if (gainLevel > equalizer.getBandLevelRange()[1]) {
                gainLevel = equalizer.getBandLevelRange()[1];
            }
            Log.i("InputStreamServer", "gainLevel: " + gainLevel);
            equalizer.setBandLevel(bandIndex, gainLevel);
            */

            try {
                //open tcp server
                sockfd = new ServerSocket(port);
                sockfd.setReuseAddress(true);
            } catch (Exception e) {
                e.printStackTrace();
                Intent intent = new Intent().setAction("inputServerStream.ERROR").putExtra("msg", e.toString());
                ctx.sendBroadcast(intent);
                return;
            }

            /*
            String filePath = "/storage/emulated/0/Download/EzycallDesktop/test_mp32.txt"; // Replace this with the actual file path
            StringBuilder content = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
                String line;
                while ((line = br.readLine()) != null) {
                    content.append(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            String hexString = content.toString().trim();

            if (hexString.length() % 2 != 0) {
                throw new IllegalArgumentException("Hex string must have an even number of characters");
            }
            List<Byte> byteList = new ArrayList<>();
            */

            new Thread() {
                final byte[] buffer = new byte[recBufSize];
                public void run() {
                    //android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                    //Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
                    //stream out data to connected client
                    try {
                        sockfd.setReuseAddress(true);
                        connfd = sockfd.accept();
                        String connectIpPort = connfd.getInetAddress().toString() + "|" +connfd.getPort();
                        Intent intent = new Intent().setAction("inputServerStream.CONNECTED").putExtra("connectDevice", connectIpPort.toString());
                        ctx.sendBroadcast(intent);

                    } catch (Exception e) {
                        e.printStackTrace();
                        Intent intent = new Intent().setAction("inputServerStream.ERROR").putExtra("msg", e.toString());
                        ctx.sendBroadcast(intent);
                        isRecording = false;
                        return;
                    }


                    try {
                        sleep(2000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    audioRecord.startRecording();
                    isRecording = true;
                    boolean isSendStreamAudio = true;

                    while (isRecording) {
                        /*
                        if(isSendStreamAudio){
                            for (int i = 0; i < hexString.length(); i += 2) {
                                String hexByte = hexString.substring(i, i + 2);
                                byte byteValue = (byte) Integer.parseInt(hexByte, 16);
                                byteList.add(byteValue);

                                if (byteList.size() > recBufSize - 1) {
                                    for (int b = 0; b < byteList.size(); b++) {
                                        buffer[b] = byteList.get(b);
                                    }
                                    byteList.clear();
                                    try {
                                        connfd.getOutputStream().write(buffer);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                            isSendStreamAudio = false;
                            audioRecord.startRecording();
                        }
                        */
                        int readSize = audioRecord.read(buffer, 0, recBufSize);
                        try {
                            connfd.getOutputStream().write(buffer, 0, readSize);
                        } catch (Exception e) {
                            e.printStackTrace();
                            Intent intent = new Intent().setAction("inputServerStream.ERROR").putExtra("msg", e.toString());
                            break;
                        }
                    }
                    audioRecord.stop();
                    audioRecord.release();
                    try {
                        connfd.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            }.start();
        }
    }

    private void getFiles(){

    }

    public void stop() {
        isRecording = false;
        try {
            sockfd.close();
        }
        catch (Exception e) { e.printStackTrace(); }
    }
}
