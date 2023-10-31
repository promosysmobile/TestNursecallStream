package com.cre8iot.testnursecallstream

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.DataInputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean


class OutputStreamServerService : Service() {
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

                    val dataInputStream = DataInputStream(socket.getInputStream())
                    //val dataOutputStream = DataOutputStream(socket.getOutputStream())

                    val t: Thread = OutputStreamClientHandler(dataInputStream, this)
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
        private val TAG = OutputStreamServerService::class.java.simpleName
        private const val PORT = 5050
    }
}

class OutputStreamClientHandler(private val dataInputStream: DataInputStream, private val myContext:Context) : Thread() {
    val frequency = 44100
    //static final int frequency = 8000;
    val channelConfiguration = AudioFormat.CHANNEL_CONFIGURATION_MONO
    //static final int channelConfiguration = AudioFormat.CHANNEL_IN_STEREO;
    val audioEncoding = AudioFormat.ENCODING_PCM_16BIT
    var isPlaying = false
    var playBufSize = 0
    var audioTrack: AudioTrack? = null
    private val mIntentFilter = IntentFilter()
    var myMainFilterBuff = byteArrayOf()
    var myFilterString = ""

    private val mReceiver = object : BroadcastReceiver() {

        override fun onReceive(contxt: Context?, intent: Intent?) {

            when (intent?.action) {
                "outputFilterPlayback" -> {
                    val myBuff = intent.getByteArrayExtra("recordBuffer")
                    myMainFilterBuff = myBuff!!
                    val myStringBuff = StringBuffer()
                    for (i in myMainFilterBuff){
                        myStringBuff.append(i)
                    }
                    myFilterString = myStringBuff.toString()
                }

                "stopOutputStream" -> {
                    val strMessage = intent.getStringExtra("send_message").toString()

                }

            }
        }
    }

//class TcpClientHandler(private val dataInputStream: DataInputStream, private val dataOutputStream: DataOutputStream) : Thread() {
    override fun run() {

    mIntentFilter.addAction("outputFilterPlayback")
    mIntentFilter.addAction("stopOutputStream")
    myContext.registerReceiver(mReceiver, mIntentFilter)

    playBufSize = AudioTrack.getMinBufferSize(
        frequency,
        channelConfiguration,
        audioEncoding
    )
    audioTrack = AudioTrack(
        AudioManager.MODE_IN_COMMUNICATION,
        frequency,
        channelConfiguration,
        audioEncoding,
        playBufSize,
        AudioTrack.MODE_STREAM
    )
    audioTrack!!.setVolume(1f)
    //enhancer = LoudnessEnhancer(audioTrack.getAudioSessionId());

    AcousticEchoCanceler.create(audioTrack!!.getAudioSessionId());
    NoiseSuppressor.create(audioTrack!!.getAudioSessionId());

    var buffer = ByteArray(playBufSize)
    audioTrack!!.play()
    isPlaying = true

    fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }

        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    while (isPlaying) {
            try {
                if(dataInputStream.available() > 0){
                    val myFilterBuff = myMainFilterBuff
                    var readSize = 0
                    try {
                        buffer = ByteArray(dataInputStream.available())
                        readSize = dataInputStream.read(buffer)
                        //Log.i(TAG, "  dataInputStream.readUTF(): ${  dataInputStream.readUTF()}")

                    } catch (e: Exception) {
                        e.printStackTrace()
                        break
                    }
                    /*
                    val strBuff = buffer.joinToString(separator = "") {
                        it.toInt().and(0xFF).toString(16).padStart(2, '0')
                    }
                    var myFinalBuff = strBuff.decodeHex()
                    if(myFilterBuff.isNotEmpty()){
                        if(buffer.size> myFilterBuff.size){
                            val strFilterBuff = myFilterBuff.joinToString(separator = "") {
                                it.toInt().and(0xFF).toString(16).padStart(2, '0')
                            }
                            myFinalBuff = strBuff.replace(strFilterBuff,"").decodeHex()
                        }
                    }
                    */

                    //Log.i(TAG,"myBuff: ${buffer.size}")
                    //Log.i(TAG,"myFinalBuff: ${myFinalBuff.size}")
                    //audioTrack!!.write(myFinalBuff, 0, myFinalBuff.size)
                    audioTrack!!.write(buffer, 0, readSize)
                }
                //audioTrack!!.write(myMainFilterBuff, 0, myMainFilterBuff.size)


            } catch (e: IOException) {
                e.printStackTrace()
                try {
                    dataInputStream.close()
                } catch (ex: IOException) {
                    ex.printStackTrace()
                }
            } catch (e: InterruptedException) {
                e.printStackTrace()
                try {
                    dataInputStream.close()
                } catch (ex: IOException) {
                    ex.printStackTrace()
                }
            }
        }



}

    companion object {
        private val TAG = OutputStreamClientHandler::class.java.simpleName
    }

}