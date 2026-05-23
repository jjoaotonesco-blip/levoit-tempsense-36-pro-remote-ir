package pt.local.levoitir;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.ConsumerIrManager;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String TAG = "LevoitIR";
    private ConsumerIrManager irManager;
    private Vibrator vibrator;

    private static final int NEC_POWER = 0xA05F807F;
    private static final int NEC_SWING = 0xA05F40BF;
    private static final int NEC_TIMER = 0xA05FC03F;
    private static final int NEC_PLUS = 0xA05F20DF;
    private static final int NEC_SLEEP = 0xA05F10EF;
    private static final int NEC_MINUS = 0xA05F609F;
    private static final int NEC_MODE = 0xA05FA05F;
    private static final int NEC_TURBO = 0xA05FE01F;
    private static final String PREFS = "levoit_ir_state";
    private static final String KEY_SELECTED_MODE = "selected_mode";
    private static final String KEY_FAN_SPEED = "fan_speed";
    private static final String KEY_TIMER_HOURS = "timer_hours";
    private static final String KEY_POWER_ON = "power_on";
    private static final String KEY_MUTED = "muted";
    private static final String KEY_IR_VARIANT = "ir_variant";
    private static final int IR_NEC_BYTES = 0;
    private static final int IR_LEGACY_LSB = 1;
    private static final int IR_MSB = 2;
    private static final int MODE_NONE = -1;
    private static final int MODE_TURBO = 0;
    private static final int MODE_NATURAL = 1;
    private static final int MODE_AUTO = 2;
    private static final int MODE_SLEEP = 3;
    private int irVariant;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        irVariant = IR_MSB;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_IR_VARIANT, IR_MSB).apply();
        irManager = (ConsumerIrManager) getSystemService(CONSUMER_IR_SERVICE);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        getWindow().setStatusBarColor(Color.rgb(4, 14, 25));
        getWindow().setNavigationBarColor(Color.rgb(4, 14, 25));
        setContentView(new RemoteView(this));
        hideSystemUi();
    }

    private void hideSystemUi() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars());
            }
        }
    }

    private void sendNec(int code) {
        if (irManager == null || !irManager.hasIrEmitter()) {
            Toast.makeText(this, "Este telemovel nao tem emissor IR disponivel.", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "No Consumer IR emitter available");
            return;
        }
        try {
            int transmitCode = irVariant == IR_NEC_BYTES ? Integer.reverseBytes(code) : code;
            int[] pattern = necPattern(transmitCode, irVariant == IR_MSB);
            Log.d(TAG, "Sending " + irVariantName()
                    + " original=0x" + String.format("%08X", code)
                    + " sent=0x" + String.format("%08X", transmitCode)
                    + " bytes=" + formatBytes(code)
                    + " at 38000Hz, pattern pulses=" + pattern.length);
            irManager.transmit(38_000, pattern);
        } catch (RuntimeException e) {
            Log.e(TAG, "IR transmit failed for 0x" + String.format("%08X", code), e);
            Toast.makeText(this, "Falha ao enviar IR: " + e.getClass().getSimpleName(), Toast.LENGTH_SHORT).show();
        }
    }

    private int[] necPattern(int code, boolean msbFirst) {
        ArrayList<Integer> pattern = new ArrayList<>();
        pattern.add(9000);
        pattern.add(4500);
        for (int i = 0; i < 32; i++) {
            int bit = msbFirst ? 31 - i : i;
            pattern.add(560);
            pattern.add(((code >>> bit) & 1) == 1 ? 1690 : 560);
        }
        pattern.add(560);
        pattern.add(40000);
        int[] result = new int[pattern.size()];
        for (int i = 0; i < pattern.size(); i++) {
            result[i] = pattern.get(i);
        }
        return result;
    }

    private String irVariantName() {
        if (irVariant == IR_LEGACY_LSB) return "LEGACY";
        if (irVariant == IR_MSB) return "MSB";
        return "NEC";
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

    private final class RemoteView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<ButtonSpec> buttons = new ArrayList<>();
        private final Bitmap iconPower;
        private final Bitmap iconMode;
        private final Bitmap iconTimer;
        private final Bitmap iconSwing;
        private final Bitmap iconSleep;
        private final Bitmap iconSound;
        private int fanSpeed = 5;
        private int timerHours = 0;
        private boolean powerOn = true;
        private boolean muted = false;
        private String mode = "COOLING";
        private int selectedMode = MODE_NONE;
        private float dialCx;
        private float dialCy;
        private float dialRadius;
        private boolean draggingDial;

        RemoteView(Context context) {
            super(context);
            iconPower = BitmapFactory.decodeResource(getResources(), R.drawable.btn_power);
            iconMode = BitmapFactory.decodeResource(getResources(), R.drawable.btn_mode);
            iconTimer = BitmapFactory.decodeResource(getResources(), R.drawable.btn_timer);
            iconSwing = BitmapFactory.decodeResource(getResources(), R.drawable.btn_swing);
            iconSleep = BitmapFactory.decodeResource(getResources(), R.drawable.btn_sleep);
            iconSound = BitmapFactory.decodeResource(getResources(), R.drawable.btn_sound);
            SharedPreferences prefs = context.getSharedPreferences(PREFS, MODE_PRIVATE);
            selectedMode = prefs.getInt(KEY_SELECTED_MODE, MODE_NONE);
            fanSpeed = Math.max(1, Math.min(5, prefs.getInt(KEY_FAN_SPEED, 5)));
            timerHours = Math.max(0, Math.min(12, prefs.getInt(KEY_TIMER_HOURS, 0)));
            powerOn = prefs.getBoolean(KEY_POWER_ON, true);
            muted = prefs.getBoolean(KEY_MUTED, false);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            buttons.clear();
            float w = getWidth();
            float h = getHeight();
            drawBackground(canvas, w, h);
            drawHeader(canvas, w);
            drawDial(canvas, w, h);
            drawButtonRows(canvas, w, h);
            drawFooterStatus(canvas, w, h);
        }

        private void drawBackground(Canvas c, float w, float h) {
            p.setShader(null);
            p.setColor(Color.rgb(4, 14, 25));
            c.drawRect(0, 0, w, h, p);
            p.setColor(Color.argb(35, 25, 98, 160));
            c.drawCircle(w * .5f, h * .28f, w * .34f, p);
            p.setColor(Color.argb(22, 33, 120, 210));
            c.drawCircle(w * .12f, h * .66f, w * .24f, p);
        }

        private void drawHeader(Canvas c, float w) {
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setFakeBoldText(true);
            p.setTextSize(dp(38));
            p.setColor(Color.WHITE);
            c.drawText("levoit", w / 2f, dp(70), p);
            p.setFakeBoldText(false);
            p.setTextSize(dp(23));
            c.drawText("TempSense 36 Pro", w / 2f, dp(116), p);
        }

        private void drawFooterStatus(Canvas c, float w, float h) {
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setFakeBoldText(false);
            p.setTextSize(dp(16));
            p.setColor(Color.rgb(18, 210, 126));
            c.drawText("- Online  - IR " + irVariantName(), w / 2f, h - dp(58), p);
        }

        private void drawGear(Canvas c, float cx, float cy, float r) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(3));
            p.setColor(Color.WHITE);
            c.drawCircle(cx, cy, r, p);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                c.drawLine(cx + (float) Math.cos(a) * (r + dp(4)), cy + (float) Math.sin(a) * (r + dp(4)),
                        cx + (float) Math.cos(a) * (r + dp(9)), cy + (float) Math.sin(a) * (r + dp(9)), p);
            }
        }

        private void drawDial(Canvas c, float w, float h) {
            float cx = w / 2f;
            float cy = dp(280);
            float r = Math.min(w * .28f, dp(125));
            dialCx = cx;
            dialCy = cy;
            dialRadius = r;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(22));
            p.setColor(Color.argb(80, 48, 83, 122));
            c.drawCircle(cx, cy, r, p);
            p.setStrokeWidth(dp(7));
            p.setColor(Color.argb(70, 130, 175, 230));
            c.drawCircle(cx, cy, r + dp(20), p);

            RectF arc = new RectF(cx - r, cy - r, cx + r, cy + r);
            p.setStrokeWidth(dp(2));
            p.setStrokeCap(Paint.Cap.BUTT);
            p.setColor(Color.argb(115, 80, 125, 180));
            for (int i = 1; i < 5; i++) {
                double a = Math.toRadians(-225 + i * 54f);
                float x1 = cx + (float) Math.cos(a) * (r - dp(8));
                float y1 = cy + (float) Math.sin(a) * (r - dp(8));
                float x2 = cx + (float) Math.cos(a) * (r + dp(11));
                float y2 = cy + (float) Math.sin(a) * (r + dp(11));
                c.drawLine(x1, y1, x2, y2, p);
            }
            p.setStrokeWidth(dp(9));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(Color.rgb(51, 142, 255));
            c.drawArc(arc, -225, 270, false, p);

            p.setStrokeCap(Paint.Cap.BUTT);
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setFakeBoldText(false);
            p.setTextSize(dp(17));
            p.setColor(Color.rgb(51, 142, 255));
            c.drawText(mode, cx, cy - dp(65), p);
            p.setFakeBoldText(true);
            p.setTextSize(dp(78));
            p.setColor(Color.WHITE);
            c.drawText("IR", cx, cy + dp(28), p);
            p.setFakeBoldText(false);
            p.setTextSize(dp(19));
            p.setColor(Color.argb(210, 220, 226, 236));
            drawFanSpeedLabel(c, cx, cy + dp(70), Color.argb(210, 220, 226, 236));

        }

        private void drawButtonRows(Canvas c, float w, float h) {
            float margin = dp(16);
            float rowW = w - margin * 2;
            float rowH = dp(118);
            float top1 = dp(472);
            drawPowerStep(c, margin + rowW * .33f, top1 - dp(42), "-", this::powerDown);
            drawPowerStep(c, margin + rowW * .67f, top1 - dp(42), "+", this::powerUp);
            drawPanel(c, margin, top1, rowW, rowH);

            float[] xs = {margin + rowW * .125f, margin + rowW * .375f, margin + rowW * .625f, margin + rowW * .875f};
            drawControl(c, xs[0], top1 + dp(45), "Power", false, this::power);
            drawControl(c, xs[1], top1 + dp(45), "Mode", false, this::mode);
            drawTimer(c, xs[2], top1 + dp(45));
            drawControl(c, xs[3], top1 + dp(45), "Swing", false, this::swing);

            float top2 = top1 + rowH + dp(22);
            drawPanel(c, margin, top2, rowW, rowH);
            drawControl(c, margin + rowW * .35f, top2 + dp(45), "Sleep", false, this::sleep);
            drawControl(c, margin + rowW * .65f, top2 + dp(45), "Sound", false, this::sound);
        }

        private void drawPanel(Canvas c, float x, float y, float w, float h) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(178, 13, 30, 49));
            c.drawRoundRect(new RectF(x, y, x + w, y + h), dp(18), dp(18), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1);
            p.setColor(Color.argb(45, 140, 180, 225));
            c.drawRoundRect(new RectF(x, y, x + w, y + h), dp(18), dp(18), p);
        }

        private void drawControl(Canvas c, float cx, float cy, String label, boolean active, Runnable action) {
            drawCircleButton(c, cx, cy, active);
            int col = active ? Color.rgb(51, 142, 255) : Color.WHITE;
            switch (label) {
                case "Power": drawBitmapIcon(c, iconPower, cx, cy, dp(43), dp(50)); break;
                case "Mode": drawBitmapIcon(c, iconMode, cx, cy, dp(47), dp(47)); break;
                case "Swing": drawBitmapIcon(c, iconSwing, cx, cy, dp(54), dp(36)); break;
                case "Turbo": drawTurboIcon(c, cx, cy, col); break;
                case "Natural": drawLeafIcon(c, cx, cy, col); break;
                case "Sleep": drawBitmapIcon(c, iconSleep, cx, cy, dp(45), dp(47)); break;
                case "Auto": drawAutoIcon(c, cx, cy, col); break;
                case "Sound": drawBitmapIcon(c, iconSound, cx, cy, dp(50), dp(42)); break;
            }
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(dp(15));
            p.setColor(active ? Color.rgb(51, 142, 255) : Color.argb(220, 226, 232, 242));
            c.drawText("Sound".equals(label) ? "Som" : label, cx, cy + dp(56), p);
            addHit(cx - dp(44), cy - dp(44), cx + dp(44), cy + dp(72), action);
        }

        private void drawPowerStep(Canvas c, float cx, float cy, String label, Runnable action) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(185, 15, 42, 70));
            c.drawRoundRect(new RectF(cx - dp(54), cy - dp(28), cx + dp(54), cy + dp(28)), dp(28), dp(28), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1.5f));
            p.setColor(Color.rgb(51, 142, 255));
            c.drawRoundRect(new RectF(cx - dp(54), cy - dp(28), cx + dp(54), cy + dp(28)), dp(28), dp(28), p);
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setFakeBoldText(true);
            p.setTextSize(dp(32));
            p.setColor(Color.WHITE);
            c.drawText(label, cx, cy + dp(11), p);
            p.setFakeBoldText(false);
            addHit(cx - dp(62), cy - dp(36), cx + dp(62), cy + dp(36), action);
        }

        private void drawTimer(Canvas c, float cx, float cy) {
            drawCircleButton(c, cx, cy, timerHours > 0);
            drawBitmapIcon(c, iconTimer, cx, cy, dp(45), dp(45));
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(dp(15));
            p.setColor(timerHours > 0 ? Color.rgb(51, 142, 255) : Color.argb(220, 226, 232, 242));
            c.drawText(timerHours == 0 ? "Timer" : timerHours + "h", cx, cy + dp(56), p);
            if (timerHours > 0) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeWidth(dp(4));
                p.setColor(Color.rgb(51, 142, 255));
                RectF r = new RectF(cx - dp(33), cy - dp(33), cx + dp(33), cy + dp(33));
                c.drawArc(r, -90, timerHours * 30f, false, p);
                p.setStrokeCap(Paint.Cap.BUTT);
            }
            addHit(cx - dp(44), cy - dp(44), cx + dp(44), cy + dp(72), this::timer);
        }

        private void drawBitmapIcon(Canvas c, Bitmap bitmap, float cx, float cy, float maxW, float maxH) {
            if (bitmap == null) return;
            float scale = Math.min(maxW / bitmap.getWidth(), maxH / bitmap.getHeight());
            float w = bitmap.getWidth() * scale;
            float h = bitmap.getHeight() * scale;
            RectF dst = new RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
            p.setAlpha(255);
            p.setShader(null);
            p.setFilterBitmap(true);
            c.drawBitmap(bitmap, null, dst, p);
            p.setFilterBitmap(false);
        }

        private void drawFanSpeedLabel(Canvas c, float cx, float baseline, int color) {
            String label = "Fan Speed";
            float iconR = dp(8.5f);
            float gap = dp(9);
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.LEFT);
            p.setTextSize(dp(19));
            p.setFakeBoldText(false);
            p.setColor(color);
            float textWidth = p.measureText(label);
            float totalWidth = iconR * 2 + gap + textWidth;
            float start = cx - totalWidth / 2f;
            drawFanIcon(c, start + iconR, baseline - dp(8), iconR, color);
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.LEFT);
            p.setTextSize(dp(19));
            p.setColor(color);
            c.drawText(label, start + iconR * 2 + gap, baseline, p);
            p.setTextAlign(Paint.Align.CENTER);
        }

        private void drawCircleButton(Canvas c, float cx, float cy, boolean active) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(active ? Color.argb(85, 31, 137, 255) : Color.argb(150, 29, 47, 70));
            c.drawCircle(cx, cy, dp(32), p);
            if (active) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(1.5f));
                p.setColor(Color.rgb(51, 142, 255));
                c.drawCircle(cx, cy, dp(33), p);
            }
        }

        private void drawNav(Canvas c, float w, float h) {
            float y = h - dp(58);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1);
            p.setColor(Color.argb(80, 120, 155, 195));
            c.drawLine(0, y - dp(48), w, y - dp(48), p);
            String[] labels = {"Control", "Schedule", "Monitor", "Settings"};
            for (int i = 0; i < 4; i++) {
                float cx = w * (.125f + i * .25f);
                p.setStyle(Paint.Style.FILL);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(dp(13));
                p.setColor(i == 0 ? Color.rgb(51, 142, 255) : Color.argb(160, 226, 232, 242));
                c.drawText(labels[i], cx, y + dp(26), p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(2.4f));
                if (i == 0) drawHomeIcon(c, cx, y - dp(3), p.getColor());
                else if (i == 1) drawCalendarIcon(c, cx, y - dp(3), p.getColor());
                else if (i == 2) drawBarsIcon(c, cx, y - dp(3), p.getColor());
                else drawUserIcon(c, cx, y - dp(3), p.getColor());
            }
        }

        private void power() {
            sendNec(NEC_POWER);
            tick();
        }

        private void mode() {
            selectedMode = selectedMode < MODE_TURBO || selectedMode >= MODE_AUTO ? MODE_TURBO : selectedMode + 1;
            saveSelectedMode();
            sendNec(NEC_MODE);
            tick();
            invalidate();
        }

        private void swing() {
            sendNec(NEC_SWING);
            tick();
            Toast.makeText(MainActivity.this, "Direcao enviada", Toast.LENGTH_SHORT).show();
        }

        private void timer() {
            timerHours = (timerHours + 1) % 13;
            saveState();
            sendNec(NEC_TIMER);
            tick();
            invalidate();
        }

        private void powerUp() {
            sendNec(NEC_PLUS);
            tick();
        }

        private void powerDown() {
            sendNec(NEC_MINUS);
            tick();
        }

        private void turbo() {
            selectedMode = MODE_TURBO;
            saveSelectedMode();
            sendNec(NEC_TURBO);
            tick();
            invalidate();
        }

        private void natural() {
            selectCycledMode(MODE_NATURAL);
        }

        private void sleep() {
            sendNec(NEC_SLEEP);
            tick();
        }

        private void sound() {
            sendNec(NEC_TURBO);
            tick();
        }

        private void autoMode() {
            selectCycledMode(MODE_AUTO);
        }

        private void selectCycledMode(int targetMode) {
            int start = selectedMode >= MODE_TURBO && selectedMode <= MODE_AUTO ? selectedMode : MODE_NONE;
            int taps = targetMode - start;
            if (taps <= 0) taps += 3;
            selectedMode = targetMode;
            saveSelectedMode();
            for (int i = 0; i < taps; i++) {
                sendNec(NEC_MODE);
            }
            tick();
            invalidate();
        }

        private void saveSelectedMode() {
            saveState();
        }

        private void saveState() {
            getContext().getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_SELECTED_MODE, selectedMode)
                    .putInt(KEY_FAN_SPEED, fanSpeed)
                    .putInt(KEY_TIMER_HOURS, timerHours)
                    .putBoolean(KEY_POWER_ON, powerOn)
                    .putBoolean(KEY_MUTED, muted)
                    .apply();
        }

        private void toggleIrOrder() {
            Toast.makeText(MainActivity.this, "IR " + irVariantName(), Toast.LENGTH_SHORT).show();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                draggingDial = isInDial(event.getX(), event.getY());
                if (draggingDial) {
                    updateDialFromTouch(event.getX(), event.getY(), true);
                    return true;
                }
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                if (draggingDial) {
                    updateDialFromTouch(event.getX(), event.getY(), true);
                    return true;
                }
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                draggingDial = false;
                return true;
            }
            if (event.getAction() != MotionEvent.ACTION_UP) return true;
            float x = event.getX(), y = event.getY();
            if (draggingDial) {
                draggingDial = false;
                updateDialFromTouch(x, y, true);
                return true;
            }
            for (int i = buttons.size() - 1; i >= 0; i--) {
                ButtonSpec b = buttons.get(i);
                if (b.area.contains(x, y)) {
                    b.action.run();
                    return true;
                }
            }
            return true;
        }

        private boolean isInDial(float x, float y) {
            return false;
        }

        private void updateDialFromTouch(float x, float y, boolean transmit) {
            double raw = Math.toDegrees(Math.atan2(y - dialCy, x - dialCx));
            double progress = raw - (-225);
            while (progress < 0) progress += 360;
            while (progress > 360) progress -= 360;
            if (progress > 270) {
                progress = progress > 315 ? 0 : 270;
            }
            int target = Math.max(1, Math.min(5, (int) Math.ceil(progress / 54.0)));
            if (target == fanSpeed) return;
            int old = fanSpeed;
            fanSpeed = target;
            saveState();
            if (transmit) {
                int code = fanSpeed > old ? NEC_PLUS : NEC_MINUS;
                for (int i = 0; i < Math.abs(fanSpeed - old); i++) {
                    sendNec(code);
                }
                tick();
            }
            invalidate();
        }

        private void addHit(float l, float t, float r, float b, Runnable action) {
            buttons.add(new ButtonSpec(new RectF(l, t, r, b), action));
        }

        private float dp(float value) {
            return value * getResources().getDisplayMetrics().density;
        }

        private void drawPowerIcon(Canvas c, float cx, float cy, int color) {
            p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(4)); p.setStrokeCap(Paint.Cap.ROUND);
            c.drawArc(new RectF(cx - dp(16), cy - dp(16), cx + dp(16), cy + dp(16)), 130, 280, false, p);
            c.drawLine(cx, cy - dp(19), cx, cy - dp(2), p);
        }

        private void drawFanIcon(Canvas c, float cx, float cy, float r, int color) {
            p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2.5f)); p.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 0; i < 3; i++) {
                c.save();
                c.rotate(i * 120, cx, cy);
                c.drawOval(new RectF(cx - r * .25f, cy - r * 1.25f, cx + r * .75f, cy - r * .1f), p);
                c.restore();
            }
            c.drawCircle(cx, cy, r * .22f, p);
        }

        private void drawClockIcon(Canvas c, float cx, float cy, int color) {
            p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(3)); p.setStrokeCap(Paint.Cap.ROUND);
            c.drawCircle(cx, cy, dp(15), p);
            c.drawLine(cx, cy, cx, cy - dp(9), p);
            c.drawLine(cx, cy, cx + dp(8), cy, p);
        }

        private void drawSwingIcon(Canvas c, float cx, float cy, int color) {
            p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(3)); p.setStrokeCap(Paint.Cap.ROUND);
            c.drawArc(new RectF(cx - dp(20), cy - dp(12), cx + dp(20), cy + dp(20)), 25, 130, false, p);
            c.drawLine(cx - dp(18), cy + dp(4), cx - dp(23), cy - dp(5), p);
            c.drawLine(cx + dp(18), cy + dp(4), cx + dp(23), cy - dp(5), p);
        }

        private void drawTurboIcon(Canvas c, float cx, float cy, int color) {
            p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2.6f)); p.setStrokeCap(Paint.Cap.ROUND);
            c.drawCircle(cx, cy, dp(17), p);
            c.drawCircle(cx, cy, dp(5), p);
            for (int i = 0; i < 6; i++) {
                double a = Math.toRadians(i * 60);
                c.drawLine(cx + (float) Math.cos(a) * dp(10), cy + (float) Math.sin(a) * dp(10),
                        cx + (float) Math.cos(a) * dp(20), cy + (float) Math.sin(a) * dp(20), p);
            }
        }

        private void drawSoundIcon(Canvas c, float cx, float cy, int color, boolean off) {
            p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(3)); p.setStrokeCap(Paint.Cap.ROUND);
            c.drawLine(cx - dp(18), cy - dp(7), cx - dp(8), cy - dp(7), p);
            c.drawLine(cx - dp(8), cy - dp(7), cx + dp(3), cy - dp(17), p);
            c.drawLine(cx + dp(3), cy - dp(17), cx + dp(3), cy + dp(17), p);
            c.drawLine(cx + dp(3), cy + dp(17), cx - dp(8), cy + dp(7), p);
            c.drawLine(cx - dp(8), cy + dp(7), cx - dp(18), cy + dp(7), p);
            c.drawLine(cx - dp(18), cy - dp(7), cx - dp(18), cy + dp(7), p);
            if (off) {
                c.drawLine(cx + dp(14), cy - dp(13), cx + dp(31), cy + dp(13), p);
                c.drawLine(cx + dp(31), cy - dp(13), cx + dp(14), cy + dp(13), p);
            } else {
                c.drawArc(new RectF(cx + dp(8), cy - dp(14), cx + dp(30), cy + dp(14)), -45, 90, false, p);
            }
        }

        private void drawLeafIcon(Canvas c, float cx, float cy, int color) {
            p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(3)); p.setStrokeCap(Paint.Cap.ROUND);
            c.drawOval(new RectF(cx - dp(18), cy - dp(14), cx + dp(15), cy + dp(16)), p);
            c.drawLine(cx - dp(18), cy + dp(18), cx + dp(18), cy - dp(16), p);
        }

        private void drawMoonIcon(Canvas c, float cx, float cy, int color) {
            p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(3)); p.setStrokeCap(Paint.Cap.ROUND);
            c.drawArc(new RectF(cx - dp(14), cy - dp(19), cx + dp(18), cy + dp(18)), 100, 230, false, p);
            c.drawArc(new RectF(cx - dp(3), cy - dp(18), cx + dp(24), cy + dp(12)), 115, 160, false, p);
        }

        private void drawAutoIcon(Canvas c, float cx, float cy, int color) {
            p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(3));
            c.drawCircle(cx, cy, dp(17), p);
            p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true); p.setTextSize(dp(20));
            c.drawText("A", cx, cy + dp(7), p); p.setFakeBoldText(false);
        }

        private void drawHomeIcon(Canvas c, float cx, float cy, int color) {
            p.setColor(color);
            c.drawLine(cx - dp(16), cy, cx, cy - dp(15), p);
            c.drawLine(cx, cy - dp(15), cx + dp(16), cy, p);
            c.drawRect(cx - dp(11), cy, cx + dp(11), cy + dp(19), p);
        }

        private void drawCalendarIcon(Canvas c, float cx, float cy, int color) {
            p.setColor(color); c.drawRoundRect(new RectF(cx - dp(16), cy - dp(14), cx + dp(16), cy + dp(18)), dp(3), dp(3), p);
            c.drawLine(cx - dp(16), cy - dp(5), cx + dp(16), cy - dp(5), p);
        }

        private void drawBarsIcon(Canvas c, float cx, float cy, int color) {
            p.setColor(color);
            c.drawLine(cx - dp(15), cy + dp(16), cx + dp(15), cy + dp(16), p);
            c.drawLine(cx - dp(11), cy + dp(10), cx - dp(11), cy - dp(8), p);
            c.drawLine(cx, cy + dp(10), cx, cy - dp(18), p);
            c.drawLine(cx + dp(11), cy + dp(10), cx + dp(11), cy - dp(3), p);
        }

        private void drawUserIcon(Canvas c, float cx, float cy, int color) {
            p.setColor(color);
            c.drawCircle(cx, cy - dp(10), dp(8), p);
            c.drawArc(new RectF(cx - dp(18), cy, cx + dp(18), cy + dp(28)), 200, 140, false, p);
        }
    }

    private static final class ButtonSpec {
        final RectF area;
        final Runnable action;

        ButtonSpec(RectF area, Runnable action) {
            this.area = area;
            this.action = action;
        }
    }
}
