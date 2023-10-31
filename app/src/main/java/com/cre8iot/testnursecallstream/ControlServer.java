package com.cre8iot.testnursecallstream;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;

public class ControlServer {
    boolean isListening;
    int recBufSize;
    ServerSocket sockfd;
    Socket connfd;


    public ControlServer(final Context ctx, final int port) {
        try {
            //open tcp server
            sockfd = new ServerSocket(port);
            new Thread() {
                //final byte[] buffer = new byte[recBufSize];
                public void run() {
                    //stream out data to connected client
                    try {
                        sockfd.setReuseAddress(true);
                        connfd = sockfd.accept();
                        Log.i("ControlServer","connected to: " + connfd.getInetAddress());
                        String connectIpPort = connfd.getInetAddress().toString() + "|" +connfd.getPort();
                        Intent intent = new Intent().setAction("controlServer.CONNECTED").putExtra("connectDevice", connectIpPort.toString());
                        ctx.sendBroadcast(intent);

                        isListening = true;

                        BufferedReader in = new BufferedReader(new InputStreamReader(connfd.getInputStream()));
                        OutputStreamWriter osw = new OutputStreamWriter(connfd.getOutputStream());

                        while (isListening) {
                            try {
                                if(connfd.getInputStream().available()>0){
                                    byte[] buffer = new byte[connfd.getInputStream().available()];
                                    int bytesRead = 0;
                                    bytesRead = connfd.getInputStream().read(buffer);

                                    Log.i("ControlServer","bytesRead: " + bytesRead);
                                    //byte[] serverMessage;
                                    //serverMessage = new byte[bytesRead];

                                    String response = new String(buffer, Charset.forName("UTF-8"));
                                    //String response = in.readLine();
                                    Log.i("ControlServer","response: " + response);
                                    intent = new Intent().setAction("controlServer.AVAILABLE_DATA").putExtra("msg", response);
                                    ctx.sendBroadcast(intent);
                                    if (response.contains("CONNECT")){
                                        connfd.getOutputStream().write("#READY#\r\n".getBytes());
                                        intent = new Intent().setAction("controlServer.RECEIVED_CONNECT").putExtra("msg", response);
                                        ctx.sendBroadcast(intent);

                                    }else if(response.contains("DISCONNECT")){
                                        connfd.getOutputStream().write("#DISCONNECT#\r\n".getBytes());
                                        isListening = false;
                                        intent = new Intent().setAction("controlServer.RECEIVED_DISCONNECT").putExtra("msg", response);
                                        ctx.sendBroadcast(intent);
                                    }
                                }

                            } catch (Exception e) {
                                e.printStackTrace();
                                intent = new Intent().setAction("controlServer.ERROR").putExtra("msg", e.toString());
                                ctx.sendBroadcast(intent);
                                break;
                            }
                        }
                        connfd.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                        Intent intent = new Intent().setAction("controlServer.ERROR").putExtra("msg", e.toString());
                        ctx.sendBroadcast(intent);
                        return;
                    }



                }
            }.start();

        } catch (Exception e) {
            e.printStackTrace();
            Intent intent = new Intent().setAction("controlServer.ERROR").putExtra("msg", e.toString());
            ctx.sendBroadcast(intent);
            return;
        }
    }

    public void stop() {
        try { sockfd.close(); }
        catch (Exception e) { e.printStackTrace(); }
    }
}
