package me.acuion.lockall_android.crypto

import java.security.*
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EcdhCompletionResult(val key: ByteArray, val mobilePublic: ByteArray)

class EncryptionUtils {
    companion object
    {
        fun completeEcdhGetKeyAndPublic(pcPublic: ByteArray): EcdhCompletionResult {
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(384)
            val kp = kpg.generateKeyPair()

            val kf = KeyFactory.getInstance("EC")
            val pkSpec = X509EncodedKeySpec(pcPublic)
            val otherPublicKey = kf.generatePublic(pkSpec)

            val ka = KeyAgreement.getInstance("ECDH")
            ka.init(kp.private)
            ka.doPhase(otherPublicKey, true)

            val hash = MessageDigest.getInstance("SHA-256")
            hash.update(ka.generateSecret())

            return EcdhCompletionResult(hash.digest(), kp.public.encoded)
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
