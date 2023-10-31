package com.cre8iot.testnursecallstream

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.cre8iot.testnursecallstream.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity(),AudioManager.OnAudioFocusChangeListener {

    private lateinit var binding: ActivityMainBinding
    private var ip = ""

    //private var port = 1884

    private var intControlPort = 5025

    //playback audio
    private var intOutputPort = 5050

    //record audio
    private var intInputPort = 5000

    private lateinit var outputStreamServer: OutputStreamServer
    private lateinit var inputStreamServer: InputStreamServer
    private lateinit var controlServer:ControlServer
    private var myIpAddress = ""

    private lateinit var audioManager:AudioManager


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        val receiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent) {
                when(intent.action){

                    "outputServerStream.CONNECTED" -> {
                        binding.textView1.append("outputServerStream.CONNECTED\n")
                        val msg = intent.getStringExtra("connectDevice")
                        binding.textView1.append("connectedTo: $msg\n")
                    }

                    "inputServerStream.CONNECTED" -> {
                        binding.textView1.append("inputServerStream.CONNECTED\n")
                        val msg = intent.getStringExtra("connectDevice")
                        binding.textView1.append("connectedTo: $msg\n")
                    }

                    "outputServerStream.ERROR" -> {
                        binding.textView1.append("""outputServerStream.ERROR: ${intent.getStringExtra("msg")}""".trimIndent())
                    }

                    "inputServerStream.ERROR" -> {
                        binding.textView1.append("""inputServerStream.ERROR: ${intent.getStringExtra("msg")}""".trimIndent())
                    }

                    "controlServer.ERROR" -> {
                        binding.textView1.append("""controlServer.ERROR: ${intent.getStringExtra("msg")}""".trimIndent())
                    }


                    "controlServer.CONNECTED" -> {
                        binding.textView1.append("controlServer.CONNECTED\n")
                        val msg = intent.getStringExtra("connectDevice")
                        binding.textView1.append("connectedTo: $msg\n")
                    }

                    "controlServer.AVAILABLE_DATA" -> {
                        val msg = intent.getStringExtra("msg")
                        binding.textView1.append("controlServer.AVAILABLE_DATA\n")
                        binding.textView1.append("data received: $msg\n")
                    }

                    "controlServer.RECEIVED_CONNECT"-> {
                        binding.textView1.append("reply READY\n")
                    }

                    "controlServer.RECEIVED_DISCONNECT"-> {
                        binding.textView1.append("controlServer.RECEIVED_DISCONNECT\n")
                    }

                    /*
                    "filterPlayback"-> {
                        val myBuff = intent.getIntArrayExtra("recordBuffer")

                        val intent = Intent().setAction("outputFilterPlayback").putExtra(
                            "recordBuffer",
                            myBuff
                        )
                        sendBroadcast(intent)
                    }
                    */
                }

            }
        }

        val filter = IntentFilter()
        filter.addAction("inputServerStream.ERROR")
        filter.addAction("inputServerStream.CONNECTED")
        filter.addAction("outputServerStream.ERROR")
        filter.addAction("outputServerStream.CONNECTED")
        filter.addAction("controlServer.CONNECTED")
        filter.addAction("controlServer.AVAILABLE_DATA")
        filter.addAction("controlServer.RECEIVED_CONNECT")
        filter.addAction("controlServer.RECEIVED_DISCONNECT")
        filter.addAction("filterPlayback")

        registerReceiver(receiver, filter)

        getMyIpAddress()

        binding.txtServerIP.text = myIpAddress

        binding.btnReady.setOnClickListener {
            if(binding.btnReady.text == "START"){
                binding.btnReady.setText("STOP STREAM")
                binding.textView1.append("Starting server\n")

                //controlServer = ControlServer(applicationContext, intControlPort)

                intInputPort = binding.edtInputServer.text.toString().toInt()
                intOutputPort = binding.edtOutputServerPort.text.toString().toInt()
                intInputPort = Integer.valueOf(binding.edtInputServer.getText().toString())

                val edtGainLevel: Short = binding.edtGainValue.text.toString().toShort()
                val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
                val audioSessionId = audioManager.generateAudioSessionId()
                val equalizer = Equalizer(0, audioSessionId)
                Log.i("InputStreamServer", "audioSessionId: $audioSessionId")
                equalizer.enabled = true
                val bandIndex: Short = 0
                var gainLevel: Short = edtGainLevel

                if (gainLevel < equalizer.bandLevelRange[0]) {
                    gainLevel = equalizer.bandLevelRange[0]
                } else if (gainLevel > equalizer.bandLevelRange[1]) {
                    gainLevel = equalizer.bandLevelRange[1]
                }
                equalizer.setBandLevel(bandIndex, gainLevel)

                Log.i("MainActivity", "bandLevels: ${equalizer.properties.bandLevels}")

                outputStreamServer = OutputStreamServer(applicationContext, intOutputPort)
                inputStreamServer = InputStreamServer(applicationContext, intInputPort)

                /*
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(Intent(applicationContext, OutputStreamServerService::class.java))
                    startForegroundService(Intent(applicationContext, InputStreamServerService::class.java))
                } else {
                    startService(Intent(applicationContext, OutputStreamServerService::class.java))
                    startService(Intent(applicationContext, InputStreamServerService::class.java))
                }
                */

            }else{
                binding.btnReady.setText("START")
                //controlServer.stop()
                //stopService(Intent(applicationContext, OutputStreamServerService::class.java))
                //stopService(Intent(applicationContext, InputStreamServerService::class.java))
                binding.textView1.append("Close server\n")

                if (outputStreamServer != null) {
                    outputStreamServer.stop()
                }

                if (inputStreamServer != null) {
                    inputStreamServer.stop()
                }
            }
        }


        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_CALL
        //audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true
        audioManager.adjustStreamVolume(AudioManager.MODE_IN_CALL, AudioManager.ADJUST_RAISE, 0);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.MODIFY_AUDIO_SETTINGS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                // Permission is not granted, request the permission
                ActivityCompat.requestPermissions(
                    this, arrayOf<String>(Manifest.permission.MODIFY_AUDIO_SETTINGS),
                    100
                )
            } else {
                // Permission is granted, mute the ringer volume
                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            }
        }else{
            audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            Log.i("MainActivity","streamVol: ${audioManager.getStreamVolume(AudioManager.MODE_IN_CALL)}")
            Log.i("MainActivity","ringerVol: ${audioManager.ringerMode}")
        }


    }



    private fun hideKeyboardFrom(context: Context, view: View) {
        val imm = context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun checkPermissions(): Boolean {
        val result = ContextCompat.checkSelfPermission(applicationContext,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this@MainActivity,
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE),
            1
        )
    }


    override fun onDestroy() {
        if (outputStreamServer != null) {
            outputStreamServer.stop()
        }
        if (inputStreamServer != null) {
            inputStreamServer.stop()
        }
        super.onDestroy()
    }

    override fun onPostResume() {
        if(!checkPermissions()){
            requestPermissions()
        }
        super.onPostResume()
    }

    private fun getMyIpAddress(){
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        myIpAddress = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
        Log.i("MainActivity","ipAddress: $myIpAddress")
    }

    override fun onAudioFocusChange(focusChange: Int) {
        Log.i("MainActivity","focusChange: $focusChange")
    }

}