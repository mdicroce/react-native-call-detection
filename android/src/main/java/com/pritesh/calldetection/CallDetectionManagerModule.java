package com.pritesh.calldetection;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.HashMap;
import java.util.Map;

public class CallDetectionManagerModule
        extends ReactContextBaseJavaModule
        implements Application.ActivityLifecycleCallbacks,
        CallDetectionPhoneStateListener.PhoneCallStateUpdate {

    private boolean wasAppInOffHook = false;
    private boolean wasAppInRinging = false;
    private ReactApplicationContext reactContext;
    private TelephonyManager telephonyManager;
    private CallDetectionPhoneStateListener callDetectionPhoneStateListener;
    private Activity activity = null;
    private boolean isListenerRegistered = false;
    private AudioManager audioManager;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable runnable;
    private int previousAudioMode = AudioManager.MODE_NORMAL;

    public CallDetectionManagerModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public void onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy();
        stopListener();
    }

    @Override
    public String getName() {
        return "CallDetectionManagerAndroid";
    }

    private void checkAudioMode() {
        if (audioManager == null) return;
        int currentMode = audioManager.getMode();
        
        // Si el modo cambió desde la última vez que chequeamos
        if (currentMode != previousAudioMode) {
            
            if (currentMode == AudioManager.MODE_IN_COMMUNICATION || currentMode == AudioManager.MODE_IN_CALL) {
                // DETECTADO: Llamada VoIP (Whatsapp, Zoom, Meet, etc.)
                // Puedes reutilizar "Offhook" o crear un nuevo estado "VoIP"
                sendEvent("PhoneCallStateUpdate", "Offhook", "VoIP Call"); 
                wasAppInOffHook = true; 
                
            } else if (currentMode == AudioManager.MODE_NORMAL) {
                // Se colgó la llamada VoIP
                if (wasAppInOffHook) {
                    sendEvent("PhoneCallStateUpdate", "Disconnected", "VoIP Call");
                    wasAppInOffHook = false;
                }
            }
            
            previousAudioMode = currentMode;
        }
    }

    @ReactMethod
    public void startListener() {
        // Prevent double registration
        if (isListenerRegistered) {
            Log.w("CallDetectionManager", "Listener already registered, ignoring duplicate call");
            return;
        }

        audioManager = (AudioManager) this.reactContext.getSystemService(Context.AUDIO_SERVICE);

        runnable = new Runnable() {
            @Override
            public void run() {
                checkAudioMode();
                
                handler.postDelayed(this, 1000);
            };
        };
        handler.post(runnable);

        if (activity == null) {
            activity = getCurrentActivity();
            if (activity != null) {
                Application application = activity.getApplication();
                if (application != null) {
                    application.registerActivityLifecycleCallbacks(this);
                } else {
                    Log.e("CallDetectionManager", "Unable to start listener: Application is null");
                    return;
                }
            } else {
                Log.e("CallDetectionManager", "Unable to start listener: Activity is null");
                return;
            }
        }

        telephonyManager = (TelephonyManager) this.reactContext.getSystemService(
                Context.TELEPHONY_SERVICE);

        if (telephonyManager == null) {
            Log.e("CallDetectionManager", "Unable to start listener: TelephonyManager is null");
            return;
        }

        callDetectionPhoneStateListener = new CallDetectionPhoneStateListener(this);
        // Note: PhoneStateListener is deprecated in API 31+, consider migrating to TelephonyCallback
        telephonyManager.listen(callDetectionPhoneStateListener,
                PhoneStateListener.LISTEN_CALL_STATE);

        isListenerRegistered = true;
    }



    @ReactMethod
    public void stopListener() {
        if (telephonyManager != null && callDetectionPhoneStateListener != null) {
            telephonyManager.listen(callDetectionPhoneStateListener, PhoneStateListener.LISTEN_NONE);
            telephonyManager = null;
            callDetectionPhoneStateListener = null;
        }

        // Unregister activity lifecycle callbacks to prevent memory leak
        if (activity != null) {
            Application application = activity.getApplication();
            if (application != null) {
                application.unregisterActivityLifecycleCallbacks(this);
            }
            activity = null;
        }

        isListenerRegistered = false;

        // Reset state
        wasAppInOffHook = false;
        wasAppInRinging = false;

        if (handler != null && runnable != null) {
            handler.removeCallbacks(runnable);
        }
    }

    /**
     * @return a map of constants this module exports to JS. Supports JSON types.
     */
    public
    Map<String, Object> getConstants() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("Incoming", "Incoming");
        map.put("Offhook", "Offhook");
        map.put("Disconnected", "Disconnected");
        map.put("Missed", "Missed");
        return map;
    }

    // Activity Lifecycle Methods
    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceType) {

    }

    @Override
    public void onActivityStarted(Activity activity) {

    }

    @Override
    public void onActivityResumed(Activity activity) {

    }

    @Override
    public void onActivityPaused(Activity activity) {

    }

    @Override
    public void onActivityStopped(Activity activity) {

    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

    }

    @Override
    public void onActivityDestroyed(Activity activity) {

    }

    private void sendEvent(String eventName, String state, @Nullable String phoneNumber) {
        // Check if React context is still active to prevent crashes
        if (this.reactContext == null || !this.reactContext.hasActiveCatalystInstance()) {
            Log.w("CallDetectionManager", "React context not available, skipping event: " + state);
            return;
        }

        try {
            WritableMap params = Arguments.createMap();
            params.putString("state", state);
            if (phoneNumber != null) {
                params.putString("phoneNumber", phoneNumber);
            }

            this.reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit(eventName, params);
        } catch (Exception e) {
            Log.e("CallDetectionManager", "Error sending event: " + e.getMessage());
        }
    }

    @Override
    public void phoneCallStateUpdated(int state, String phoneNumber) {
        switch (state) {
            //Hangup
            case TelephonyManager.CALL_STATE_IDLE:
                if(wasAppInOffHook == true) { // if there was an ongoing call and the call state switches to idle, the call must have gotten disconnected
                    sendEvent("PhoneCallStateUpdate", "Disconnected", phoneNumber);
                } else if(wasAppInRinging == true) { // if the phone was ringing but there was no actual ongoing call, it must have gotten missed
                    sendEvent("PhoneCallStateUpdate", "Missed", phoneNumber);
                }

                //reset device state
                wasAppInRinging = false;
                wasAppInOffHook = false;
                break;
            //Outgoing
            case TelephonyManager.CALL_STATE_OFFHOOK:
                //Device call state: Off-hook. At least one call exists that is dialing, active, or on hold, and no calls are ringing or waiting.
                wasAppInOffHook = true;
                sendEvent("PhoneCallStateUpdate", "Offhook", phoneNumber);
                break;
            //Incoming
            case TelephonyManager.CALL_STATE_RINGING:
                // Device call state: Ringing. A new call arrived and is ringing or waiting. In the latter case, another call is already active.
                wasAppInRinging = true;
                sendEvent("PhoneCallStateUpdate", "Incoming", phoneNumber);
                break;
        }
    }
}
