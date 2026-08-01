package org.hoteldumbria;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
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
