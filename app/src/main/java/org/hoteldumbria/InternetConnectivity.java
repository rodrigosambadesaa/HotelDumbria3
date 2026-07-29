package org.hoteldumbria;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Version-aware network state and real Internet reachability checks.
 *
 * NetworkCapabilities is used wherever the platform supports it. NetworkInfo is
 * deliberately isolated to Android 4.1–5.1, where no modern equivalent exists.
 */
public final class InternetConnectivity {

    public interface Callback {
        void onResult(boolean reachable, @Nullable String reachedHost);
    }

    private static final List<String> PROBE_URLS = Collections.unmodifiableList(Arrays.asList(
            "https://www.google.com/generate_204",
            "https://www.facebook.com/",
            "https://www.wolframalpha.com/",
            "https://www.apple.com/",
            "https://www.amazon.com/"
    ));

    private static final int CONNECT_TIMEOUT_MS = 1_800;
    private static final int READ_TIMEOUT_MS = 1_800;
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private InternetConnectivity() {
    }

    public static void check(@NonNull Context context, @NonNull Callback callback) {
        final Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            String reachedHost = null;
            if (hasNetwork(appContext)) {
                for (String candidate : PROBE_URLS) {
                    if (probe(candidate)) {
                        reachedHost = candidate;
                        break; // Do not contact any remaining provider.
                    }
                }
            }
            final String result = reachedHost;
            MAIN_HANDLER.post(() -> callback.onResult(result != null, result));
        });
    }

    public static void runWhenAvailable(
            @NonNull Context context,
            @NonNull Runnable whenAvailable,
            @NonNull Runnable whenUnavailable
    ) {
        check(context, (reachable, reachedHost) -> {
            if (reachable) {
                whenAvailable.run();
            } else {
                whenUnavailable.run();
            }
        });
    }

    public static boolean hasNetwork(@NonNull Context context) {
        ConnectivityManager manager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network active = manager.getActiveNetwork();
            if (active == null) {
                return false;
            }
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(active);
            return capabilities != null
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            for (Network network : manager.getAllNetworks()) {
                NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
                if (capabilities != null
                        && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    return true;
                }
            }
            return false;
        }

        return getLegacyConnectedNetwork(manager) != null;
    }

    @SuppressWarnings("deprecation")
    @Nullable
    private static NetworkInfo getLegacyConnectedNetwork(ConnectivityManager manager) {
        NetworkInfo active = manager.getActiveNetworkInfo();
        return active != null && active.isAvailable() && active.isConnected() ? active : null;
    }

    private static boolean probe(String address) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setRequestProperty("Connection", "close");
            connection.connect();
            return connection.getResponseCode() > 0;
        } catch (IOException ignored) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
