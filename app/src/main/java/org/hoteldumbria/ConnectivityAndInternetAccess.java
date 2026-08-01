/*
 * SPDX-License-Identifier: MIT
 *
 * Based on Connectivity.java by str4d:
 * https://gist.github.com/str4d/22cac7a3f70bc227cdca
 * The original source identifies Emil and str4d as its authors.
 * Modernized extensions copyright (c) 2020-2026 Rodrigo Sambade.
 * See docs/CONNECTIVITY_LICENSE.md for the complete notices.
 */
package org.hoteldumbria;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.TelephonyManager;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Java reimplementation of the original Kotlin connectivity helper.
 *
 * <p>Supports Android 4.1 (API 16) and newer. Deprecated NetworkInfo APIs are
 * isolated to API 16-20, where NetworkCapabilities is unavailable.</p>
 */
@SuppressWarnings("deprecation")
public final class ConnectivityAndInternetAccess {

    public interface InternetCallback {
        void onResult(InternetResult result);
    }

    public static final class InternetResult {
        private final boolean reachable;
        private final String reachedHost;
        private final List<String> attemptedHosts;
        private final long elapsedMilliseconds;

        private InternetResult(
                boolean reachable,
                String reachedHost,
                List<String> attemptedHosts,
                long elapsedMilliseconds
        ) {
            this.reachable = reachable;
            this.reachedHost = reachedHost;
            this.attemptedHosts = Collections.unmodifiableList(
                    new ArrayList<>(attemptedHosts)
            );
            this.elapsedMilliseconds = elapsedMilliseconds;
        }

        public boolean isReachable() {
            return reachable;
        }

        public String getReachedHost() {
            return reachedHost;
        }

        public List<String> getAttemptedHosts() {
            return attemptedHosts;
        }

        public long getElapsedMilliseconds() {
            return elapsedMilliseconds;
        }
    }

    public static final class Request {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile Future<?> future;

        private Request() {
        }

        public void cancel() {
            cancelled.set(true);
            Future<?> task = future;
            if (task != null) {
                task.cancel(true);
            }
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        private void attach(Future<?> task) {
            future = task;
            if (cancelled.get()) {
                task.cancel(true);
            }
        }
    }

    private static final int MINIMUM_FAST_KBPS = 3_072;
    private static final int CONNECT_TIMEOUT_MS = 2_500;
    private static final int READ_TIMEOUT_MS = 2_500;
    private static final int DNS_TIMEOUT_MS = 1_500;
    private static final int DNS_PORT = 53;
    private static final long CONNECTION_ATTEMPT_TIMEOUT_MS = 30_000L;
    private static final String DNS_QUERY_NAME = "example.com";

    private static final List<String> DEFAULT_DNS_RESOLVERS =
            Collections.unmodifiableList(Arrays.asList(
                    "1.1.1.1",
                    "8.8.8.8",
                    "9.9.9.9",
                    "208.67.222.222"
            ));

    private static final List<String> DEFAULT_HOSTS =
            Collections.unmodifiableList(Arrays.asList(
                    "https://www.google.com/generate_204",
                    "https://www.facebook.com/",
                    "https://www.wolframalpha.com/",
                    "https://www.apple.com/",
                    "https://www.amazon.com/"
            ));

    private static volatile List<String> configuredHosts = DEFAULT_HOSTS;
    private static final AtomicBoolean CONNECTION_ATTEMPT =
            new AtomicBoolean(false);
    private static final AtomicInteger DNS_TRANSACTION_ID =
            new AtomicInteger((int) System.nanoTime());
    private static int connectionAttemptGeneration;

    private static final ExecutorService EXECUTOR =
            Executors.newCachedThreadPool(new ThreadFactory() {
                private int number;

                @Override
                public synchronized Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(
                            runnable,
                            "connectivity-check-" + (++number)
                    );
                    thread.setDaemon(true);
                    return thread;
                }
            });

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final SSLSocketFactory TLS_12_SOCKET_FACTORY = createTls12Factory();

    static SSLSocketFactory legacyTls12SocketFactory() {
        return TLS_12_SOCKET_FACTORY;
    }

