package me.acuion.lockall_android

import android.util.Base64
import android.widget.Toast
import me.acuion.lockall_android.crypto.EncryptionUtils
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class QrContent(base64Data : String) {
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
        secondComponent = ByteArray(scLen) //
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
            TODO("Read associated first component")
        }

        val userBytes = ByteBuffer.wrap(EncryptionUtils.decryptDataWithAes256(key, iv, encryptedBody))
        val hostIpBytes = ByteArray(4)
        userBytes.get(hostIpBytes)
        hostAddress = InetAddress.getByAddress(hostIpBytes)
        hostPort = userBytes.getInt()
        userData = ByteArray(userBytes.remaining())
        userBytes.get(userData)
    }
}