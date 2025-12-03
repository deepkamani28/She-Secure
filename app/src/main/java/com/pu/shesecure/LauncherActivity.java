package com.pu.shesecure;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

public class LauncherActivity extends AppCompatActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ActivityResultLauncher<String> locationPermission;
    private ActivityResultLauncher<String> notificationPermission;
    private OnboardingAdapter onboardingAdapter;
    private ViewPager2 view_pager2;

    private ConstraintLayout ongoing_layout;
    private TextView notice_text, app_name_text;
    private Button back_button, next_button;
    private boolean isFirstLaunch;
    private boolean alreadyNavigated;
    private boolean isPermissionOpen = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);

        isFirstLaunch = getSharedPreferences("appPrefs", MODE_PRIVATE).getBoolean("isFirstLaunch", true);
        locationPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), result -> checkPermissionsAndProceed());

        notificationPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), result -> {
            isPermissionOpen = false;
            locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        });

        if (isFirstLaunch) setupViewsAndListeners();
        else handler.postDelayed(this::requestNotificationPermission, 1000);
    }

    private void setupViewsAndListeners() {
        view_pager2 = findViewById(R.id.view_pager2);
        back_button = findViewById(R.id.back_button);
        next_button = findViewById(R.id.next_button);
        notice_text = findViewById(R.id.notice_text);
        app_name_text = findViewById(R.id.app_name_text);
        ongoing_layout = findViewById(R.id.ongoing_layout);

        notice_text.setVisibility(View.GONE);
        app_name_text.setVisibility(View.GONE);
        ongoing_layout.setVisibility(View.VISIBLE);
        onboardingAdapter = new OnboardingAdapter();
        view_pager2.setAdapter(onboardingAdapter);

        back_button.setOnClickListener(v -> view_pager2.setCurrentItem(view_pager2.getCurrentItem() - 1, true));

        next_button.setOnClickListener(v -> {
            int current = view_pager2.getCurrentItem();
            if (current == onboardingAdapter.getItemCount() - 1) {
                requestNotificationPermission();
                getSharedPreferences("appPrefs", MODE_PRIVATE).edit().putBoolean("isFirstLaunch", false).apply();
            } else view_pager2.setCurrentItem(current + 1, true);
        });

        view_pager2.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int pos) {
                back_button.setVisibility(pos == 0 ? View.GONE : View.VISIBLE);
                next_button.setText(pos == onboardingAdapter.getItemCount() - 1 ? "Start" : "Next");
            }
        });
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isPermissionOpen = true;
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    private void checkPermissionsAndProceed() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (isFirstLaunch && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                isFirstLaunch = false;
                isPermissionOpen = true;

                ongoing_layout.setVisibility(View.GONE);
                notice_text.setVisibility(View.VISIBLE);
                app_name_text.setVisibility(View.VISIBLE);
                notice_text.setText("Preparing your safe space...");

                Constants.showPermissionRequiredDialog(this, "Background location is required so we can keep you safe by tracking your location and providing emergency help even if the app is closed or running in the background. Please select 'Allow all the time' when prompted.", () -> locationPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION), () -> {
                    Toast.makeText(this, "Limited safety without background location", Toast.LENGTH_LONG).show();
                    goNextWithDelay();
                });
            } else goNextWithDelay();
        } else handleLocationDenied();
    }

    private void handleLocationDenied() {
        notice_text.setText("SheSecure won't work without Location Permission. If you are in danger, the app cannot send SOS messages with your live location.");

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            Constants.showPermissionRequiredDialog(this, "SheSecure requires Location Permission for your safety. " + "When you feel unsafe and trigger the SOS (by pressing the button or shouting your set keyword), " + "the app needs your live location to immediately alert your selected contacts.", () -> locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION), () -> notice_text.setVisibility(View.VISIBLE));
        } else {
            Constants.showPermissionRequiredDialog(this, "Location Permission is essential for SheSecure to work. Without it, we cannot fetch your location during an emergency. " + "Please enable Location Permission from App Settings.", () -> {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.fromParts("package", getPackageName(), null));
                startActivity(intent);
            }, () -> notice_text.setVisibility(View.VISIBLE));
        }
    }

    private void goNextWithDelay() {
        if (alreadyNavigated) return;

        alreadyNavigated = true;
        handler.postDelayed(() -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                alreadyNavigated = false;
                handleLocationDenied();
            } else {
                startActivity(new Intent(this, HomeActivity.class));
                finish();
            }
        }, 1500);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!alreadyNavigated && !isPermissionOpen) checkPermissionsAndProceed();
    }
}