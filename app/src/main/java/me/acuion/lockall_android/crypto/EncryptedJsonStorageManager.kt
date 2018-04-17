package me.acuion.lockall_android.crypto

import android.content.Context
import android.security.keystore.KeyProperties
import javax.crypto.KeyGenerator
import android.security.keystore.KeyGenParameterSpec
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.acuion.lockall_android.FirstComponentsStorage
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

class EncryptedJsonStorageManager(val context : Context, val filename : Filename) {
    companion object {
        enum class Filename(val filename : String) {
            FirstComponentsStorage("firstComponentsStorage")
        }
    }

    var data : JsonObject

    get() {
        val file = File(context.filesDir, filename.filename)

        if (!file.exists())
            return JsonObject()

        val buffer = ByteBuffer.wrap(file.readBytes())

        val iv = ByteArray(16) // 128bit block
        buffer.get(iv)
        val encryptedData = ByteArray(buffer.remaining())
        buffer.get(encryptedData)

        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        val secretKeyEntry = keyStore
                .getEntry(filename.filename, null) as KeyStore.SecretKeyEntry
        val secretKey = secretKeyEntry.secretKey

        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
        val jsonString = String(cipher.doFinal(encryptedData), Charset.forName("UTF-8"))
        return JsonParser().parse(jsonString).asJsonObject
    }
    set(value) {
        val keyGenerator : KeyGenerator = KeyGenerator
                .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(filename.filename,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .build()
        keyGenerator.init(keyGenParameterSpec)
        val secretKey : SecretKey = keyGenerator.generateKey()

        val cipher : Cipher  = Cipher.getInstance("AES/CBC/PKCS7Padding") // TODO("CBC vs GCM")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv  = cipher.iv

        val encryptedData = cipher.doFinal(value.toString()
                .toByteArray(Charset.forName("UTF-8")))

        val file = File(context.filesDir, filename.filename)
        file.writeBytes(iv)
        file.appendBytes(encryptedData)
    }
}