package me.acuion.lockall_android.crypto

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.security.*
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EcdhCompletionResult(val key: ByteArray, val mobilePublic: ByteArray)

class EncryptionUtils {
    companion object
    {
        fun completeEcdhGetKeyAndPublic(pcPublic: ByteArray): EcdhCompletionResult {
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(521)
            val kp = kpg.generateKeyPair()

            val pk = PublicKey()
        }

        fun generate128bitIv() : ByteArray {
            val iv = ByteArray(16) // IV size == 128bit
            val random = SecureRandom()
            random.nextBytes(iv)
            return iv
        }

        fun encryptDataWithAes256(data: ByteArray, key: ByteArray, iv : ByteArray): ByteArray {
            val ecipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            ecipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            return ecipher.doFinal(data)
        }

        fun decryptDataWithAes256(data: ByteArray, key: ByteArray, iv : ByteArray): ByteArray {
            val ecipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            ecipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            return ecipher.doFinal(data)
        }

        fun hmacSha1(value: ByteArray, key: ByteArray): ByteArray {
            val type = "HmacSHA1"
            val secret = SecretKeySpec(key, type)
            val mac = Mac.getInstance(type)
            mac.init(secret)
            return mac.doFinal(value)
        }
    }
}
