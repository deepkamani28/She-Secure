package com.pu.shesecure;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String API_KEY = "GOOGLE_MAP_API_KEY";
    private static final String PLACES_URL = "https://maps.googleapis.com/maps/api/place/nearbysearch/json";

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final List<Marker> placeMarkers = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private FusedLocationProviderClient fusedLocationClient;
    private MapView mapView;
    private GoogleMap googleMap;
    private LocationCallback locationCallback;
    private Toast currentToast;

    private double latitude, longitude;
    private boolean locationReady;
    private int pageCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationCallback = createLocationCallback();

        if (!isLocationEnabled()) {
            new AlertDialog.Builder(this).setTitle("Location Required").setMessage("Please enable location to find nearby places.").setPositiveButton("Enable", (dialog, which) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))).setNegativeButton("Cancel", (dialog, which) -> finish()).setCancelable(false).show();
        } else checkPermissionsAndInitLocation();
    }

    private boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return locationManager != null && (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }

    private void checkPermissionsAndInitLocation() {
        if (hasLocationPermission()) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 101);
        } else initLocation();
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED;
    }

    @NonNull
    private LocationCallback createLocationCallback() {
        return new LocationCallback() {
            boolean firstUpdate = true;

            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location location = result.getLastLocation();
                if (location == null) return;

                latitude = location.getLatitude();
                longitude = location.getLongitude();
                locationReady = true;

                if (firstUpdate && googleMap != null && !isFinishing() && !isDestroyed()) {
                    firstUpdate = false;
                    CameraPosition cameraPosition = new CameraPosition.Builder().target(new LatLng(latitude, longitude)).zoom(18f).build();
                    googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 2000, null);
                }
            }
        };
    }

    private void initLocation() {
        if (hasLocationPermission()) return;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        showToast("Fetching your location...");
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null && googleMap != null) {
                LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 18f));
            }
            startLocationUpdates();
        });
    }

    private void startLocationUpdates() {
        if (hasLocationPermission()) return;

        try {
            LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).setMinUpdateIntervalMillis(5000).build();
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
            if (googleMap != null) googleMap.setMyLocationEnabled(true);
        } catch (SecurityException ignored) {
            showToast("Location permission not granted");
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setCompassEnabled(true);
        googleMap.getUiSettings().setMapToolbarEnabled(true);
        googleMap.getUiSettings().setMyLocationButtonEnabled(true);

        findViewById(R.id.hospitalBtn).setOnClickListener(v -> searchNearby("hospital", "Looking for nearby Hospitals"));
        findViewById(R.id.policeBtn).setOnClickListener(v -> searchNearby("police", "Looking for nearby Police Stations"));
    }

    private void searchNearby(String placeType, String toastMessage) {
        if (!locationReady) {
            showToast("Waiting for location...");
            return;
        }

        clearPlaceMarkers();
        pageCount = 0;

        showToast(toastMessage);
        fetchNearbyPlaces(PLACES_URL + "?location=" + latitude + "," + longitude + "&radius=3500&type=" + placeType + "&key=" + API_KEY);
    }

    private void clearPlaceMarkers() {
        for (Marker marker : placeMarkers) marker.remove();
        placeMarkers.clear();
    }

    private void fetchNearbyPlaces(String url) {
        executorService.submit(() -> {
            try {
                String json = downloadUrl(url);
                if (json.isEmpty()) {
                    showToast("No response from server");
                    return;
                }

                JSONObject obj = new JSONObject(json);
                String status = obj.optString("status", "");

                if ("ZERO_RESULTS".equals(status)) {
                    showToast("No nearby places found");
                    return;
                }
                if (!"OK".equals(status) && !"INVALID_REQUEST".equals(status)) {
                    showToast("Error: " + status);
                    return;
                }

                List<MarkerOptions> markers = parseMarkers(obj.getJSONArray("results"));
                String nextToken = obj.optString("next_page_token", "");

                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
                    for (MarkerOptions mo : markers) {
                        Marker marker = googleMap.addMarker(mo);
                        if (marker != null) {
                            placeMarkers.add(marker);
                            boundsBuilder.include(marker.getPosition());
                        }
                    }

                    if (!markers.isEmpty() && pageCount == 0) {
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 25));
                    }

                    if (!nextToken.isEmpty() && ++pageCount <= 3) {
                        mainHandler.postDelayed(() -> fetchNearbyPlaces(PLACES_URL + "?pagetoken=" + nextToken + "&key=" + API_KEY), 2100);
                    }
                });

            } catch (Exception e) {
                showToast("Error loading nearby places");
            }
        });
    }

    @NonNull
    private List<MarkerOptions> parseMarkers(@NonNull JSONArray results) throws JSONException {
        List<MarkerOptions> list = new ArrayList<>();
        for (int i = 0; i < results.length(); i++) {
            JSONObject place = results.getJSONObject(i);
            JSONObject loc = place.getJSONObject("geometry").getJSONObject("location");

            String name = place.optString("name", "Unknown");
            String vicinity = place.optString("vicinity", "");
            if (name.length() > 40) name = name.substring(0, 40) + "...";

            list.add(new MarkerOptions().position(new LatLng(loc.getDouble("lat"), loc.getDouble("lng"))).title(name + " : " + vicinity).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
        }
        return list;
    }

    @NonNull
    private String downloadUrl(String strUrl) {
        StringBuilder sb = new StringBuilder();
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) new URL(strUrl).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                mainHandler.post(() -> showToast("Failed to fetch places: " + responseCode));
                return "";
            }

            try (InputStream in = connection.getInputStream(); BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }
        } catch (Exception e) {
            mainHandler.post(() -> showToast("Error loading nearby places"));
        } finally {
            if (connection != null) connection.disconnect();
        }

        return sb.toString();
    }

    private void showToast(String message) {
        mainHandler.post(() -> {
            if (currentToast != null) currentToast.cancel();
            currentToast = Toast.makeText(MapsActivity.this, message, Toast.LENGTH_LONG);
            currentToast.show();
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            boolean granted = true;
            for (int result : grantResults)
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }

            if (granted) initLocation();
            else finish();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onDestroy() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }

        mapView.onDestroy();
        executorService.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}