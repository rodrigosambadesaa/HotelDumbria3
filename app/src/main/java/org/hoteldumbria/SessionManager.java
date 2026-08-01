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

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONObject;

import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.List;

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
    private static volatile String refreshToken;
    private static volatile boolean persistRefreshToken;
    private static final Object REFRESH_LOCK = new Object();
    private static final List<RefreshCallback> REFRESH_CALLBACKS = new ArrayList<>();
    private static boolean refreshInFlight;
    private static int sessionGeneration;

    private SessionManager() {}

    public static String getAccessToken() { return accessToken; }
    public static void setAccessToken(String token) { accessToken = token; }

    public static void startSession(
            Context context,
            String newAccessToken,
            String newRefreshToken,
            boolean remember
    ) {
        synchronized (REFRESH_LOCK) {
            sessionGeneration++;
            accessToken = newAccessToken;
            refreshToken = newRefreshToken;
            persistRefreshToken = remember && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2;
            if (persistRefreshToken) {
                persistRefreshToken(context, newRefreshToken);
            } else {
                clearPersistedToken(context);
            }
        }
    }

    public static void updateRotatedTokens(
            Context context,
            String newAccessToken,
            String newRefreshToken
    ) {
        accessToken = newAccessToken;
        refreshToken = newRefreshToken;
        if (persistRefreshToken) persistRefreshToken(context, newRefreshToken);
    }

    public static String getRefreshToken(Context context) {
        return refreshToken != null ? refreshToken : loadRefreshToken(context);
    }

    public interface RefreshCallback {
        void onComplete(boolean refreshed);
    }

    /** Coalesces simultaneous 401 responses into one refresh-token rotation. */
    public static void refreshAccessToken(Context context, RefreshCallback callback) {
        Context applicationContext = context.getApplicationContext();
        final int refreshGeneration;
        synchronized (REFRESH_LOCK) {
            REFRESH_CALLBACKS.add(callback);
            if (refreshInFlight) return;
            refreshInFlight = true;
            refreshGeneration = sessionGeneration;
        }
        String currentRefreshToken = getRefreshToken(applicationContext);
        if (currentRefreshToken == null || currentRefreshToken.isEmpty()) {
            finishRefresh(false);
            return;
        }
        JSONObject payload = new JSONObject();
        try {
            payload.put("refreshToken", currentRefreshToken);
        } catch (Exception error) {
            finishRefresh(false);
            return;
        }
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                WebService.getRefreshUrl(),
                payload,
                response -> {
                    try {
                        if (!updateRotatedTokensIfCurrent(
                                applicationContext,
                                response.getString("accessToken"),
                                response.getString("refreshToken"),
                                refreshGeneration
                        )) {
                            finishRefresh(false);
                            return;
                        }
                        finishRefresh(true);
                    } catch (Exception error) {
                        clearIfCurrent(applicationContext, refreshGeneration);
                        finishRefresh(false);
                    }
                },
                error -> {
                    clearIfCurrent(applicationContext, refreshGeneration);
                    finishRefresh(false);
                }
        );
        request.setTag("SessionRefresh");
        PeticionesRed.getInstancia(applicationContext).anhadirPeticionACola(request);
    }

    private static void finishRefresh(boolean success) {
        List<RefreshCallback> callbacks;
        synchronized (REFRESH_LOCK) {
            callbacks = new ArrayList<>(REFRESH_CALLBACKS);
            REFRESH_CALLBACKS.clear();
            refreshInFlight = false;
        }
        for (RefreshCallback callback : callbacks) callback.onComplete(success);
    }

    public static void saveRefreshToken(Context context, String token) {
        refreshToken = token;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            Log.w(TAG, "Persistencia desactivada: Android Keystore no está disponible");
            return;
        }
        persistRefreshToken = true;
        persistRefreshToken(context, token);
    }

    private static void persistRefreshToken(Context context, String token) {
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
            refreshToken = new String(cipher.doFinal(encrypted), "UTF-8");
            persistRefreshToken = true;
            return refreshToken;
        } catch (Exception error) {
            Log.e(TAG, "La sesión almacenada ya no es válida", error);
            clear(context);
            return null;
        }
    }

    public static void clear(Context context) {
        synchronized (REFRESH_LOCK) {
            sessionGeneration++;
            accessToken = null;
            refreshToken = null;
            persistRefreshToken = false;
            clearPersistedToken(context);
        }
    }

    private static boolean updateRotatedTokensIfCurrent(
            Context context,
            String newAccessToken,
            String newRefreshToken,
            int expectedGeneration
    ) {
        synchronized (REFRESH_LOCK) {
            if (sessionGeneration != expectedGeneration) return false;
            updateRotatedTokens(context, newAccessToken, newRefreshToken);
            return true;
        }
    }

    private static void clearIfCurrent(Context context, int expectedGeneration) {
        synchronized (REFRESH_LOCK) {
            if (sessionGeneration == expectedGeneration) clear(context);
        }
    }

    private static void clearPersistedToken(Context context) {
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