    /** Preserves the original constructor that changes the configured hosts. */
    public ConnectivityAndInternetAccess(ArrayList<String> hosts) {
        configuredHosts = normalizeHosts(hosts);
    }

    /** Whether Android reports an active usable network. */
    public static boolean isActiveNetworkConnected(Context context) {
        ConnectivityManager manager = manager(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network active = manager.getActiveNetwork();
            return active != null && isConnected(context, active);
        }
        return isConnected(context);
    }

    /** Original network-specific overload. */
    public static boolean isConnected(Context context, Network network) {
        requireContext(context);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return isConnected(context);
        }
        if (network == null) {
            return false;
        }
        return isUsable(manager(context).getNetworkCapabilities(network));
    }

    /**
     * Detects the platform connecting state through API 28. API 29+ removed
     * that generic state, so callers that initiate a network request must call
     * beginConnectionAttempt() immediately before it.
     */
    public static boolean isConnecting(Context context) {
        requireContext(context);
        if (isConnected(context)) {
            CONNECTION_ATTEMPT.set(false);
            return false;
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            for (NetworkInfo info : legacyNetworks(manager(context))) {
                if (info != null && info.isConnectedOrConnecting()) {
                    return true;
                }
            }
        }
        return CONNECTION_ATTEMPT.get();
    }

    /**
     * Marks a connection attempt initiated by the app. Required only on API
     * 29+, where Android no longer exposes a generic CONNECTING state.
     */
    public static synchronized void beginConnectionAttempt(Context context) {
        requireContext(context);
        final int generation = ++connectionAttemptGeneration;
        CONNECTION_ATTEMPT.set(true);
        MAIN_HANDLER.postDelayed(() -> {
            synchronized (ConnectivityAndInternetAccess.class) {
                if (generation == connectionAttemptGeneration) {
                    CONNECTION_ATTEMPT.set(false);
                }
            }
        }, CONNECTION_ATTEMPT_TIMEOUT_MS);
    }

    /** Clears a connection attempt after failure or explicit cancellation. */
    public static synchronized void endConnectionAttempt() {
        connectionAttemptGeneration++;
        CONNECTION_ATTEMPT.set(false);
    }

    /** Corrected so the original method no longer throws on API 21+. */
    public static boolean isConnectedOrConnecting(Context context) {
        requireContext(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return isConnected(context);
        }
        for (NetworkInfo info : legacyNetworks(manager(context))) {
            if (info != null && info.isAvailable() && info.isConnectedOrConnecting()) {
                return true;
            }
        }
        return false;
    }

    /** Local connectivity only; use checkInternetAsync for real Internet access. */
    public static boolean isConnected(Context context) {
        requireContext(context);
        ConnectivityManager manager = manager(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            for (Network network : manager.getAllNetworks()) {
                if (isUsable(manager.getNetworkCapabilities(network))) {
                    CONNECTION_ATTEMPT.set(false);
                    return true;
                }
            }
            return false;
        }
        boolean connected = isConnectedLegacy(manager.getActiveNetworkInfo());
        if (connected) {
            CONNECTION_ATTEMPT.set(false);
        }
        return connected;
    }

    public static boolean isConnectedWifi(Context context) {
        return hasTransport(context, NetworkCapabilities.TRANSPORT_WIFI);
    }

    public static boolean isConnectedWifi(Context context, Network network) {
        return hasTransport(context, network, NetworkCapabilities.TRANSPORT_WIFI);
    }

    public static boolean isConnectedWifiOverAirplaneMode(Context context) {
        return isAirplaneModeOn(context) && isConnectedWifi(context);
    }

    public static boolean isConnectedWifiOverAirplaneMode(
            Context context,
            Network network
    ) {
        return isAirplaneModeOn(context) && isConnectedWifi(context, network);
    }

    /**
     * Preserves the TelephonyManager method. Android 12+ uses
     * NetworkCapabilities because dataState is deprecated there.
     */
    public static boolean isConnectedMobileTelephonyManager(Context context) {
        requireContext(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return isConnectedMobile(context);
        }
        TelephonyManager telephony =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return telephony != null
                && telephony.getDataState() == TelephonyManager.DATA_CONNECTED;
    }

    public static boolean isConnectedMobile(Context context, Network network) {
        return hasTransport(context, network, NetworkCapabilities.TRANSPORT_CELLULAR);
    }

    public static boolean isConnectedMobile(Context context) {
        return hasTransport(context, NetworkCapabilities.TRANSPORT_CELLULAR);
    }

    public static boolean isConnectedEthernet(Context context) {
        return hasTransport(context, NetworkCapabilities.TRANSPORT_ETHERNET);
    }

    public static boolean isConnectedEthernet(Context context, Network network) {
        return hasTransport(context, network, NetworkCapabilities.TRANSPORT_ETHERNET);
    }

    public static boolean isConnectedFast(Context context) {
        requireContext(context);
        ConnectivityManager manager = manager(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            for (Network network : manager.getAllNetworks()) {
                if (isFast(manager.getNetworkCapabilities(network))) {
                    return true;
                }
            }
            return false;
        }
        for (NetworkInfo info : legacyNetworks(manager)) {
            if (isConnectedLegacy(info)
                    && isConnectionFast(info.getType(), info.getSubtype())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isConnectedFast(Context context, Network network) {
        requireContext(context);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            NetworkInfo info = manager(context).getActiveNetworkInfo();
            return isConnectedLegacy(info)
                    && isConnectionFast(info.getType(), info.getSubtype());
        }
        if (network == null) {
            return false;
        }
        return isFast(manager(context).getNetworkCapabilities(network));
    }

    public static boolean isAirplaneModeOn(Context context) {
        requireContext(context);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return Settings.System.getInt(
                    context.getContentResolver(),
                    Settings.System.AIRPLANE_MODE_ON,
                    0
            ) != 0;
        }
        return Settings.Global.getInt(
                context.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON,
                0
        ) != 0;
    }

    /** The original VPN method now safely returns false below API 21. */
    public static boolean vpnActive(Context context) {
        requireContext(context);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }
        ConnectivityManager manager = manager(context);
        for (Network network : manager.getAllNetworks()) {
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            if (capabilities != null
                    && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                return true;
            }
        }
        return false;
    }

    /** Original blocking API. Call only from a worker thread. */
    public static boolean isInternetReachable(Context context) {
        return checkInternetBlocking(context, configuredHosts).isReachable();
    }

    /** Original blocking overload with custom bare hosts or HTTP(S) URLs. */
    public static boolean isInternetReachable(
            Context context,
            ArrayList<String> hosts
    ) {
        return checkInternetBlocking(context, normalizeHosts(hosts)).isReachable();
    }

    /**
     * Expanded non-blocking API for Activities. The callback runs on the main
     * thread and can be cancelled from onStop/onDestroy.
     */
    public static Request checkInternetAsync(
            Context context,
            InternetCallback callback
    ) {
        return checkInternetAsync(context, configuredHosts, callback);
    }

    public static Request checkInternetAsync(
            Context context,
            List<String> hosts,
            InternetCallback callback
    ) {
        return checkInternetAsync(
                context,
                DEFAULT_DNS_RESOLVERS,
                hosts,
                callback
        );
    }

    public static Request checkInternetAsync(
            Context context,
            List<String> dnsResolvers,
            List<String> hosts,
            InternetCallback callback
    ) {
        requireContext(context);
        if (callback == null) {
            throw new IllegalArgumentException("callback == null");
        }
        Context application = context.getApplicationContext();
        final Context appContext = application != null ? application : context;
        final List<String> normalizedResolvers =
                normalizeDnsResolvers(dnsResolvers);
        final List<String> normalized = normalizeHosts(hosts);
        final Request request = new Request();
        Future<?> future = EXECUTOR.submit(() -> {
            final InternetResult result =
                    checkInternetBlocking(
                            appContext,
                            normalizedResolvers,
                            normalized
                    );
            if (!request.isCancelled()) {
                MAIN_HANDLER.post(() -> {
                    if (!request.isCancelled()) {
                        callback.onResult(result);
                    }
                });
            }
        });
        request.attach(future);
        return request;
    }

    /** Expanded diagnostic API. Must be called from a worker thread. */
    public static InternetResult checkInternetBlocking(Context context) {
        return checkInternetBlocking(context, configuredHosts);
    }

    public static InternetResult checkInternetBlocking(
            Context context,
            List<String> hosts
    ) {
        return checkInternetBlocking(context, DEFAULT_DNS_RESOLVERS, hosts);
    }

    public static InternetResult checkInternetBlocking(
            Context context,
            List<String> dnsResolvers,
            List<String> hosts
    ) {
        requireContext(context);
        long started = SystemClock.elapsedRealtime();
        List<String> attempted = new ArrayList<>();
        ConnectivityManager manager = manager(context);
        Network network = selectProbeNetwork(manager);

        if (!isConnected(context)) {
            return new InternetResult(
                    false,
                    null,
                    attempted,
                    SystemClock.elapsedRealtime() - started
            );
        }

        // DNS is intentionally attempted first and sequentially. A valid DNS
        // response proves that a public resolver was reached, so no web
        // provider is contacted after the first DNS success.
        for (String resolver : normalizeDnsResolvers(dnsResolvers)) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            String endpoint = dnsEndpointLabel(resolver);
            attempted.add(endpoint);
            if (isDnsResolverAvailable(resolver, network)) {
                return new InternetResult(
                        true,
                        endpoint,
                        attempted,
                        SystemClock.elapsedRealtime() - started
                );
            }
        }

        // Web providers are only a fallback for networks that block access to
        // external DNS resolvers. Stop immediately after the first success.
        for (String host : normalizeHosts(hosts)) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            attempted.add(host);
            if (isHostAvailable(host, network)) {
                return new InternetResult(
                        true,
                        host,
                        attempted,
                        SystemClock.elapsedRealtime() - started
                );
            }
        }
        return new InternetResult(
                false,
                null,
                attempted,
                SystemClock.elapsedRealtime() - started
        );
    }

    public static List<String> defaultHosts() {
        return DEFAULT_HOSTS;
    }

    public static List<String> defaultDnsResolvers() {
        return DEFAULT_DNS_RESOLVERS;
    }

    private static ConnectivityManager manager(Context context) {
        requireContext(context);
        ConnectivityManager manager =
                (ConnectivityManager) context.getSystemService(
                        Context.CONNECTIVITY_SERVICE
                );
        if (manager == null) {
            throw new IllegalStateException("ConnectivityManager unavailable");
        }
        return manager;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static boolean isUsable(NetworkCapabilities capabilities) {
        if (capabilities == null
                || !capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
        )) {
            return false;
        }
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.P
                || capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED
        );
    }

    private static boolean hasTransport(Context context, int transport) {
        requireContext(context);
        ConnectivityManager manager = manager(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            for (Network network : manager.getAllNetworks()) {
                NetworkCapabilities capabilities =
                        manager.getNetworkCapabilities(network);
                if (isUsable(capabilities)
                        && capabilities.hasTransport(transport)) {
                    return true;
                }
            }
            return false;
        }
        for (NetworkInfo info : legacyNetworks(manager)) {
            if (isConnectedLegacy(info)
                    && legacyTypeMatches(info.getType(), transport)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTransport(
            Context context,
            Network network,
            int transport
    ) {
        requireContext(context);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return hasTransport(context, transport);
        }
        if (network == null) {
            return false;
        }
        NetworkCapabilities capabilities =
                manager(context).getNetworkCapabilities(network);
        return isUsable(capabilities) && capabilities.hasTransport(transport);
    }

    private static boolean legacyTypeMatches(int type, int transport) {
        if (transport == NetworkCapabilities.TRANSPORT_WIFI) {
            return type == ConnectivityManager.TYPE_WIFI;
        }
        if (transport == NetworkCapabilities.TRANSPORT_CELLULAR) {
            return type == ConnectivityManager.TYPE_MOBILE;
        }
        if (transport == NetworkCapabilities.TRANSPORT_ETHERNET) {
            return type == ConnectivityManager.TYPE_ETHERNET;
        }
        return false;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static boolean isFast(NetworkCapabilities capabilities) {
        return isUsable(capabilities)
                && capabilities.getLinkDownstreamBandwidthKbps() >= MINIMUM_FAST_KBPS
                && capabilities.getLinkUpstreamBandwidthKbps() >= MINIMUM_FAST_KBPS;
    }

    private static NetworkInfo[] legacyNetworks(ConnectivityManager manager) {
        NetworkInfo[] networks = manager.getAllNetworkInfo();
        return networks != null ? networks : new NetworkInfo[0];
    }

    private static boolean isConnectedLegacy(NetworkInfo info) {
        return info != null && info.isAvailable() && info.isConnected();
    }

    private static boolean isConnectionFast(int type, int subType) {
        if (type == ConnectivityManager.TYPE_WIFI
                || type == ConnectivityManager.TYPE_ETHERNET) {
            return true;
        }
        if (type != ConnectivityManager.TYPE_MOBILE) {
            return false;
        }
        switch (subType) {
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
            case TelephonyManager.NETWORK_TYPE_LTE:
                return true;
            default:
                return false;
        }
    }

    private static Network selectProbeNetwork(ConnectivityManager manager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network active = manager.getActiveNetwork();
            if (active != null
                    && isUsable(manager.getNetworkCapabilities(active))) {
                return active;
            }
        }
        for (Network network : manager.getAllNetworks()) {
            if (isUsable(manager.getNetworkCapabilities(network))) {
                return network;
            }
        }
        return null;
    }

    private static List<String> normalizeHosts(List<String> hosts) {
        if (hosts == null) {
            throw new IllegalArgumentException("hosts == null");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String raw : hosts) {
            if (raw == null) {
                continue;
            }
            String value = raw.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (!value.regionMatches(true, 0, "https://", 0, 8)
                    && !value.regionMatches(true, 0, "http://", 0, 7)) {
                value = "https://" + value + "/";
            }
            if (!isValidURL(value)) {
                throw new IllegalArgumentException(
                        "Invalid HTTP(S) URL: " + value
                );
            }
            normalized.add(value);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("hosts cannot be empty");
        }
        return Collections.unmodifiableList(new ArrayList<>(normalized));
    }

    private static List<String> normalizeDnsResolvers(List<String> resolvers) {
        if (resolvers == null) {
            throw new IllegalArgumentException("dnsResolvers == null");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String raw : resolvers) {
            if (raw == null) {
                continue;
            }
            String value = raw.trim();
            if (value.isEmpty()) {
                continue;
            }
            parseDnsResolver(value);
            normalized.add(value);
        }
        return Collections.unmodifiableList(new ArrayList<>(normalized));
    }

    private static boolean isValidURL(String address) {
        if (address == null) {
            throw new IllegalArgumentException("url == null");
        }
        try {
            URL url = new URL(address);
            url.toURI();
            return "http".equalsIgnoreCase(url.getProtocol())
                    || "https".equalsIgnoreCase(url.getProtocol());
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isHostAvailable(String address, Network network) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(address);
            URLConnection raw;
            if (network != null
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                raw = network.openConnection(url);
            } else {
                raw = url.openConnection();
            }

            connection = (HttpURLConnection) raw;
            if (connection instanceof HttpsURLConnection
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN
                    && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
                    && TLS_12_SOCKET_FACTORY != null) {
                ((HttpsURLConnection) connection)
                        .setSSLSocketFactory(TLS_12_SOCKET_FACTORY);
            }

            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("Connection", "close");
            connection.setRequestProperty(
                    "User-Agent",
                    "ConnectivityAndInternetAccess/2"
            );
            int response = connection.getResponseCode();
            return response >= 100 && response <= 599;
        } catch (IOException | RuntimeException ignored) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static boolean isDnsResolverAvailable(
            String resolver,
            Network network
    ) {
        DnsResolver endpoint = parseDnsResolver(resolver);
        DatagramSocket socket = null;
        try {
            int transactionId = DNS_TRANSACTION_ID.incrementAndGet() & 0xffff;
            byte[] query = createDnsQuery(transactionId);

            socket = new DatagramSocket();
            if (network != null
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                network.bindSocket(socket);
            }
            socket.setSoTimeout(DNS_TIMEOUT_MS);
            socket.connect(new InetSocketAddress(endpoint.host, endpoint.port));
            socket.send(new DatagramPacket(query, query.length));

            byte[] buffer = new byte[512];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            socket.receive(response);
            return isValidDnsResponse(
                    transactionId,
                    response.getData(),
                    response.getLength()
            );
        } catch (IOException | RuntimeException ignored) {
            return false;
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    private static byte[] createDnsQuery(int transactionId) {
        String[] labels = DNS_QUERY_NAME.split("\\.");
        int length = 12 + 1 + 4;
        for (String label : labels) {
            length += 1 + label.length();
        }
        byte[] query = new byte[length];
        query[0] = (byte) (transactionId >>> 8);
        query[1] = (byte) transactionId;
        query[2] = 0x01; // Recursion desired.
        query[5] = 0x01; // One question.

        int offset = 12;
        for (String label : labels) {
            query[offset++] = (byte) label.length();
            for (int index = 0; index < label.length(); index++) {
                query[offset++] = (byte) label.charAt(index);
            }
        }
        query[offset++] = 0x00;
        query[offset++] = 0x00;
        query[offset++] = 0x01; // QTYPE A.
        query[offset++] = 0x00;
        query[offset] = 0x01; // QCLASS IN.
        return query;
    }

    private static boolean isValidDnsResponse(
            int transactionId,
            byte[] response,
            int length
    ) {
        if (response == null || length < 12) {
            return false;
        }
        int responseId =
                ((response[0] & 0xff) << 8) | (response[1] & 0xff);
        int flags =
                ((response[2] & 0xff) << 8) | (response[3] & 0xff);
        int questionCount =
                ((response[4] & 0xff) << 8) | (response[5] & 0xff);
        int responseCode = flags & 0x000f;
        return responseId == transactionId
                && (flags & 0x8000) != 0
                && (flags & 0x7800) == 0
                && questionCount > 0
                && responseCode <= 5;
    }

    private static String dnsEndpointLabel(String resolver) {
        DnsResolver endpoint = parseDnsResolver(resolver);
        return "dns://" + endpoint.host + ":" + endpoint.port;
    }

    private static DnsResolver parseDnsResolver(String resolver) {
        String host = resolver;
        int port = DNS_PORT;
        int firstColon = resolver.indexOf(':');
        int lastColon = resolver.lastIndexOf(':');
        if (firstColon > 0 && firstColon == lastColon) {
            host = resolver.substring(0, firstColon).trim();
            String portValue = resolver.substring(firstColon + 1).trim();
            try {
                port = Integer.parseInt(portValue);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException(
                        "Invalid DNS resolver port: " + resolver,
                        error
                );
            }
        }
        if (host.isEmpty() || port < 1 || port > 65_535) {
            throw new IllegalArgumentException(
                    "Invalid DNS resolver: " + resolver
            );
        }
        return new DnsResolver(host, port);
    }

    private static final class DnsResolver {
        private final String host;
        private final int port;

        private DnsResolver(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    private static SSLSocketFactory createTls12Factory() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN
                || Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return null;
        }
        try {
            SSLContext context = SSLContext.getInstance("TLSv1.2");
            context.init(null, null, null);
            return new Tls12SocketFactory(context.getSocketFactory());
        } catch (GeneralSecurityException ignored) {
            return null;
        }
    }

    private static void requireContext(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context == null");
        }
    }

    private static final class Tls12SocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;

        private Tls12SocketFactory(SSLSocketFactory delegate) {
            this.delegate = delegate;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(
                Socket socket,
                String host,
                int port,
                boolean autoClose
        ) throws IOException {
            return enable(delegate.createSocket(socket, host, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return enable(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(
                String host,
                int port,
                InetAddress localHost,
                int localPort
        ) throws IOException {
            return enable(delegate.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return enable(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(
                InetAddress address,
                int port,
                InetAddress localAddress,
                int localPort
        ) throws IOException {
            return enable(
                    delegate.createSocket(address, port, localAddress, localPort)
            );
        }

        private Socket enable(Socket socket) {
            if (socket instanceof SSLSocket) {
                SSLSocket sslSocket = (SSLSocket) socket;
                if (Arrays.asList(sslSocket.getSupportedProtocols())
                        .contains("TLSv1.2")) {
                    sslSocket.setEnabledProtocols(new String[]{"TLSv1.2"});
                }
            }
            return socket;
        }
    }
}
