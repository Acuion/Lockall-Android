package me.acuion.lockall_android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import java.net.Inet4Address
import java.nio.charset.Charset

class MainActivity : Activity() {

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when(requestCode) {
            42 -> {
                runOnUiThread {
                    val base64FromQr = data!!.extras.getString("qr")
                    var qrBytes : ByteArray
                    try {
                        qrBytes = Base64.decode(base64FromQr, 0)
                    } catch (ex : IllegalArgumentException) {
                        Toast.makeText(applicationContext, "Base64 decode failed", Toast.LENGTH_LONG).show() // debug
                        return@runOnUiThread
                    }
                    val key = qrBytes.sliceArray(IntRange(0, 31))
                    val localIp = Inet4Address.getByAddress(qrBytes.sliceArray(IntRange(32, 35)))
                    val username = String(qrBytes.sliceArray(IntRange(36, qrBytes.size - 1)), Charset.forName("UTF-8"))
                    Toast.makeText(applicationContext, "Success: $username at $localIp", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        buttonPair.setOnClickListener {
            var qrIntent = Intent(applicationContext, PairingActivity::class.java)
            startActivityForResult(qrIntent, 42)
        }
    }
}
