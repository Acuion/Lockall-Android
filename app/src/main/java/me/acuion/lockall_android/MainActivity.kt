package me.acuion.lockall_android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import java.io.FileInputStream
import java.net.Inet4Address
import java.nio.charset.Charset
import java.security.KeyStore

class MainActivity : Activity() {

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when(requestCode) {
            42 -> {
                runOnUiThread {
                    val base64FromQr = data!!.extras.getString("data")
                    val qrBytes : ByteArray
                    try {
                        qrBytes = Base64.decode(base64FromQr, 0)
                    } catch (ex : IllegalArgumentException) {
                        Toast.makeText(applicationContext, "Base64 decode failed", Toast.LENGTH_LONG).show()
                        return@runOnUiThread
                    }

                    // 4len + fc
                    // 4len + sc
                    // 4ip
                    // 4port
                    // {rest} - machine+username
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        buttonPair.setOnClickListener {
            val qrIntent = Intent(applicationContext, ScanQrActivity::class.java)
            qrIntent.putExtra("prefix", "PAIRING")
            startActivityForResult(qrIntent, 42)
        }
    }
}
