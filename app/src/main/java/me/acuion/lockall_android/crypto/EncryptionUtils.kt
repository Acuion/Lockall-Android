package me.acuion.lockall_android.crypto

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class EncryptionUtils {
    companion object
    {
        fun produce256BitsFromComponents(comp1: ByteArray, comp2: ByteArray): ByteArray {
            val merged = ByteBuffer.allocate(comp1.size + comp2.size)
            merged.put(comp1)
            merged.put(comp2)
            return MessageDigest.getInstance("SHA-256").digest(merged.array())
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
