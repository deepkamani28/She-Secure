package com.pu.shesecure;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.telephony.SmsManager;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.sac.speech.GoogleVoiceTypingDisabledException;
import com.sac.speech.Speech;
import com.sac.speech.SpeechDelegate;
import com.sac.speech.SpeechRecognitionNotAvailable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class SpeechService extends Service implements SpeechDelegate, Speech.stopDueToDelay {

    private static final String baseMessage = "Please HELP me! I am in Danger, I need your help.";
    public static boolean shouldRestart = true;
    public static boolean isRunning;

    private final String CHANNEL_ID = "secure_channel";
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    private FusedLocationProviderClient fusedLocationClient;
    private Handler handler;
    private Runnable sendHelpRunnable;

    private long lastTriggerAt = 0L;
    private int retryCount = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;

        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("SheSecure is with YOU").setContentText("Listening if you need help...").setSmallIcon(R.drawable.vector_power_off).setPriority(NotificationCompat.PRIORITY_DEFAULT).setOngoing(true).build();
        startForeground(1, notification);

        notifyServiceState(true);
    }

    private void createNotificationChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "SheSecure Service", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("SOS monitoring and alerts");
            channel.setSound(null, null);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        muteBeepSoundOfRecorder();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone permission not granted!", Toast.LENGTH_SHORT).show();
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            Speech.init(this);
            Speech.getInstance().setListener(this);
        } catch (Exception e) {
            Toast.makeText(this, "Speech init failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        handler = new Handler(Looper.getMainLooper());
        restartListeningSafely();

        return Service.START_STICKY;
    }

    private void restartListeningSafely() {
        if (!isRunning || retryCount >= 5) return;
        if (handler == null) handler = new Handler(Looper.getMainLooper());
        retryCount++;

        try {
            Speech inst = Speech.getInstance();

            if (inst == null) {
                try {
                    Speech.init(this);
                } catch (Exception ignored) {}

                handler.postDelayed(this::restartListeningSafely, 1500);
                return;
            }

            if (inst.isListening()) {
                try {
                    inst.stopListening();
                } catch (Exception ignored) {}
            }

            try {
                inst.stopTextToSpeech();
            } catch (Exception ignored) {}

            inst.setListener(this);
            inst.startListening(null, this);
        } catch (SpeechRecognitionNotAvailable e) {
            Toast.makeText(this, "Speech recognition not available!", Toast.LENGTH_LONG).show();
        } catch (GoogleVoiceTypingDisabledException e) {
            Toast.makeText(this, "Google voice typing disabled!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Speech start failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            handler.postDelayed(this::restartListeningSafely, 1500);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {return null;}

    @Override
    public void onStartOfSpeech() {}

    @Override
    public void onSpeechRmsChanged(float value) {}

    @Override
    public void onSpeechPartialResults(List<String> results) {}

    @Override
    public void onSpeechResult(String result) {
        if (TextUtils.isEmpty(result)) return;
        String sosWord = getSharedPreferences("appPrefs", MODE_PRIVATE).getString("sos_word", "help me");
        String lowerResult = result.toLowerCase();

        if (!TextUtils.isEmpty(sosWord) && lowerResult.contains(sosWord.toLowerCase())) {
            long now = SystemClock.elapsedRealtime();
            if (now - lastTriggerAt < 10_000) return;
            lastTriggerAt = now;
            showServiceNotification("SheSecure activated");
            startSendingHelpMessages();
        }
    }

    @Override
    public void onSpecifiedCommandPronounced(String event) {
        muteBeepSoundOfRecorder();
        restartListeningSafely();
    }

    private void startSendingHelpMessages() {
        callFavoriteNumber();
        if (handler == null) handler = new Handler(Looper.getMainLooper());

        if (sendHelpRunnable == null) {
            sendHelpRunnable = new Runnable() {
                @Override
                public void run() {
                    sendHelpMessageWithLocation();
                    if (isRunning) handler.postDelayed(this, 10 * 60 * 1000);
                }
            };
            handler.post(sendHelpRunnable);
        }
    }

    private void callFavoriteNumber() {
        try (DatabaseHelper dbHelper = new DatabaseHelper(this)) {
            String favoriteNumber = dbHelper.getFavoriteNumber();
            if (!TextUtils.isEmpty(favoriteNumber) && ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                Intent call = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + favoriteNumber));
                call.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(call);
                } catch (ActivityNotFoundException | SecurityException ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private void sendHelpMessageWithLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            sendSOS(baseMessage + "\n\nLocation permission not granted");
            return;
        }

        final AtomicBoolean locationHandled = new AtomicBoolean(false);
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).setMaxUpdates(1).build();
        final LocationCallback callback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                try {
                    if (locationHandled.getAndSet(true)) return;
                    fusedLocationClient.removeLocationUpdates(this);
                    handler.removeCallbacksAndMessages(this);

                    Location loc = result.getLastLocation();
                    if (loc != null) {
                        String link = "\n\nMy location on " + DateFormat.format("dd-MM-yyyy HH:mm:ss", loc.getTime()) + ": https://maps.google.com/?q=" + loc.getLatitude() + "," + loc.getLongitude();
                        sendSOS(baseMessage + link);
                    } else fallbackToLastKnown(locationHandled);
                } catch (Exception ignored) {}
            }
        };

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper());

            handler.postDelayed(() -> {
                if (locationHandled.get()) return;
                locationHandled.set(true);

                try {
                    fusedLocationClient.removeLocationUpdates(callback);
                } catch (Exception ignored) {}

                fallbackToLastKnown(locationHandled);
            }, 10_000);
        } catch (SecurityException | IllegalArgumentException e) {
            fallbackToLastKnown(locationHandled);
        }
    }

    private void fallbackToLastKnown(AtomicBoolean locationHandled) {
        if (locationHandled != null && locationHandled.get()) return;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            sendSOS(baseMessage + "\n\nLocation permission not granted");
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (locationHandled != null && locationHandled.getAndSet(true)) return;

            if (location != null) {
                String locationLink = "\n\nLast Known Location (" + DateFormat.format("dd-MM-yyyy HH:mm:ss", location.getTime()) + "): https://maps.google.com/?q=" + location.getLatitude() + "," + location.getLongitude();
                sendSOS(baseMessage + locationLink);
            } else sendSOS(baseMessage + "\n\nLocation not available");
        }).addOnFailureListener(e -> {
            if (locationHandled != null && locationHandled.getAndSet(true)) return;
            sendSOS(baseMessage + "\n\nLocation not available");
        });
    }

    private void sendSOS(String message) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            showServiceNotification("SMS permission not granted");
            return;
        }

        backgroundExecutor.execute(() -> {
            try (DatabaseHelper dbHelper = new DatabaseHelper(this)) {
                List<String> allNumbers = dbHelper.getAllNumbers();

                if (allNumbers == null || allNumbers.isEmpty()) {
                    showServiceNotification("No emergency contacts found");
                    return;
                }

                SmsManager smsManager = SmsManager.getDefault();
                for (String number : allNumbers) {
                    try {
                        ArrayList<String> parts = smsManager.divideMessage(message);
                        smsManager.sendMultipartTextMessage(number, null, parts, null, null);
                    } catch (Exception ignored) {}
                }

                showServiceNotification("SOS messages sent");
            } catch (Exception e) {
                showServiceNotification("Failed to send SOS");
            }
        });
    }

    private void muteBeepSoundOfRecorder() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) return;

        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
        } catch (Exception ignored) {}
    }

    private void showServiceNotification(String message) {
        PendingIntent content = PendingIntent.getActivity(this, 0, new Intent(this, LauncherActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("SheSecure").setContentText(message).setSmallIcon(R.drawable.vector_power_off).setPriority(NotificationCompat.PRIORITY_HIGH).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(content).setOnlyAlertOnce(true).setOngoing(false).setAutoCancel(true);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(2, builder.build());
    }

    private void notifyServiceState(boolean running) {
        Intent intent = new Intent("SERVICE_STATE_CHANGED").setPackage(getPackageName());
        intent.putExtra("isRunning", running);
        sendBroadcast(intent);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (shouldRestart) {
            PendingIntent service = PendingIntent.getService(getApplicationContext(), 1000, new Intent(getApplicationContext(), SpeechService.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 1000, service);
            }
        }

        if (handler != null && sendHelpRunnable != null) {
            handler.removeCallbacks(sendHelpRunnable);
        }

        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        sendHelpRunnable = null;
        notifyServiceState(false);

        if (handler != null) handler.removeCallbacksAndMessages(null);

        try {
            Speech inst = null;
            try {
                inst = Speech.getInstance();
            } catch (Exception ignored) {}

            if (inst != null) {
                try {
                    if (inst.isListening()) inst.stopListening();
                } catch (Exception ignored) {}

                try {
                    inst.shutdown();
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}


        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            Intent intent = new Intent(getApplicationContext(), SpeechService.class);
            PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), 1000, intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pendingIntent != null) alarmManager.cancel(pendingIntent);
        }

        try {
            backgroundExecutor.shutdownNow();
        } catch (Exception ignored) {}

        stopForeground(true);
        super.onDestroy();
    }
}