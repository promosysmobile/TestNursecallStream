package com.cre8iot.testnursecallstream

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import java.io.DataOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class InputStreamServerService : Service() {
    private var serverSocket: ServerSocket? = null
    private val working = AtomicBoolean(true)
    private val runnable = Runnable {
        var socket: Socket? = null
        try {
            serverSocket = ServerSocket(PORT)
            while (working.get()) {
                if (serverSocket != null) {
                    socket = serverSocket!!.accept()
                    Log.i(TAG, "New client: $socket")
                    val connectIpPort: String = socket.getInetAddress().toString() + "|" + socket.getPort()
                    val intent = Intent().setAction("outputServerStream.CONNECTED").putExtra(
                        "connectDevice",
                        connectIpPort
                    )
                    this.sendBroadcast(intent)

                    //val dataInputStream = DataInputStream(socket.getInputStream())
                    val dataOutputStream = DataOutputStream(socket.getOutputStream())

                    val t: Thread = InputStreamClientHandler(dataOutputStream, this)
                    t.start()
                } else {
                    Log.e(TAG, "Couldn't create ServerSocket!")
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            try {
                socket?.close()
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        startMeForeground()
        Thread(runnable).start()
    }

    override fun onDestroy() {
        working.set(false)
    }

    private fun startMeForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val NOTIFICATION_CHANNEL_ID = packageName
            val channelName = "Tcp Server Background Service"
            val chan = NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE)
            chan.lightColor = Color.BLUE
            chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            val manager = (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            manager.createNotificationChannel(chan)
            val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            val notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Tcp Server is running in background")
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build()
            startForeground(2, notification)
        } else {
            startForeground(1, Notification())
        }
    }

    companion object {
        private val TAG = InputStreamServerService::class.java.simpleName
        private const val PORT = 5000
    }
}

class InputStreamClientHandler(private val dataOutputStream: DataOutputStream, private val myContext:Context) : Thread() {
    val frequency = 44100
    //static final int frequency = 8000;
    val channelConfiguration = AudioFormat.CHANNEL_CONFIGURATION_MONO
    //val channelConfiguration = AudioFormat.CHANNEL_IN_STEREO;
    val audioEncoding = AudioFormat.ENCODING_PCM_16BIT
    var isRecording = false
    private var recBufSize = 0
    private var audioRecord: AudioRecord? = null

    //class TcpClientHandler(private val dataInputStream: DataInputStream, private val dataOutputStream: DataOutputStream) : Thread() {
    override fun run() {
        recBufSize = AudioRecord.getMinBufferSize(
            frequency,
            channelConfiguration,
            audioEncoding
        )

        //audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, frequency, channelConfiguration, audioEncoding, recBufSize);
        if (ActivityCompat.checkSelfPermission(
                myContext,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                frequency,
                channelConfiguration,
                audioEncoding,
                recBufSize
            )
            val myAcousticCanceler = AcousticEchoCanceler.create(audioRecord!!.audioSessionId)
            AcousticEchoCanceler.create(audioRecord!!.audioSessionId)
            myAcousticCanceler.enabled = true
        }
        val buffer = ByteArray(recBufSize)
        audioRecord!!.startRecording()
        isRecording = true

        while (isRecording) {
            try {
                val readSize = audioRecord!!.read(buffer, 0, recBufSize)
                try {
                    dataOutputStream.write(buffer, 0, readSize)
                    val intent = Intent().setAction("outputFilterPlayback").putExtra(
                        "recordBuffer",
                        buffer
                    )
                    myContext.sendBroadcast(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                    break
                }

            } catch (e: IOException) {
                e.printStackTrace()
                try {
                    dataOutputStream.close()
                    audioRecord!!.stop()
                } catch (ex: IOException) {
                    ex.printStackTrace()
                }
            } catch (e: InterruptedException) {
                e.printStackTrace()
                try {
                    dataOutputStream.close()
                    audioRecord!!.stop()
                } catch (ex: IOException) {
                    ex.printStackTrace()
                }
            }
        }
        audioRecord!!.stop()
        audioRecord!!.release()
    }

    companion object {
        private val TAG = OutputStreamClientHandler::class.java.simpleName
    }

}