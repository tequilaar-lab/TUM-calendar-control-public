/*
 * 职责：以 Android Keystore 中不可导出的 AES-GCM 密钥加密／解密少量私有配置文本。
 * 输入／输出：输入明文或 Base64 封装密文；输出对应文本或受控失败。
 * 外部副作用：首次使用时在 Android Keystore 创建本应用密钥；不联网、不记录明文。
 * 不负责：用户认证、生物识别、令牌刷新、备份或跨设备密钥迁移。
 */
package com.tequila.tumcalendarcontrol.sync

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeystoreTextCipher {
    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val payload = ByteBuffer.allocate(1 + cipher.iv.size + encrypted.size)
            .put(cipher.iv.size.toByte()).put(cipher.iv).put(encrypted).array()
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    fun decrypt(encodedPayload: String): String {
        val payload = Base64.decode(encodedPayload, Base64.NO_WRAP)
        require(payload.isNotEmpty()) { "私有配置密文为空。" }
        val ivLength = payload[0].toInt() and 0xff
        require(ivLength in 12..16 && payload.size > ivLength + 1) { "私有配置密文格式无效。" }
        val buffer = ByteBuffer.wrap(payload, 1, payload.size - 1)
        val iv = ByteArray(ivLength).also(buffer::get)
        val encrypted = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "tum_calendar_private_sync_configuration_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
    }
}
