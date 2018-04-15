package me.acuion.lockall_android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*
import me.acuion.lockall_android.crypto.EncryptionUtils
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

class MainActivity : Activity() {

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when(requestCode) {
            42 -> {
                runOnUiThread {
                    val base64FromQr = data!!.extras.getString("data")

                    val qrData = QrMessage(base64FromQr)
                    //TODO("Set the first component globally")
                    val key = EncryptionUtils.produce256BitsFromComponents(qrData.firstComponent!!,
                            qrData.secondComponent)
                    val iv = EncryptionUtils.generate128bitIv()
                    val encryptedHostName = EncryptionUtils.encryptDataWithAes256(qrData.userData,
                            key, iv)

                    val message  = ByteBuffer.allocate(4 + iv.size + encryptedHostName.size)
                    message.order(ByteOrder.LITTLE_ENDIAN)
                    message.putInt(iv.size + encryptedHostName.size)
                    message.put(iv)
                    message.put(encryptedHostName) // todo: to a method

                    thread {
                        val toHostConn = Socket(qrData.hostAddress, qrData.hostPort)
                        toHostConn.getOutputStream().write(message.array())
                        toHostConn.close()
                    }
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
