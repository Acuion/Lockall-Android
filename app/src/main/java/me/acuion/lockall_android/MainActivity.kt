package me.acuion.lockall_android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import java.io.FileInputStream
import java.net.Inet4Address
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.security.KeyStore

class MainActivity : Activity() {

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when(requestCode) {
            42 -> {
                runOnUiThread {
                    val base64FromQr = data!!.extras.getString("data")
                    val qrBytes : ByteBuffer
                    try {
                        qrBytes = ByteBuffer.wrap(Base64.decode(base64FromQr, 0))
                    } catch (ex : IllegalArgumentException) {
                        Toast.makeText(applicationContext, "Base64 decode failed", Toast.LENGTH_LONG).show()
                        return@runOnUiThread
                    }
                    qrBytes.order(ByteOrder.LITTLE_ENDIAN)
                    // 4len + fc
                    // 4len + sc
                    // 4ip
                    // 4port
                    // {rest} - machine+username
                    var fcLen = qrBytes.getInt()
                    val firstComponent = ByteArray(fcLen) //
                    qrBytes.get(firstComponent)
                    val scLen = qrBytes.getInt()
                    val secondComponent = ByteArray(scLen) //
                    qrBytes.get(secondComponent)
                    val ipBuffer = ByteArray(4)
                    qrBytes.get(ipBuffer)
                    val localPort = qrBytes.getInt() //
                    val hostNameBuffer = ByteArray(qrBytes.remaining())
                    qrBytes.get(hostNameBuffer)

                    val localIp = Inet4Address.getByAddress(ipBuffer) //
                    val hostName = String(hostNameBuffer, Charset.forName("UTF-8")) //

                    Toast.makeText(applicationContext,
                            "$firstComponent and $secondComponent from $hostName at $localIp:$localPort", Toast.LENGTH_LONG).show()
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
