package com.pu.shesecure;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

public class SecureFragment extends Fragment {

    private ActivityResultLauncher<String> permissionLauncher;
    private ImageView powerButton;
    private TextView appNameText;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_secure, container, false);

        powerButton = view.findViewById(R.id.powerButton);
        appNameText = view.findViewById(R.id.appName);

        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) startOrStopService();
            else handlePermissionDenied();
        });

        powerButton.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startOrStopService();
            } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        });

        return view;
    }

    private void startOrStopService() {
        if (!hasContactsInDatabase()) {
            ((HomeActivity) requireActivity()).switchToContacts();
            return;
        }

        if (!isLocationEnabled()) {
            Constants.showPermissionRequiredDialog(requireActivity(), "Please enable location to get help when you are in emergency.", () -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)), null);
            return;
        }

        PowerManager powerManager = (PowerManager) requireContext().getSystemService(Context.POWER_SERVICE);
        if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(requireContext().getPackageName())) {
            Constants.showPermissionRequiredDialog(requireActivity(), "Battery optimization should be disabled to make sure SOS works even when your device is locked.\n\n⚠ If you skip, SOS may not work in the background.", () -> {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
                startActivity(intent);
            }, () -> {
                Toast.makeText(requireContext(), "SOS may not work in background!", Toast.LENGTH_LONG).show();
                toggleService();
            });
            return;
        }

        toggleService();
    }

    private void toggleService() {
        Intent serviceIntent = new Intent(requireActivity(), SpeechService.class);
        if (SpeechService.isRunning) {
            requireActivity().stopService(serviceIntent);
        } else requireActivity().startService(serviceIntent);
    }

    private boolean hasContactsInDatabase() {
        try (DatabaseHelper dbHelper = new DatabaseHelper(requireContext())) {
            return dbHelper.getContactsCount() > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) requireActivity().getSystemService(Context.LOCATION_SERVICE);
        return locationManager != null && (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }

    private void handlePermissionDenied() {
        boolean shouldShowRationale = shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO);

        if (!shouldShowRationale) {
            Constants.showPermissionRequiredDialog(requireActivity(), "Microphone permission is required to use voice features.\n\nSince you denied permissions, you now have to allow them from Settings.", () -> Constants.openAppSettings(requireContext()), null);
        } else {
            Constants.showPermissionRequiredDialog(requireActivity(), "Microphone permission is required to use voice features.", () -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO), null);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ContextCompat.registerReceiver(requireContext(), new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getBooleanExtra("isRunning", false)) {
                    SpeechService.shouldRestart = true;
                    powerButton.setImageResource(R.drawable.vector_power_on);
                    appNameText.setTextColor(ContextCompat.getColor(requireContext(), R.color.powerGreen));
                } else {
                    SpeechService.shouldRestart = false;
                    powerButton.setImageResource(R.drawable.vector_power_off);
                    appNameText.setTextColor(ContextCompat.getColor(requireContext(), R.color.powerRed));
                }
            }
        }, new IntentFilter("SERVICE_STATE_CHANGED"), ContextCompat.RECEIVER_NOT_EXPORTED);
    }
}