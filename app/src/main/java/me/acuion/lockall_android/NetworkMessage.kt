package me.acuion.lockall_android

import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.launch
import me.acuion.lockall_android.crypto.EncryptionUtils
import java.net.InetAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

class NetworkMessage(key : ByteArray, userDataJson : String) {
    val readyMessage : ByteArray

    init {
        val iv = EncryptionUtils.generate128bitIv()
        val encryptedUserData = EncryptionUtils.encryptDataWithAes256(
                userDataJson.toByteArray(Charset.forName("UTF-8")), key, iv)

        val message  = ByteBuffer.allocate(4 + iv.size + encryptedUserData.size)
        message.order(ByteOrder.LITTLE_ENDIAN)
        message.put(iv)
        message.putInt(encryptedUserData.size)
        message.put(encryptedUserData)
        readyMessage = message.array()
    }

    fun send(hostIp : InetAddress, hostPort : Int) : Job {
        return launch {//TODO("check it")
            val toHostConn = Socket(hostIp, hostPort)
            toHostConn.getOutputStream().write(readyMessage)
            toHostConn.close()
        }
    }
}