package com.pu.shesecure;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.sac.speech.GoogleVoiceTypingDisabledException;
import com.sac.speech.Speech;
import com.sac.speech.SpeechDelegate;
import com.sac.speech.SpeechRecognitionNotAvailable;

import java.util.List;

public class TestSpeechService extends Service implements SpeechDelegate, Speech.stopDueToDelay {

    public static boolean isRunning = false;
    private Handler handler;

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        handler = new Handler(getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            Speech.init(this);
        } catch (Exception e) {
            Toast.makeText(this, "Speech init failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            stopSelf();
            return START_NOT_STICKY;
        }

        startListeningWithRetry();
        return START_NOT_STICKY;
    }

    private void startListeningWithRetry() {
        try {
            Speech speech = Speech.getInstance();
            speech.setListener(this);
            speech.startListening(null, this);
        } catch (SpeechRecognitionNotAvailable e) {
            Toast.makeText(this, "Speech recognition not available!", Toast.LENGTH_LONG).show();
            stopSelf();
        } catch (GoogleVoiceTypingDisabledException e) {
            Toast.makeText(this, "Google voice typing disabled!", Toast.LENGTH_LONG).show();
            stopSelf();
        } catch (Exception e) {
            Toast.makeText(this, "Speech start failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            handler.postDelayed(this::startListeningWithRetry, 500);
        }
    }

    private void stopListening() {
        try {
            Speech.getInstance().stopListening();
        } catch (Exception ignored) {}
    }

    private void restartListening() {
        stopListening();
        handler.postDelayed(this::startListeningWithRetry, 400);
    }

    @Override
    public void onSpeechResult(String result) {
        if (!TextUtils.isEmpty(result)) {
            Intent broadcast = new Intent("TEST_SPEECH_RESULT");
            broadcast.putExtra("speech_result", result);
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
        }
        restartListening();
    }

    @Override
    public void onSpecifiedCommandPronounced(String event) {
        restartListening();
    }

    @Override
    public void onStartOfSpeech() {}

    @Override
    public void onSpeechRmsChanged(float value) {}

    @Override
    public void onSpeechPartialResults(List<String> results) {}

    @Override
    public IBinder onBind(Intent intent) {return null;}

    @Override
    public void onDestroy() {
        stopListening();
        handler.removeCallbacksAndMessages(null);
        isRunning = false;
        super.onDestroy();
    }
}