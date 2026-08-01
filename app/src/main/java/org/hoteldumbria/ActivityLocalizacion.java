package org.hoteldumbria;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.Locale;

public final class ActivityLocalizacion extends AppCompatActivity
        implements OnMapReadyCallback {

    private static final String MAP_FRAGMENT_TAG = "dumbria-map";
    private static final String STATE_INTERNET_VERIFIED =
            "location.internetVerified";
    private static final double DUMBRIA_LATITUDE = 43.0094171900;
    private static final double DUMBRIA_LONGITUDE = -9.1152205111;
    private static final float MAP_ZOOM = 14.5f;

    private ConnectivityAndInternetAccess.Request internetAccessRequest;
    private View connectionPanel;
    private ProgressBar connectionProgress;
    private TextView connectionStatus;
    private Button retryButton;
    private Button navigationButton;
    private WebView fallbackMap;
    private boolean internetVerified;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_localizacion);

        connectionPanel = findViewById(R.id.location_connection_panel);
        connectionProgress = findViewById(R.id.location_connection_progress);
        connectionStatus = findViewById(R.id.location_connection_status);
        retryButton = findViewById(R.id.location_retry_button);
        navigationButton = findViewById(R.id.location_navigation_button);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        retryButton.setOnClickListener(view -> verifyInternetAndLoadMap());
        navigationButton.setOnClickListener(view -> openDefaultNavigation());

        if (savedInstanceState != null) {
            internetVerified = savedInstanceState.getBoolean(
                    STATE_INTERNET_VERIFIED,
                    false
            );
        }

        SupportMapFragment restoredMap =
                (SupportMapFragment) getSupportFragmentManager()
                        .findFragmentByTag(MAP_FRAGMENT_TAG);
        if (internetVerified) {
            showLoadingMapState();
            if (restoredMap != null) {
                restoredMap.getMapAsync(this);
            } else {
                loadMap();
            }
        } else {
            showCheckingInternetState();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!internetVerified && internetAccessRequest == null) {
            verifyInternetAndLoadMap();
        }
    }

    private void verifyInternetAndLoadMap() {
        cancelInternetCheck();
        internetVerified = false;
        showCheckingInternetState();

        internetAccessRequest = ConnectivityAndInternetAccess.checkInternetAsync(
                this,
                result -> {
                    internetAccessRequest = null;
                    if (isFinishing()) {
                        return;
                    }
                    if (!result.isReachable()) {
                        showNoInternetState();
                        return;
                    }
                    internetVerified = true;
                    showLoadingMapState();
                    loadMap();
                }
        );
    }

    private void loadMap() {
        if (!isGoogleMapsKeyConfigured()) {
            loadOpenStreetMap();
            return;
        }
        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager()
                        .findFragmentByTag(MAP_FRAGMENT_TAG);
        if (mapFragment == null) {
            mapFragment = SupportMapFragment.newInstance();
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(
                            R.id.location_map_container,
                            mapFragment,
                            MAP_FRAGMENT_TAG
                    )
                    .commit();
        }
        mapFragment.getMapAsync(this);
    }

    private boolean isGoogleMapsKeyConfigured() {
        String apiKey = getString(R.string.google_maps_key).trim();
        return !apiKey.isEmpty() && !"YOUR_KEY_HERE".equals(apiKey);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void loadOpenStreetMap() {
        FrameLayout container = findViewById(R.id.location_map_container);
        if (fallbackMap != null) {
            container.removeView(fallbackMap);
            fallbackMap.destroy();
        }

        fallbackMap = new WebView(this);
        fallbackMap.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        WebSettings settings = fallbackMap.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setDomStorageEnabled(false);
        settings.setGeolocationEnabled(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        fallbackMap.setWebViewClient(new WebViewClient() {
            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !isAllowedMapUri(Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (isAllowedMapUri(Uri.parse(url))) {
                    showMapReadyState();
                }
            }

            @Override
            @SuppressWarnings("deprecation")
            public void onReceivedError(
                    WebView view,
                    int errorCode,
                    String description,
                    String failingUrl
            ) {
                if (failingUrl != null
                        && failingUrl.startsWith(
                        "https://www.openstreetmap.org/export/embed.html"
                )) {
                    showMapLoadError();
                }
            }
        });

        container.addView(fallbackMap);
        fallbackMap.loadUrl(
                "https://www.openstreetmap.org/export/embed.html"
                        + "?bbox=-9.1400%2C42.9900%2C-9.0900%2C43.0300"
                        + "&layer=mapnik"
                        + "&marker=43.0094171900%2C-9.1152205111"
        );
    }

    private boolean isAllowedMapUri(Uri uri) {
        return uri != null
                && "https".equalsIgnoreCase(uri.getScheme())
                && "www.openstreetmap.org".equalsIgnoreCase(uri.getHost());
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        LatLng dumbria = new LatLng(DUMBRIA_LATITUDE, DUMBRIA_LONGITUDE);
        googleMap.clear();
        googleMap.addMarker(
                new MarkerOptions()
                        .position(dumbria)
                        .title(getString(R.string.location_marker_title))
        );
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(dumbria, MAP_ZOOM));
        googleMap.getUiSettings().setMapToolbarEnabled(true);
        googleMap.getUiSettings().setZoomControlsEnabled(true);

        showMapReadyState();
    }

    private void showMapReadyState() {
        connectionPanel.setVisibility(View.GONE);
        connectionProgress.setVisibility(View.GONE);
        connectionStatus.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        navigationButton.setEnabled(true);
    }

    private void showMapLoadError() {
        connectionPanel.setVisibility(View.VISIBLE);
        connectionProgress.setVisibility(View.GONE);
        connectionStatus.setVisibility(View.VISIBLE);
        connectionStatus.setText(R.string.location_map_error);
        retryButton.setVisibility(View.VISIBLE);
        navigationButton.setEnabled(true);
    }

    private void showCheckingInternetState() {
        connectionPanel.setVisibility(View.VISIBLE);
        connectionProgress.setVisibility(View.VISIBLE);
        connectionStatus.setVisibility(View.VISIBLE);
        connectionStatus.setText(R.string.location_checking_internet);
        retryButton.setVisibility(View.GONE);
        navigationButton.setEnabled(false);
    }

    private void showLoadingMapState() {
        connectionPanel.setVisibility(View.VISIBLE);
        connectionProgress.setVisibility(View.VISIBLE);
        connectionStatus.setVisibility(View.VISIBLE);
        connectionStatus.setText(R.string.location_loading_map);
        retryButton.setVisibility(View.GONE);
        navigationButton.setEnabled(false);
    }

    private void showNoInternetState() {
        connectionPanel.setVisibility(View.VISIBLE);
        connectionProgress.setVisibility(View.GONE);
        connectionStatus.setVisibility(View.VISIBLE);
        connectionStatus.setText(R.string.location_no_internet);
        retryButton.setVisibility(View.VISIBLE);
        navigationButton.setEnabled(false);
    }

    private void openDefaultNavigation() {
        if (!internetVerified) {
            return;
        }
        String coordinates = String.format(
                Locale.US,
                "%.10f,%.10f",
                DUMBRIA_LATITUDE,
                DUMBRIA_LONGITUDE
        );
        Uri location = Uri.parse(
                "geo:" + coordinates
                        + "?q=" + coordinates
                        + "(" + Uri.encode(getString(R.string.location_marker_title)) + ")"
        );
        Intent navigationIntent = new Intent(Intent.ACTION_VIEW, location);
        try {
            startActivity(navigationIntent);
            return;
        } catch (ActivityNotFoundException ignored) {
            // Fall back to universal HTTPS directions below.
        }

        Uri webDirections = Uri.parse(
                "https://www.google.com/maps/dir/?api=1&destination="
                        + Uri.encode(coordinates)
        );
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, webDirections);
        try {
            startActivity(browserIntent);
        } catch (ActivityNotFoundException ignored) {
            Toast.makeText(
                    this,
                    R.string.location_no_navigation_app,
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void cancelInternetCheck() {
        if (internetAccessRequest != null) {
            internetAccessRequest.cancel();
            internetAccessRequest = null;
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean(STATE_INTERNET_VERIFIED, internetVerified);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onStop() {
        cancelInternetCheck();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (fallbackMap != null) {
            fallbackMap.stopLoading();
            fallbackMap.setWebViewClient(null);
            fallbackMap.destroy();
            fallbackMap = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
