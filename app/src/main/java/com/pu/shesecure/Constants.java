package com.pu.shesecure;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;

public class Constants {

    private static AlertDialog currentDialog;

    public static void showPermissionRequiredDialog(Activity activity, String message, Runnable grantListener, Runnable cancelListener) {
        if (currentDialog != null && currentDialog.isShowing()) currentDialog.dismiss();

        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_permission_required, null);
        currentDialog = new AlertDialog.Builder(activity, R.style.TransparentDialog).setView(dialogView).create();
        currentDialog.setCancelable(false);

        ((TextView) dialogView.findViewById(R.id.message_text)).setText(message);

        dialogView.findViewById(R.id.permission_grant_button).setOnClickListener(v -> {
            currentDialog.dismiss();
            currentDialog = null;
            if (grantListener != null) grantListener.run();
        });

        dialogView.findViewById(R.id.permission_cancel_button).setOnClickListener(v -> {
            currentDialog.dismiss();
            currentDialog = null;
            if (cancelListener != null) cancelListener.run();
        });

        if (currentDialog.getWindow() != null) {
            currentDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            currentDialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            currentDialog.getWindow().setDimAmount(0.80f);
        }

        currentDialog.show();
    }

    public static void openAppSettings(@NonNull Context context) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", context.getPackageName(), null);
        intent.setData(uri);
        context.startActivity(intent);
    }
}