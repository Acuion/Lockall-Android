package me.acuion.lockall_android.crypto

import android.content.Context
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
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import android.widget.Toast
import android.R.string.cancel
import android.support.v4.os.CancellationSignal
import com.google.gson.JsonSyntaxException


class EncryptedJsonStorageManager(val context : Context, val encfile : Filename) {
    companion object {
        enum class Filename(val fname : String) {
            FirstComponentsStorage("firstComponentsStorage"),
            PasswordsStorage("passwordsStorage")
        }
    }

    private fun getAuthorizedChipher() {

    }

    fun loadData(cancellationSignal: CancellationSignal, callback : (data : JsonObject?) -> Unit) {
        val file = File(context.filesDir, encfile.fname)

        if (!file.exists()) {
            callback(JsonObject())
            return
        }

        val buffer = ByteBuffer.wrap(file.readBytes())

        val iv = ByteArray(16) // 128bit block
        buffer.get(iv)
        val encryptedData = ByteArray(buffer.remaining())
        buffer.get(encryptedData)

        val ahelper = CryptoHelperForAlias(encfile.fname, IvParameterSpec(iv))
        val fpco = ahelper.fingerprintCryptoObject
        if (fpco == null) {
            callback(null)
            return
        }

        val fphelper = FingerprintHelper(context, cancellationSignal)
        fphelper.startAuth(fpco) {
            try {
                val jsonString = String(it.doFinal(encryptedData), Charset.forName("UTF-8"))
                callback(JsonParser().parse(jsonString).asJsonObject)
            }
            catch (ex : Exception) {
                ex.printStackTrace()
                callback(JsonObject()) // invalidated :(
            }
        }
    }

    fun setData(value : JsonObject, cancellationSignal: CancellationSignal, callback : (success : Boolean) -> Unit) {
        val ahelper = CryptoHelperForAlias(encfile.fname)
        val fpco = ahelper.fingerprintCryptoObject
        if (fpco == null) {
            callback(false)
            return
        }

        val fphelper = FingerprintHelper(context, cancellationSignal)
        fphelper.startAuth(fpco) {
            try {
                val encryptedData = it.doFinal(value.toString()
                        .toByteArray(Charset.forName("UTF-8")))
                val file = File(context.filesDir, encfile.fname)
                file.writeBytes(it.iv)
                file.appendBytes(encryptedData)
                callback(true)
            } catch (ex :Exception) {
                ex.printStackTrace()
                callback(false)
            }
        }
    }

    private enum class CryptoMode {
        ENCRYPT, DECRYPT
    }

    private class CryptoHelperForAlias {
        private var keyStore : KeyStore? = null
        private var cipher : Cipher? = null
        private var keyGenerator : KeyGenerator? = null

        val fingerprintCryptoObject : FingerprintManagerCompat.CryptoObject?
        get() {
            return if (prepareKeyStore() && prepareCipher() && prepareKey() && initCipher())
                FingerprintManagerCompat.CryptoObject(cipher)
            else
                null
        }
        var iv : IvParameterSpec? = null
        val mode : CryptoMode
        val alias : String

        constructor(alias : String, iv : IvParameterSpec) {
            this.iv = iv
            mode = CryptoMode.DECRYPT
            this.alias = alias
        }

        constructor(alias : String) {
            mode = CryptoMode.ENCRYPT
            this.alias = alias
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
                cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
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
                        .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                        .setUserAuthenticationRequired(true)
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

    private class FingerprintHelper (private val mContext: Context,
                                     private val cancellationSignal: CancellationSignal)
        : FingerprintManagerCompat.AuthenticationCallback() {

        lateinit var callback : (cipher : Cipher) -> Unit

        fun startAuth(cryptoObject: FingerprintManagerCompat.CryptoObject, callback : (cipher : Cipher) -> Unit) {
            this.callback = callback
            val manager = FingerprintManagerCompat.from(mContext)
            manager.authenticate(cryptoObject, 0, cancellationSignal, this, null)
            Toast.makeText(mContext, "Finger!", Toast.LENGTH_LONG).show()
        }

        override fun onAuthenticationError(errMsgId: Int, errString: CharSequence?) {
            Toast.makeText(mContext, errString, Toast.LENGTH_SHORT).show()
        }

        override fun onAuthenticationHelp(helpMsgId: Int, helpString: CharSequence?) {
            Toast.makeText(mContext, helpString, Toast.LENGTH_SHORT).show()
        }

        override fun onAuthenticationSucceeded(result: FingerprintManagerCompat.AuthenticationResult?) {
            callback(result!!.cryptoObject.cipher)
            Toast.makeText(mContext, "success", Toast.LENGTH_SHORT).show()
        }

        override fun onAuthenticationFailed() {
            Toast.makeText(mContext, "try again", Toast.LENGTH_SHORT).show()
        }

    }

}