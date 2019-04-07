package me.acuion.lockall_android.crypto

import android.app.KeyguardManager
import android.content.Context
import android.content.Context.KEYGUARD_SERVICE
import android.security.keystore.KeyProperties
import javax.crypto.KeyGenerator
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import android.widget.Toast
import android.support.v4.os.CancellationSignal
import javax.crypto.spec.GCMParameterSpec


class EncryptedJsonStorageManager(val context : Context, val encfile : Filename) {
    enum class Filename(val fname : String) {
        PasswordsStorage("passwordsStorage"),
        OtpsStorage("otpsStorage")
    }

    var data : JsonObject?
    get() {
        val file = File(context.filesDir, encfile.fname)

        if (!file.exists()) {
            return JsonObject()
        }

        val buffer = ByteBuffer.wrap(file.readBytes())

        val iv = ByteArray(12) // for GCM
        buffer.get(iv)
        val encryptedData = ByteArray(buffer.remaining())
        buffer.get(encryptedData)

        val ahelper = CryptoHelper(GCMParameterSpec(128, iv))
        val cipher = ahelper.getCipher(false)
        if (cipher == null) {
            if (ahelper.invalidatedKey) {
                Toast.makeText(context, "The key was invalidated, the storage is lost!", Toast.LENGTH_SHORT).show()
                return JsonObject()  // invalidated :(
            } else
                Toast.makeText(context, "Failed to initialize a cryptographic backend :(", Toast.LENGTH_SHORT).show()
            return null
        }
        return try {
            val jsonString = String(cipher.doFinal(encryptedData), Charset.forName("UTF-8"))
            JsonParser().parse(jsonString).asJsonObject
        }
        catch (ex : Exception) {
            Toast.makeText(context, "Failed to decrypt, the storage is lost!", Toast.LENGTH_SHORT).show()
            ex.printStackTrace()
            JsonObject() // invalidated :(
        }
    }
    set(value) {
        val ahelper = CryptoHelper()
        val cipher = ahelper.getCipher(true)
        if (cipher == null) {
            Toast.makeText(context, "Failed to initialize a cryptographic backend :(", Toast.LENGTH_SHORT).show()
            throw RuntimeException()
        }

        val encryptedData = cipher.doFinal(value.toString()
                .toByteArray(Charset.forName("UTF-8")))
        val file = File(context.filesDir, encfile.fname)
        file.writeBytes(cipher.iv)
        file.appendBytes(encryptedData)
    }

    private enum class CryptoMode {
        ENCRYPT, DECRYPT
    }

    private class CryptoHelper {
        private var keyStore : KeyStore? = null
        private var keyGenerator : KeyGenerator? = null
        private var cipher : Cipher? = null

        var iv : GCMParameterSpec? = null
        val mode : CryptoMode
        val alias : String = "LOCKALL_KEY_32SEC_GCM"
        var invalidatedKey : Boolean = false

        fun getCipher(recreateKeyIfNeeded : Boolean) : Cipher? {
            if (prepareKeyStore() && prepareCipher() && prepareKey())
            {
                if (initCipher())
                    return cipher
                if (recreateKeyIfNeeded) {
                    if (prepareKey() && initCipher())
                        return cipher
                }
            }
            return null
        }

        constructor(iv : GCMParameterSpec) {
            this.iv = iv
            mode = CryptoMode.DECRYPT
        }

        constructor() {
            mode = CryptoMode.ENCRYPT
        }

        private fun initCipher() : Boolean {
            try {
                val key = keyStore!!.getKey(alias, null)

                if (mode == CryptoMode.ENCRYPT) {
                    cipher!!.init(Cipher.ENCRYPT_MODE, key)
                } else {
                    cipher!!.init(Cipher.DECRYPT_MODE, key, iv)
                }

                return true
            } catch (ex : KeyPermanentlyInvalidatedException) {
                invalidatedKey = true
                try {
                    keyStore!!.deleteEntry(alias)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            } catch (ex : Exception) {
                ex.printStackTrace()
            }
            return false
        }

        private fun prepareKeyStore() : Boolean {
            if (keyStore != null)
                return true
            try {
                keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore!!.load(null)
                return true
            }
            catch(ex : Exception) {
                ex.printStackTrace()
            }
            return false
        }

        private fun prepareCipher() : Boolean {
            if (cipher != null)
                return true
            try {
                cipher = Cipher.getInstance("AES/GCM/NoPadding")
                return true
            }
            catch (ex : Exception) {
                ex.printStackTrace()
            }
            return false
        }

        private fun prepareKey() : Boolean {
            try {
                return keyStore!!.containsAlias(alias) || generateKey()
            }
            catch (ex : Exception) {
                ex.printStackTrace()
            }
            return false
        }

        private fun generateKey() : Boolean {
            if (!prepareKeyGenerator())
                return false
            try {
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(alias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setKeySize(256)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setUserAuthenticationRequired(true)
                        .setUserAuthenticationValidityDurationSeconds(32)
                        .build()
                keyGenerator!!.init(keyGenParameterSpec)
                keyGenerator!!.generateKey()
                return true
            }
            catch (ex : Exception) {
                ex.printStackTrace()
            }
            return false
        }

        private fun prepareKeyGenerator() : Boolean {
            try {
                keyGenerator = KeyGenerator
                        .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                return true
            }
            catch (ex : Exception) {
                ex.printStackTrace()
            }
            return false
        }
    }
}