package com.cre8iot.testnursecallstream;

import static android.content.Context.AUDIO_SERVICE;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.NoiseSuppressor;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.stream.Stream;

public class OutputStreamServer {
    static final int frequency = 44100;
    //static final int frequency = 8000;
    static final int channelConfiguration = AudioFormat.CHANNEL_CONFIGURATION_MONO;
    //static final int channelConfiguration = AudioFormat.CHANNEL_IN_STEREO;
    static final int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
    boolean isPlaying;
    int playBufSize;
    private ServerSocket sockfd;
    private Socket connfd;
    AudioTrack audioTrack;

    LoudnessEnhancer enhancer;
    AudioManager audioManager;

    public OutputStreamServer(final Context ctx, final int port) {

        //audioManager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        //audioManager.setMode(AudioManager.MODE_IN_CALL);

        playBufSize= AudioTrack.getMinBufferSize(frequency, channelConfiguration, audioEncoding);
        //audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, frequency, channelConfiguration, MediaRecorder.AudioEncoder.AMR_NB, playBufSize, AudioTrack.MODE_STREAM, AudioTrack.PERFORMANCE_MODE_LOW_LATENCY);
        //audioTrack = new AudioTrack(AudioManager.STREAM_VOICE_CALL, frequency, channelConfiguration, MediaRecorder.AudioEncoder.AMR_NB, playBufSize, AudioTrack.MODE_STREAM);

        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, frequency, channelConfiguration, audioEncoding, playBufSize, AudioTrack.MODE_STREAM);
        /*
        enhancer = new LoudnessEnhancer(audioTrack.getAudioSessionId());
        audioTrack.setVolume(1f);
        enhancer.setTargetGain(1000);
        enhancer.setEnabled(true);

        AcousticEchoCanceler.create(audioTrack.getAudioSessionId());
        */

        try {
            //open tcp server
            sockfd = new ServerSocket(port);
            sockfd.setReuseAddress(true);
        } catch (Exception e) {
            e.printStackTrace();
            Intent intent = new Intent().setAction("outputServerStream.ERROR").putExtra("msg", e.toString());
            ctx.sendBroadcast(intent);
            return;
        }

        new Thread() {
            byte[] buffer = new byte[playBufSize];
            public void run() {
                //android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                //Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
                //stream out data to connected client
                try {
                    connfd = sockfd.accept();
                    String connectIpPort = connfd.getInetAddress().toString() + "|" +connfd.getPort();
                    Intent intent = new Intent().setAction("outputServerStream.CONNECTED").putExtra("connectDevice", connectIpPort.toString());
                    ctx.sendBroadcast(intent);

                } catch (Exception e) {
                    e.printStackTrace();
                    Intent intent = new Intent().setAction("outputServerStream.ERROR").putExtra("msg", e.toString());
                    ctx.sendBroadcast(intent);
                    isPlaying = false;
                    return;
                }
                //audioTrack.play();
                isPlaying = true;
                while (isPlaying) {
                    int readSize = 0;

                    try {
                        //buffer = new byte[connfd.getInputStream().available()];
                        readSize = connfd.getInputStream().read(buffer);
                    } catch (Exception e) {
                        e.printStackTrace();
                        Intent intent = new Intent().setAction("outputServerStream.ERROR").putExtra("msg", e.toString());
                        ctx.sendBroadcast(intent);
                        break;
                    }
                    //audioTrack.write(buffer, 0, readSize);
                    //audioTrack.flush();

                }
                //audioTrack.stop();
                //audioTrack.release();
                try {
                    connfd.close();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }.start();
    }

    public void stop() {
        isPlaying = false;
        try {
            sockfd.close();
            //enhancer.release();
        }
        catch (Exception e) { e.printStackTrace(); }
    }
}
