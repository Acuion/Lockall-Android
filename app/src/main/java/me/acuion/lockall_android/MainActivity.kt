package me.acuion.lockall_android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.beust.klaxon.Klaxon
import kotlinx.android.synthetic.main.activity_main.*
import me.acuion.lockall_android.crypto.EncryptionUtils
import me.acuion.lockall_android.messages.pairing.MessageWithName
import java.util.*

class MainActivity : Activity() {

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when(requestCode) {
            42 -> { // pairing
                runOnUiThread {
                    val base64FromQr = data!!.extras.getString("data")

                    val qrData = QrMessage(base64FromQr)
                    val qrContent = Klaxon().parse<MessageWithName>(qrData.userDataJson)!!
                    //TODO("Set the first component globally")
                    val key = EncryptionUtils.produce256BitsFromComponents(qrData.firstComponent!!,
                            qrData.secondComponent)
                    val message = NetworkMessage(key,
                            Klaxon().toJsonString(MessageWithName(qrContent.name)))
                    message.send(qrData.hostAddress, qrData.hostPort)
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
