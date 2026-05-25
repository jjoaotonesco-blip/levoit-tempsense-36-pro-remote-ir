package pt.local.levoitir;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.hardware.ConsumerIrManager;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final String TAG = "LevoitIR";

    private static final int NEC_POWER = 0xA05F807F;
    private static final int NEC_SWING = 0xA05F40BF;
    private static final int NEC_TIMER = 0xA05FC03F;
    private static final int NEC_PLUS = 0xA05F20DF;
    private static final int NEC_SLEEP = 0xA05F10EF;
    private static final int NEC_MINUS = 0xA05F609F;
    private static final int NEC_MODE = 0xA05FA05F;
    private static final int NEC_SOUND = 0xA05FE01F;

    private ConsumerIrManager irManager;
    private Vibrator vibrator;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        irManager = (ConsumerIrManager) getSystemService(CONSUMER_IR_SERVICE);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        getWindow().setStatusBarColor(0xff101415);
        getWindow().setNavigationBarColor(0xff101415);

        WebView webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        webView.setBackgroundColor(0xff101415);
        webView.addJavascriptInterface(new IrBridge(), "LevoitIR");
        setContentView(webView);
        hideSystemUi();
        webView.loadUrl("file:///android_asset/remote.html");
    }

    private void hideSystemUi() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars());
            }
        }
    }

    private final class IrBridge {
        @JavascriptInterface
        public void send(String command) {
            runOnUiThread(() -> sendCommand(command));
        }
    }

    private void sendCommand(String command) {
        int code;
        String label;
        switch (command) {
            case "power":
                code = NEC_POWER;
                label = "Power";
                break;
            case "swing":
                code = NEC_SWING;
                label = "Oscilacao";
                break;
            case "timer":
                code = NEC_TIMER;
                label = "Timer";
                break;
            case "speed":
            case "plus":
                code = NEC_PLUS;
                label = "Fan +";
                break;
            case "minus":
                code = NEC_MINUS;
                label = "Fan -";
                break;
            case "sleep":
                code = NEC_SLEEP;
                label = "Noite";
                break;
            case "mode":
                code = NEC_MODE;
                label = "Modo";
                break;
            case "sound":
                code = NEC_SOUND;
                label = "Som";
                break;
            default:
                Log.w(TAG, "Unknown command from WebView: " + command);
                return;
        }
        sendNec(code, label);
        tick();
    }

    private void sendNec(int code, String label) {
        if (irManager == null || !irManager.hasIrEmitter()) {
            Toast.makeText(this, "Este telemovel nao tem emissor IR disponivel.", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "No Consumer IR emitter available");
            return;
        }
        try {
            int[] pattern = necPatternMsb(code);
            Log.d(TAG, "Sending MSB " + label
                    + " original=0x" + String.format("%08X", code)
                    + " bytes=" + formatBytes(code)
                    + " at 38000Hz, pattern pulses=" + pattern.length);
            irManager.transmit(38_000, pattern);
        } catch (RuntimeException e) {
            Log.e(TAG, "IR transmit failed for 0x" + String.format("%08X", code), e);
            Toast.makeText(this, "Falha ao enviar IR: " + e.getClass().getSimpleName(), Toast.LENGTH_SHORT).show();
        }
    }

    private int[] necPatternMsb(int code) {
        ArrayList<Integer> pattern = new ArrayList<>();
        pattern.add(9000);
        pattern.add(4500);
        for (int i = 31; i >= 0; i--) {
            pattern.add(560);
            pattern.add(((code >>> i) & 1) == 1 ? 1690 : 560);
        }
        pattern.add(560);
        pattern.add(40000);
        int[] result = new int[pattern.size()];
        for (int i = 0; i < pattern.size(); i++) {
            result[i] = pattern.get(i);
        }
        return result;
    }

    private String formatBytes(int code) {
        return String.format("%02X %02X %02X %02X",
                (code >>> 24) & 0xff,
                (code >>> 16) & 0xff,
                (code >>> 8) & 0xff,
                code & 0xff);
    }

    private void tick() {
        if (vibrator == null) return;
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(18, 70));
            } else {
                vibrator.vibrate(18);
            }
        } catch (SecurityException ignored) {
            vibrator = null;
        }
    }
}
