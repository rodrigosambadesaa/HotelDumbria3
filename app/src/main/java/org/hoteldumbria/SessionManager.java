package org.hoteldumbria;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Calendar;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.security.auth.x500.X500Principal;

/**
 * El access token vive solo en memoria. El refresh token se cifra con Android
 * Keystore en API 18+. API 16/17 siguen siendo compatibles, pero no persisten
 * sesiones porque esas versiones no ofrecen almacenamiento criptográfico seguro.
 */
public final class SessionManager {
    private static final String TAG = "SessionManager";
    private static final String PREFS = "hotel_secure_session";
    private static final String ALIAS = "hotel_dumbria_refresh_v2";
    private static volatile String accessToken;

    private SessionManager() {}

    public static String getAccessToken() { return accessToken; }
    public static void setAccessToken(String token) { accessToken = token; }

    public static void saveRefreshToken(Context context, String token) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            Log.w(TAG, "Persistencia desactivada: Android Keystore no está disponible");
            return;
        }
        try {
            byte[] encrypted;
            byte[] iv = new byte[0];
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                KeyStore store = loadStore();
                if (!store.containsAlias(ALIAS)) {
                    KeyGenerator generator = KeyGenerator.getInstance(
                            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
                    generator.init(new KeyGenParameterSpec.Builder(
                            ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .build());
                    generator.generateKey();
                }
                SecretKey key = (SecretKey) store.getKey(ALIAS, null);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, key);
                iv = cipher.getIV();
                encrypted = cipher.doFinal(token.getBytes("UTF-8"));
            } else {
                KeyStore store = loadStore();
                if (!store.containsAlias(ALIAS)) createLegacyKeyPair(context);
                PublicKey key = store.getCertificate(ALIAS).getPublicKey();
                Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                cipher.init(Cipher.ENCRYPT_MODE, key);
                encrypted = cipher.doFinal(token.getBytes("UTF-8"));
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString("refresh", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .putString("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                    .apply();
        } catch (Exception error) {
            Log.e(TAG, "No se pudo guardar la sesión cifrada", error);
            clear(context);
        }
    }

    public static String loadRefreshToken(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) return null;
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String encoded = preferences.getString("refresh", null);
        if (encoded == null) return null;
        try {
            byte[] encrypted = Base64.decode(encoded, Base64.NO_WRAP);
            Cipher cipher;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                SecretKey key = (SecretKey) loadStore().getKey(ALIAS, null);
                byte[] iv = Base64.decode(preferences.getString("iv", ""), Base64.NO_WRAP);
                cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            } else {
                PrivateKey key = (PrivateKey) loadStore().getKey(ALIAS, null);
                cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                cipher.init(Cipher.DECRYPT_MODE, key);
            }
            return new String(cipher.doFinal(encrypted), "UTF-8");
        } catch (Exception error) {
            Log.e(TAG, "La sesión almacenada ya no es válida", error);
            clear(context);
            return null;
        }
    }

    public static void clear(Context context) {
        accessToken = null;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static KeyStore loadStore() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        return store;
    }

    @SuppressLint("NewApi")
    private static void createLegacyKeyPair(Context context) throws Exception {
        Calendar start = Calendar.getInstance();
        Calendar end = Calendar.getInstance();
        end.add(Calendar.YEAR, 20);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
        generator.initialize(new KeyPairGeneratorSpec.Builder(context)
                .setAlias(ALIAS)
                .setSubject(new X500Principal("CN=Hotel Dumbria Session"))
                .setSerialNumber(BigInteger.ONE)
                .setStartDate(start.getTime())
                .setEndDate(end.getTime())
                .build());
        generator.generateKeyPair();
    }
}
