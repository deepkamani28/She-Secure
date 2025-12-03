package com.pu.shesecure;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.BounceInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

public class ContactsFragment extends Fragment {

    private ActivityResultLauncher<String[]> permissionLauncher;
    private ContactsAdapter contactsAdapter;
    private DatabaseHelper dbHelper;

    private TextView notice_text;
    private Button add_button;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_contacts, container, false);

        dbHelper = new DatabaseHelper(requireContext());
        contactsAdapter = new ContactsAdapter(dbHelper.getAllContactsData(), this::showAddContactDialog);

        notice_text = view.findViewById(R.id.notice_text);
        add_button = view.findViewById(R.id.contacts_add_button);

        RecyclerView recycler_view = view.findViewById(R.id.contacts_recycler_view);
        recycler_view.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler_view.setAdapter(contactsAdapter);

        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            boolean smsGranted = Boolean.TRUE.equals(result.get(Manifest.permission.SEND_SMS));
            boolean callGranted = Boolean.TRUE.equals(result.get(Manifest.permission.CALL_PHONE));

            if (smsGranted && callGranted) showAddContactDialog(null);
            else handlePermissionDenied(smsGranted, callGranted);
        });

        add_button.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                showAddContactDialog(null);
            } else requestPermissions();
        });

        loadContacts();
        return view;
    }

    private void showAddContactDialog(ContactsRecord contact) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_contact, null);
        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.TransparentDialog).setView(dialogView).create();

        EditText name_edittext = dialogView.findViewById(R.id.emergency_name_edittext);
        EditText number_edittext = dialogView.findViewById(R.id.emergency_number_edittext);
        TextInputLayout emergency_name = dialogView.findViewById(R.id.emergency_name);
        TextInputLayout emergency_number = dialogView.findViewById(R.id.emergency_number);
        CheckBox primary_checkbox = dialogView.findViewById(R.id.emergency_check_box);
        ImageView delete_icon = dialogView.findViewById(R.id.emergency_delete_icon);
        Button add_btn = dialogView.findViewById(R.id.emergency_add_button);

        name_edittext.requestFocus();
        int strokeColor = emergency_name.getBoxStrokeColor();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            dialog.getWindow().setDimAmount(0.80f);
        }

        if (contact != null) {
            name_edittext.setText(contact.name());
            number_edittext.setText(contact.number());
            primary_checkbox.setChecked(contact.isFavorite());
            delete_icon.setVisibility(View.VISIBLE);
            add_btn.setText("Update");
        } else {
            delete_icon.setVisibility(View.GONE);
            primary_checkbox.setChecked(dbHelper.getAllContactsData().isEmpty());
        }

        name_edittext.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                emergency_name.setError(null);
                emergency_name.setBoxStrokeColor(strokeColor);
            }
        });

        number_edittext.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                emergency_number.setError(null);
                emergency_number.setBoxStrokeColor(strokeColor);
            }
        });

        delete_icon.setOnClickListener(v -> {
            if (contact != null && dbHelper.deleteContact(contact.id())) {
                loadContacts();
                dialog.dismiss();
            }
        });

        add_btn.setOnClickListener(v -> {
            String name = name_edittext.getText().toString().trim();
            String number = number_edittext.getText().toString().trim();

            if (name.isEmpty()) {
                emergency_name.setBoxStrokeColor(Color.RED);
                return;
            }

            if (number.isEmpty()) {
                emergency_number.setBoxStrokeColor(Color.RED);
                return;
            }

            boolean success = (contact == null) ? dbHelper.insertContact(name, number, primary_checkbox.isChecked()) : dbHelper.updateContact(contact.id(), name, number, primary_checkbox.isChecked());
            if (success) loadContacts();
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.emergency_cancel_button).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void handlePermissionDenied(boolean smsGranted, boolean callGranted) {
        boolean shouldShowRationaleSms = shouldShowRequestPermissionRationale(Manifest.permission.SEND_SMS);
        boolean shouldShowRationaleCall = shouldShowRequestPermissionRationale(Manifest.permission.CALL_PHONE);

        if (shouldShowRationaleSms || shouldShowRationaleCall) {
            Constants.showPermissionRequiredDialog(requireActivity(), buildPermissionMessage(smsGranted, callGranted) + "\n\nWe do not collect or share your data.", this::requestPermissions, null);
        } else {
            Constants.showPermissionRequiredDialog(requireActivity(), buildPermissionMessage(smsGranted, callGranted) + "\n\nSince you denied permissions permanently, you now have to allow them from Settings.", () -> Constants.openAppSettings(requireContext()), null);
        }
    }

    @NonNull
    private String buildPermissionMessage(boolean smsGranted, boolean callGranted) {
        StringBuilder message = new StringBuilder();
        if (!smsGranted) message.append("• SMS Permission: Required to send emergency alerts\n\n");
        if (!callGranted) message.append("• Call Permission: Required to call your emergency contact\n\n");

        message.append("Without these permissions, the app cannot function because all safety features rely on SMS and Calls.");
        return message.toString();
    }

    private void requestPermissions() {
        permissionLauncher.launch(new String[]{Manifest.permission.SEND_SMS, Manifest.permission.CALL_PHONE});
    }

    private void loadContacts() {
        List<ContactsRecord> contacts = dbHelper.getAllContactsData();
        add_button.setVisibility(contacts.size() >= 5 ? View.GONE : View.VISIBLE);
        notice_text.setVisibility(contacts.isEmpty() ? View.VISIBLE : View.GONE);
        contactsAdapter.updateData(contacts);
    }

    public void highlightAddButton() {
        ScaleAnimation bounce = new ScaleAnimation(0.9f, 1.1f, 0.9f, 1.1f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);

        bounce.setDuration(500);
        bounce.setInterpolator(new BounceInterpolator());
        bounce.setRepeatMode(Animation.REVERSE);
        bounce.setRepeatCount(10);

        add_button.startAnimation(bounce);
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void afterTextChanged(Editable s) {}
    }
}