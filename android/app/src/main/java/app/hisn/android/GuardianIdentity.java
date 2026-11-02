package app.hisn.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.os.Build;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyPairGenerator;
import java.security.cert.Certificate;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import app.hisn.core.GuardianPairing;

/**
 * The phone's guardian identity.
 *
 * <p>On API 31+ the P-256 key lives inside AndroidKeyStore with sign and key
 * agreement purposes. Older runtimes use a software P-256 key wrapped with an
 * AES-256-GCM key held in AndroidKeyStore. The debug pairing harness exercises
 * both paths; the product pairing screen uses the same identity.
 */
final class GuardianIdentity {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String WRAP_ALIAS = "hisn.guardian.wrap.v1";
    private static final String EC_ALIAS = "hisn.guardian.ec.v1";
    private static final String PREFS = "hisn_guardian_identity";
    private static final String WRAPPED = "wrapped_pkcs8";
    private static final String IV = "iv";
    private static final String PUBLIC = "public_der";

    static final class Keys {
        final PrivateKey privateKey;
        final PublicKey publicKey;
        final String mode;
        Keys(PrivateKey privateKey, PublicKey publicKey, String mode) {
            this.privateKey = privateKey;
            this.publicKey = publicKey;
            this.mode = mode;
        }
    }

    static Keys loadOrCreate(Context context) {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                return loadOrCreateEc();
            } catch (RuntimeException unsupported) {
                // Older Keystore implementations or a missing key-agreement
                // purpose fall back to the wrapped software key below.
            }
        }
        return loadOrCreateWrapped(context);
    }

    private static Keys loadOrCreateEc() {
        try {
            KeyStore store = KeyStore.getInstance(KEYSTORE);
            store.load(null);
            if (!store.containsAlias(EC_ALIAS)) {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", KEYSTORE);
                KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(EC_ALIAS,
                        KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_AGREE_KEY)
                        .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .build();
                generator.initialize(spec);
                generator.generateKeyPair();
            }
            PrivateKey privateKey = (PrivateKey) store.getKey(EC_ALIAS, null);
            Certificate certificate = store.getCertificate(EC_ALIAS);
            if (privateKey == null || certificate == null) throw new IllegalStateException("missing EC entry");
            return new Keys(privateKey, certificate.getPublicKey(), "ec-keystore");
        } catch (Exception unavailable) {
            throw new IllegalStateException("EC Keystore unavailable", unavailable);
        }
    }

    private static Keys loadOrCreateWrapped(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String wrapped = prefs.getString(WRAPPED, null);
        String iv = prefs.getString(IV, null);
        String publicB64 = prefs.getString(PUBLIC, null);
        if (wrapped != null && iv != null && publicB64 != null) {
            try {
                byte[] plain = decrypt(Base64.decode(wrapped, Base64.NO_WRAP),
                        Base64.decode(iv, Base64.NO_WRAP));
                return new Keys(GuardianPairing.loadPrivateB64(
                        Base64.encodeToString(plain, Base64.NO_WRAP)),
                        GuardianPairing.loadPublicB64(publicB64), "aes-wrapped-software");
            } catch (RuntimeException unusable) {
                // A cleared Keystore or damaged preference gets a fresh identity.
            }
        }
        KeyPair generated = GuardianPairing.generateKeyPair();
        byte[] plain = generated.getPrivate().getEncoded();
        try {
            byte[][] sealed = encrypt(plain);
            prefs.edit()
                    .putString(WRAPPED, Base64.encodeToString(sealed[0], Base64.NO_WRAP))
                    .putString(IV, Base64.encodeToString(sealed[1], Base64.NO_WRAP))
                    .putString(PUBLIC, GuardianPairing.publicB64(generated.getPublic()))
                    .commit();
        } catch (RuntimeException unavailable) {
            throw new IllegalStateException("Cannot wrap the guardian identity", unavailable);
        }
        return new Keys(generated.getPrivate(), generated.getPublic(), "aes-wrapped-software");
    }

    private static byte[][] encrypt(byte[] plain) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, wrapKey());
            byte[] iv = cipher.getIV();
            return new byte[][]{cipher.doFinal(plain), iv};
        } catch (Exception unavailable) {
            throw new IllegalStateException("Cannot encrypt the guardian identity", unavailable);
        }
    }

    private static byte[] decrypt(byte[] sealed, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, wrapKey(), new GCMParameterSpec(128, iv));
            return cipher.doFinal(sealed);
        } catch (Exception unavailable) {
            throw new IllegalStateException("Cannot decrypt the guardian identity", unavailable);
        }
    }

    private static SecretKey wrapKey() {
        try {
            KeyStore store = KeyStore.getInstance(KEYSTORE);
            store.load(null);
            if (store.containsAlias(WRAP_ALIAS)) return (SecretKey) store.getKey(WRAP_ALIAS, null);
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(WRAP_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build();
            generator.init(spec);
            return generator.generateKey();
        } catch (Exception unavailable) {
            throw new IllegalStateException("Cannot open the identity wrapping key", unavailable);
        }
    }

    private GuardianIdentity() { }
}
