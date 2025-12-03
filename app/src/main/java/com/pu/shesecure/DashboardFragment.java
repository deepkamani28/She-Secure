package com.pu.shesecure;

import static android.content.Context.MODE_PRIVATE;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.card.MaterialCardView;

public class DashboardFragment extends Fragment {

    private ActivityResultLauncher<String> permissionLauncher;
    private MaterialCardView nearby_places_card, emergency_numbers_card, sos_keyword_card, stop_keyword_card, test_keywords_card;
    private TextView result_text;

    private final BroadcastReceiver speechReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            String result = intent.getStringExtra("speech_result");
            if (result_text != null) result_text.setText(result);
        }
    };

    @Override
    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        nearby_places_card = view.findViewById(R.id.nearby_places_card);
        emergency_numbers_card = view.findViewById(R.id.emergency_numbers_card);
        sos_keyword_card = view.findViewById(R.id.sos_keyword_card);
        stop_keyword_card = view.findViewById(R.id.stop_keyword_card);
        test_keywords_card = view.findViewById(R.id.test_keywords_card);

        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) showTestKeywordsDialog();
            else handlePermissionDenied();
        });

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        nearby_places_card.setOnClickListener(v -> startActivity(new Intent(requireActivity(), MapsActivity.class)));
        emergency_numbers_card.setOnClickListener(v -> showEmergencyDialog());

        sos_keyword_card.setOnClickListener(v -> showKeywordsDialog());
        stop_keyword_card.setOnClickListener(v -> Toast.makeText(requireContext(), "Coming soon...", Toast.LENGTH_SHORT).show());

        test_keywords_card.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                showTestKeywordsDialog();
            } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        });
    }

    private void showEmergencyDialog() {
        String[] numbers = {"100 - Police", "108 - Ambulance", "112 - All-in-one Emergency", "181 - Women Helpline", "1091 - Women in Distress", "1098 - Child Helpline"};

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_emergency_numbers, null);
        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.TransparentDialog).setView(dialogView).create();
        dialog.setCancelable(false);

        LinearLayout container = dialogView.findViewById(R.id.numbers_container);
        Button closeBtn = dialogView.findViewById(R.id.numbers_close_button);

        for (String item : numbers) {
            TextView textView = new TextView(requireContext());
            textView.setText(item);
            textView.setTextSize(18f);
            textView.setTextColor(Color.BLACK);
            textView.setPadding(24, 24, 24, 24);

            TypedValue outValue = new TypedValue();
            requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            textView.setBackgroundResource(outValue.resourceId);

            textView.setOnClickListener(v -> {
                String number = item.split(" - ")[0];
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number)));
            });
            container.addView(textView);
        }

        closeBtn.setOnClickListener(v -> dialog.dismiss());

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
            dialog.getWindow().setDimAmount(0.80f);
        }

        dialog.show();
    }

    private void showKeywordsDialog() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("appPrefs", MODE_PRIVATE);
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_keyword, null);
        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.TransparentDialog).setView(dialogView).create();
        dialog.setCancelable(false);

        EditText keyword_edittext = dialogView.findViewById(R.id.keywords_edittext);
        TextView keyword_title = dialogView.findViewById(R.id.keywords_title);
        Button change_button = dialogView.findViewById(R.id.keywords_change_button);
        Button cancel_button = dialogView.findViewById(R.id.keywords_cancel_button);

        keyword_title.setText("Edit SOS Keyword");
        keyword_edittext.setText(prefs.getString("sos_word", "help me"));

        keyword_edittext.requestFocus();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            dialog.getWindow().setDimAmount(0.80f);
        }

        change_button.setOnClickListener(v -> {
            String new_keyword = keyword_edittext.getText().toString().trim();

            if (new_keyword.isEmpty()) {
                Toast.makeText(getActivity(), "Please enter your keyword", Toast.LENGTH_SHORT).show();
                return;
            }

            prefs.edit().putString("sos_word", new_keyword).apply();
            Toast.makeText(requireContext(), "Saved", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        cancel_button.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showTestKeywordsDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_test_speech, null);
        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.TransparentDialog).setView(dialogView).create();
        dialog.setCancelable(false);

        result_text = dialogView.findViewById(R.id.result_text);
        ImageView mic_icon = dialogView.findViewById(R.id.mic_icon);
        Button cancel_button = dialogView.findViewById(R.id.test_close_button);
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(speechReceiver, new IntentFilter("TEST_SPEECH_RESULT"));

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setDimAmount(0.80f);
        }

        mic_icon.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), TestSpeechService.class);
            if (!TestSpeechService.isRunning) {
                requireContext().startService(intent);
                mic_icon.setColorFilter(Color.GREEN);
            } else {
                requireContext().stopService(intent);
                mic_icon.setColorFilter(Color.RED);
            }
        });

        cancel_button.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), TestSpeechService.class);
            requireContext().stopService(intent);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void handlePermissionDenied() {
        if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            Constants.showPermissionRequiredDialog(requireActivity(), "Microphone permission is required to use voice features.\n\nSince you denied permissions, you now have to allow them from Settings.", () -> Constants.openAppSettings(requireContext()), null);
        } else {
            Constants.showPermissionRequiredDialog(requireActivity(), "Microphone permission is required to use voice features.", () -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO), null);
        }
    }
}