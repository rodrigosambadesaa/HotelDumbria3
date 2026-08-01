package org.hoteldumbria;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.security.NetworkSecurityPolicy;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        assertEquals("org.hoteldumbria", appContext.getPackageName());
    }

    @Test
    public void manifestDoesNotRequestDeviceLocation() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PackageInfo info = context.getPackageManager().getPackageInfo(
                context.getPackageName(),
                PackageManager.GET_PERMISSIONS
        );
        List<String> permissions = info.requestedPermissions == null
                ? java.util.Collections.emptyList()
                : Arrays.asList(info.requestedPermissions);
        assertFalse(permissions.contains(android.Manifest.permission.ACCESS_FINE_LOCATION));
        assertFalse(permissions.contains(android.Manifest.permission.ACCESS_COARSE_LOCATION));
    }

    @Test
    public void debugCleartextIsRestrictedToTheEmulatorBackend() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        NetworkSecurityPolicy policy = NetworkSecurityPolicy.getInstance();
        assertFalse(policy.isCleartextTrafficPermitted());
        assertTrue(policy.isCleartextTrafficPermitted("10.0.2.2"));
        assertFalse(policy.isCleartextTrafficPermitted("example.com"));
    }

    @Test
    public void rememberedRefreshTokenIsNeverStoredAsPlaintext() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String secret = "refresh-token-that-must-not-appear-in-preferences";
        SessionManager.startSession(context, "access", secret, true);
        String stored = context.getSharedPreferences("hotel_secure_session", Context.MODE_PRIVATE)
                .getString("refresh", "");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            assertTrue(stored.isEmpty());
        } else {
            assertFalse(stored.isEmpty());
            assertFalse(stored.contains(secret));
        }
        assertEquals(secret, SessionManager.getRefreshToken(context));
        SessionManager.clear(context);
    }

    @Test
    public void loginAndLocationActivitiesSurviveRecreation() {
        ActivityLoginRegistro login = launch(ActivityLoginRegistro.class);
        login = recreate(login, ActivityLoginRegistro.class);
        assertNotNull(login.findViewById(R.id.chk_guardar_login));
        finish(login);

        ActivityLocalizacion location = launch(ActivityLocalizacion.class);
        location = recreate(location, ActivityLocalizacion.class);
        assertNotNull(location.findViewById(R.id.location_navigation_button));
        finish(location);
    }

    @Test
    public void profileImageFromNonPersistableContentProviderCanBePreviewed() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        UploadImagenActivity activity = launch(UploadImagenActivity.class);
        try {
            instrumentation.runOnMainSync(() -> {
                Intent result = new Intent();
                result.setData(android.net.Uri.parse(
                        "content://org.hoteldumbria.test.images/avatar"));
                result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.onActivityResult(20, android.app.Activity.RESULT_OK, result);
                assertTrue(activity.findViewById(R.id.btn_UploadImagen).isEnabled());
                assertNotNull(((android.widget.ImageView) activity.findViewById(
                        R.id.imgv_imagen)).getDrawable());
            });
        } finally {
            finish(activity);
        }
    }

    private static <T extends Activity> T launch(Class<T> activityClass) {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(instrumentation.getTargetContext(), activityClass)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return activityClass.cast(instrumentation.startActivitySync(intent));
    }

    private static <T extends Activity> T recreate(T current, Class<T> activityClass) {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                activityClass.getName(), null, false);
        instrumentation.runOnMainSync(current::recreate);
        Activity replacement = instrumentation.waitForMonitorWithTimeout(monitor, 10_000);
        instrumentation.removeMonitor(monitor);
        assertNotNull("The activity was not recreated", replacement);
        instrumentation.waitForIdleSync();
        return activityClass.cast(replacement);
    }

    private static void finish(Activity activity) {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.runOnMainSync(activity::finish);
        instrumentation.waitForIdleSync();
    }

    @Test
    public void internetProbeRunsAsynchronouslyAndTriesDnsBeforeDomains()
            throws Exception {
        Context appContext =
                InstrumentationRegistry.getInstrumentation().getTargetContext();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicBoolean callReturned = new AtomicBoolean(false);
        AtomicBoolean callbackRanAfterReturn = new AtomicBoolean(false);
        AtomicBoolean callbackRanOnMainThread = new AtomicBoolean(false);
        AtomicReference<ConnectivityAndInternetAccess.InternetResult> result =
                new AtomicReference<>();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            ConnectivityAndInternetAccess.checkInternetAsync(
                    appContext,
                    value -> {
                        result.set(value);
                        callbackRanAfterReturn.set(callReturned.get());
                        callbackRanOnMainThread.set(
                                android.os.Looper.myLooper()
                                        == android.os.Looper.getMainLooper()
                        );
                        completed.countDown();
                    }
            );
            callReturned.set(true);
        });

        assertTrue("The Internet probe timed out",
                completed.await(30, TimeUnit.SECONDS));
        assertTrue("The callback ran before the asynchronous call returned",
                callbackRanAfterReturn.get());
        assertTrue("The callback was not delivered on the main thread",
                callbackRanOnMainThread.get());

        ConnectivityAndInternetAccess.InternetResult probeResult = result.get();
        assertNotNull(probeResult);
        assertTrue("The emulator must have Internet access for this test",
                probeResult.isReachable());

        List<String> attempts = probeResult.getAttemptedHosts();
        assertFalse(attempts.isEmpty());
        boolean domainStageStarted = false;
        for (String endpoint : attempts) {
            if (endpoint.startsWith("dns://")) {
                assertFalse(
                        "A DNS resolver was contacted after a web domain",
                        domainStageStarted
                );
            } else {
                domainStageStarted = true;
                assertTrue(endpoint.startsWith("https://"));
            }
        }
        assertEquals(
                attempts.get(attempts.size() - 1),
                probeResult.getReachedHost()
        );
    }
}
