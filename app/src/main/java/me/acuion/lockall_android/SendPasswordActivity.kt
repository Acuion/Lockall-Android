package me.acuion.lockall_android

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import java.net.ServerSocket
import kotlin.concurrent.thread

class SendPasswordActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_send_password)

        thread(isDaemon = true, name = "TCPListener") {
            println("Runs in a separate thread")
            var sck = ServerSocket(42424)
            var connection = sck.accept()
            runOnUiThread {
                Toast.makeText(applicationContext, "Yay!", Toast.LENGTH_LONG).show()
                println("Yay ")
            }
        }
    }
}
