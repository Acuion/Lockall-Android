package me.acuion.lockall_android

import com.google.gson.JsonObject
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.acuion.lockall_android.crypto.EncryptionUtils
import java.net.InetAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.widget.Toast
import android.support.v4.app.ActivityCompat.startActivityForResult
import android.content.Intent
import me.acuion.lockall_android.crypto.EcdhCompletionResult
import java.util.*


class NetworkMessage {
    val readyMessage : ByteArray

    constructor(keyStructure : EcdhCompletionResult) {
        val message  = ByteBuffer.allocate(4 + keyStructure.mobilePublic.size)
        message.order(ByteOrder.LITTLE_ENDIAN)
        message.putInt(keyStructure.mobilePublic.size)
        message.put(keyStructure.mobilePublic)
        readyMessage = message.array()
    }

    constructor(aes256Key: ByteArray, userDataJson : JsonObject) {
        val iv = EncryptionUtils.generate128bitIv()
        val encryptedUserData = EncryptionUtils.encryptDataWithAes256(
                userDataJson.toString().toByteArray(Charset.forName("UTF-8")), aes256Key, iv)

        val message  = ByteBuffer.allocate(4 + iv.size + encryptedUserData.size)
        message.order(ByteOrder.LITTLE_ENDIAN)
        message.put(iv)
        message.putInt(encryptedUserData.size)
        message.put(encryptedUserData)
        readyMessage = message.array()
    }
}