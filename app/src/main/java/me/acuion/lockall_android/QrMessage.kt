package me.acuion.lockall_android

import android.util.Base64
import android.widget.Toast
import me.acuion.lockall_android.crypto.EncryptionUtils
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

// QR:
// 00/01 - safe or unsafe (without the first component or with)
// if unsafe, then the first component's length 4 bytes and the first component itself
// the second component length 4 bytes
// the second component itself
// IV 16 bytes
// the encrypted body length
// the encrypted body itself

// Unencrypted body:
// host's ip 4 bytes
// host's port 4 bytes
// user data...

class QrMessage(base64Data: String) {
    val firstComponent : ByteArray?
    val secondComponent : ByteArray
    val hostAddress : InetAddress
    val hostPort : Int
    val userData : ByteArray

    init {
        val qrBytes = ByteBuffer.wrap(Base64.decode(base64Data, 0))
        qrBytes.order(ByteOrder.LITTLE_ENDIAN)
        if (qrBytes.get().compareTo(1) == 0) {
            var fcLen = qrBytes.getInt()
            firstComponent = ByteArray(fcLen)
            qrBytes.get(firstComponent)
        } else {
            firstComponent = null
        }
        val scLen = qrBytes.getInt()
        secondComponent = ByteArray(scLen)
        qrBytes.get(secondComponent)
        val iv = ByteArray(16)
        qrBytes.get(iv)
        val encryptedBodyLen = qrBytes.getInt()
        val encryptedBody = ByteArray(encryptedBodyLen)
        qrBytes.get(encryptedBody)
        val key : ByteArray
        if (firstComponent != null) {
            key = EncryptionUtils.produce256BitsFromComponents(firstComponent, secondComponent)
        } else {
            TODO("Read global first component")
        }
        val userBytes = ByteBuffer.wrap(EncryptionUtils.decryptDataWithAes256(encryptedBody, key, iv))
        userBytes.order(ByteOrder.LITTLE_ENDIAN)
        val hostIpBytes = ByteArray(4)
        userBytes.get(hostIpBytes)
        hostAddress = InetAddress.getByAddress(hostIpBytes)
        hostPort = userBytes.getInt()
        userData = ByteArray(userBytes.remaining())
        userBytes.get(userData)
    }
}