package com.dtops.payment.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec

class KeystoreManager {
    private val keyAlias = "dtops_key"
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    init {
        if (!keyStore.containsAlias(keyAlias)) {
            generateKey()
        }
    }

    private fun generateKey() {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore"
        )
        
        try {
            val spec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setIsStrongBoxBacked(true)
                .build()
            keyPairGenerator.initialize(spec)
            keyPairGenerator.generateKeyPair()
        } catch (e: StrongBoxUnavailableException) {
            // Fallback to TEE if StrongBox is unavailable
            val spec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setIsStrongBoxBacked(false)
                .build()
            keyPairGenerator.initialize(spec)
            keyPairGenerator.generateKeyPair()
        }
    }

    fun getPublicKey(): PublicKey {
        return keyStore.getCertificate(keyAlias).publicKey
    }
    
    fun getPublicKeyPem(): String {
        val pubKey = getPublicKey()
        val encoded = Base64.encodeToString(pubKey.encoded, Base64.NO_WRAP)
        return "-----BEGIN PUBLIC KEY-----\n$encoded\n-----END PUBLIC KEY-----"
    }

    fun sign(payload: String): String {
        val privateKey = keyStore.getKey(keyAlias, null) as PrivateKey
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(payload.toByteArray(Charsets.UTF_8))
        val sigBytes = signature.sign()
        return Base64.encodeToString(sigBytes, Base64.NO_WRAP)
    }

    fun verify(payload: String, signatureStr: String, pubKey: PublicKey): Boolean {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initVerify(pubKey)
        signature.update(payload.toByteArray(Charsets.UTF_8))
        val sigBytes = Base64.decode(signatureStr, Base64.NO_WRAP)
        return signature.verify(sigBytes)
    }

    fun verifySignature(payload: String, signatureStr: String, pubKeyPem: String): Boolean {
        try {
            val pubKeyString = pubKeyPem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\n", "")
                .trim()
            val pubKeyBytes = Base64.decode(pubKeyString, Base64.NO_WRAP)
            val keyFactory = KeyFactory.getInstance("EC")
            val x509KeySpec = X509EncodedKeySpec(pubKeyBytes)
            val pubKey = keyFactory.generatePublic(x509KeySpec)
            return verify(payload, signatureStr, pubKey)
        } catch (e: Exception) {
            return false
        }
    }
}

