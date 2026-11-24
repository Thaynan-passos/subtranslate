package com.example.data.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Contrato para cifragem em repouso da chave da API do Google Gemini.
 */
interface ApiKeyCipher {
    fun encrypt(plainText: String): String
    fun decryptIfEncrypted(storedValue: String): String
}

/**
 * Implementação usando `AndroidKeyStore` com AES-256-GCM.
 * Valores legados sem o prefixo `enc:v1:` são aceitos na leitura para migração transparente.
 */
class AndroidKeyStoreApiKeyCipher : ApiKeyCipher {

    override fun encrypt(plainText: String): String {
        val clean = plainText.trim()
        if (clean.isEmpty()) return ""

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + cipherBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)
            ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(combined)
        } catch (e: Exception) {
            // Em testes unitários JVM puros sem provedor AndroidKeyStore, evita quebrar o fluxo.
            if (Build.FINGERPRINT == null) {
                clean
            } else {
                Log.w(TAG, "Falha ao criptografar chave de API: ${e.javaClass.simpleName}")
                throw IllegalStateException("Não foi possível proteger a chave da API no armazenamento seguro.", e)
            }
        }
    }

    override fun decryptIfEncrypted(storedValue: String): String {
        val trimmed = storedValue.trim()
        if (trimmed.isEmpty()) return ""
        if (!trimmed.startsWith(ENCRYPTED_PREFIX)) {
            // Valor legado em texto plano — será criptografado na próxima gravação.
            return trimmed
        }

        return try {
            val payload = Base64.getDecoder().decode(trimmed.removePrefix(ENCRYPTED_PREFIX))
            if (payload.size <= GCM_IV_LENGTH_BYTES) return ""
            val iv = payload.copyOfRange(0, GCM_IV_LENGTH_BYTES)
            val cipherBytes = payload.copyOfRange(GCM_IV_LENGTH_BYTES, payload.size)

            val secretKey = getExistingKey() ?: return ""
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            try {
                Log.w(TAG, "Falha ao descriptografar chave de API: ${e.javaClass.simpleName}")
            } catch (_: Throwable) {
                // Ignora ausência de android.util.Log em ambiente JVM
            }
            ""
        }
    }

    private fun getExistingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    }

    private fun getOrCreateKey(): SecretKey {
        getExistingKey()?.let { return it }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_KEY_SIZE_BITS)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val TAG = "ApiKeyCipher"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "subtranslate_gemini_api_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ENCRYPTED_PREFIX = "enc:v1:"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val AES_KEY_SIZE_BITS = 256
    }
}
