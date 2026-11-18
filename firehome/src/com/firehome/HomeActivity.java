package com.firehome;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** A clean, app-first home screen. HomeWatcher brings it up whenever Amazon's home is started. */
public class HomeActivity extends Activity {
    static final String AMAZON_HOME = "com.amazon.tv.launcher";
    private static final String APPSTORE = "com.amazon.venezia";
    // "Fire TV home" starts Amazon's home the way a Home press does, so the watcher stands down this long
    private static final long AMAZON_PAUSE_MS = 10 * 60 * 1000L;
    private static final long STATUS_REFRESH_MS = 5000L;
    // Amazon's settings screen hands straight over to its home with a HOME start; let that one through
    private static final long SETTINGS_PAUSE_MS = 6000L;
    // Fire OS 5 won't let ADB disable or hide Amazon's apps, so end the idle ones each time Home comes up
    private static final long TRIM_DELAY_MS = 4000L;
    private static final long TRIM_INTERVAL_MS = 60 * 1000L;
    // Unused on this stick (no casting, mirroring or Alexa). Never listed: the launcher, firebat/avod (Prime Video),
    // venezia (Appstore), vizzini, logan, imp, the IME, Bluetooth and remote services, devicecontrol, tcomm,
    // whisperjoin (Wi-Fi), connectivity, identity/sync, device.messaging, OTA, settings, and whisperlink/discovery
    // (the Fire TV phone app is the way in if ADB is ever off).
    private static final String[] IDLE_AMAZON = {
            "com.amazon.tv.livetv", "com.amazon.kso.blackbird",
            "com.amazon.client.metrics", "com.amazon.ceviche", "com.amazon.diode",
            "com.amazon.tv.alexaalerts", "com.amazon.tv.alexanotifications",
            "com.amazon.alexamediaplayer.runtime.ftv", "com.amazon.ftv.screensaver",
            "com.amazon.ods.kindleconnect", "com.amazon.bueller.photos", "com.amazon.bueller.music",
            "com.amazon.tahoe", "com.amazon.adep", "com.amazon.assetsync.service",
            "com.amazon.tv.ottssocompanionapp", "com.amazon.wcast.sink", "com.amazon.alta.h2clientservice",
            "com.amazon.device.crashmanager", "com.amazon.perfcollection", "com.amazon.dpcclient",
            "com.amazon.tv.csapp", "com.amazon.tv.releasenotes", "com.amazon.tv.easyupgrade",
            "com.amazon.device.metrics", "com.amazon.tv.fw.metrics", "com.amazon.franktvinput",
            "com.amazon.cloud9", "com.amazon.gamehub", "com.amazon.imdb.tv.android.app",
            "com.imdb.livingroom.firetv", "com.amazon.minitv.android.app",
    };
    // At boot their receivers cold-start these too; end them once, not on every Home, so going back to an app
    // someone just left stays a warm start
    private static final String[] BOOT_STARTED_APPS = {
            "com.netflix.ninja", "com.amazon.firetv.youtube", "com.disney.disneyplus", "de.cbc.tvnow.firetv",
            "de.prosiebensat1digital.seventv", "de.swr.ard.avp.mobile.android.amazon", "com.zdf.android.mediathek",
    };
    // the receivers after BOOT_COMPLETED keep starting processes until about four minutes after power-on (measured:
    // adep, livetv, blackbird and photos came up at 3:30-4:00); trimming earlier only makes them start again
    private static final long BOOT_SETTLE_MS = 300 * 1000L;
    private static long lastTrim;
    private static boolean bootTrimDone;
    private static final int MARK = 0xFFF6F8FB;
    private static final int LABEL = 0xE6F6F8FB;
    private static final int MARK_BOX_W = 150;
    private static final int MARK_BOX_H = 76;
    private static final float MARK_AREA = 3400f;   // dp², about a 58 dp square

    private float dp;
    private final List<View> appTiles = new ArrayList<>();
    private WifiIconView wifiIcon;
    private TextClock time;
    private TextClock date;
    // take the cover off a moment after this screen has drawn underneath it
    private static final long COVER_HANDOVER_MS = 150L;
    private final Runnable handOver = Cover::hide;
    private final Runnable snapshot = () -> {
        View root = findViewById(android.R.id.content);
        if (root != null && hasWindowFocus()) {
            Cover.capture(root, time, date);
        }
    };
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private final Runnable statusUpdater = new Runnable() {
        @Override
        public void run() {
            refreshWifiStatus();
            statusHandler.postDelayed(this, STATUS_REFRESH_MS);
        }
    };
    private final Runnable trimmer = () -> new Thread(this::trimIdleApps, "trim").start();

    private static final AppEntry[] APPS = new AppEntry[] {
            // White single-colour marks as in CleanFrame (github.com/nthnclrk/CleanFrame): Netflix, YouTube,
            // Disney+ and the Prime smile from its icon set, RTL, Das Erste and ZDF from Simple Icons (CC0).
            // On Fire OS 5.2.9.5 Prime Video is com.amazon.firebat (Amazon's home starts it); the old
            // com.amazon.avod only finishes itself when launched on its own
            new AppEntry("Prime Video", "com.amazon.firebat", R.drawable.logo_primevideo_clean),
            new AppEntry("Netflix", "com.netflix.ninja", R.drawable.logo_netflix_clean),
            new AppEntry("YouTube", "com.amazon.firetv.youtube", R.drawable.logo_youtube_clean),
            new AppEntry("Disney+", "com.disney.disneyplus", R.drawable.logo_disneyplus_clean),
            new AppEntry("RTL+", "de.cbc.tvnow.firetv", R.drawable.logo_rtl_clean),
            // no open Joyn mark exists, so the name is set in type instead
            new AppEntry("7TV", "de.prosiebensat1digital.seventv", "joyn"),
            new AppEntry("ARD", "de.swr.ard.avp.mobile.android.amazon", R.drawable.logo_ard_clean),
            new AppEntry("ZDF", "com.zdf.android.mediathek", R.drawable.logo_zdf_clean),
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // Fire TV reports high density; halve it so this layout maps to a 1920x1080 design grid.
        dp = Math.max(1f, getResources().getDisplayMetrics().density / 2f);
        startService(new Intent(this, HomeWatcher.class));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0A111D);

        ImageView bgPhoto = new ImageView(this);
        bgPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
        // RGB_565 halves the bitmap; the photo has no alpha and a 1 GB stick is short on memory
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        bgPhoto.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.home_bg_forest, opts));
        root.addView(bgPhoto, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View topShade = new View(this);
        topShade.setBackground(background(0x22000000, 0x00000000));
        root.addView(topShade, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(220), Gravity.TOP));

        View bottomShade = new View(this);
        bottomShade.setBackground(background(0x00000000, 0x77000000));
        root.addView(bottomShade, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(380), Gravity.BOTTOM));

        LinearLayout corner = new LinearLayout(this);
        corner.setOrientation(LinearLayout.VERTICAL);
        time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("HH:mm");
        style(time, 58, 0xFFF6F8FB, "sans-serif-medium");
        time.setShadowLayer(px(2), 0, px(1), 0x66000000);
        time.setSingleLine(true);
        time.setGravity(Gravity.START);
        time.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        date = new TextClock(this);
        date.setFormat12Hour("EEEE, d. MMMM");
        date.setFormat24Hour("EEEE, d. MMMM");
        style(date, 20, 0xD6EEF6FF, "sans-serif");
        date.setShadowLayer(px(2), 0, px(1), 0x55000000);
        date.setSingleLine(true);
        date.setGravity(Gravity.START);
        date.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout clockRow = new LinearLayout(this);
        clockRow.setOrientation(LinearLayout.HORIZONTAL);
        clockRow.setGravity(Gravity.CENTER_VERTICAL);
        clockRow.addView(time);
        wifiIcon = new WifiIconView(this);
        LinearLayout.LayoutParams wifiLp = new LinearLayout.LayoutParams(px(66), px(50));
        wifiLp.leftMargin = px(26);
        wifiLp.topMargin = px(14);   // the clock's font padding sits above the digits; centre on the digits
        clockRow.addView(wifiIcon, wifiLp);
        corner.addView(clockRow);
        corner.addView(date);
        root.addView(corner, at(Gravity.TOP | Gravity.START, 56, 34));

        FrameLayout dock = new FrameLayout(this);
        // dark smoked glass, as CleanFrame's dock: white marks stay readable on the pale fog
        GradientDrawable dockBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x8C1E272D, 0x99161D22});
        dockBg.setCornerRadius(px(32));
        dockBg.setStroke(px(1), 0x1FFFFFFF);
        dock.setBackground(dockBg);
        FrameLayout.LayoutParams dockLp = new FrameLayout.LayoutParams(px(1180), px(460),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        dockLp.topMargin = px(360);
        root.addView(dock, dockLp);

        GridLayout apps = new GridLayout(this);
        apps.setColumnCount(4);
        apps.setRowCount(2);
        apps.setPadding(px(20), px(20), px(20), px(20));
        for (AppEntry app : APPS) {
            View tile = tile(app);
            appTiles.add(tile);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = px(262);
            lp.height = px(190);
            lp.setMargins(px(8), px(8), px(8), px(8));
            apps.addView(tile, lp);
        }
        dock.addView(apps, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        LinearLayout bottom = new LinearLayout(this);
        TextView settingsPill = pill("Einstellungen", v -> openSettings());
        TextView storePill = pill("Appstore", v -> openAppstore());
        TextView amazonPill = pill("Fire TV Startseite", v -> openAmazonHome());
        bottom.addView(settingsPill);
        bottom.addView(storePill);
        bottom.addView(amazonPill);
        linkFocus(settingsPill, storePill, amazonPill);
        root.addView(bottom, at(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 20));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        HomeWatcher.pause(0);   // back from Settings or Amazon's home: Home brings this screen up again
        refreshWifiStatus();
        statusHandler.removeCallbacks(statusUpdater);
        statusHandler.postDelayed(statusUpdater, STATUS_REFRESH_MS);
        statusHandler.removeCallbacks(trimmer);
        statusHandler.postDelayed(trimmer, TRIM_DELAY_MS);
        if (!appTiles.isEmpty()) {
            appTiles.get(0).requestFocus();
        }
        statusHandler.removeCallbacks(handOver);
        statusHandler.postDelayed(handOver, COVER_HANDOVER_MS);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        statusHandler.removeCallbacks(snapshot);
        if (hasFocus) {
            // the picture the cover shows next time: default focus, drawn, nothing in flight
            statusHandler.postDelayed(snapshot, 600);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        statusHandler.removeCallbacks(statusUpdater);
        statusHandler.removeCallbacks(trimmer);   // an app was opened: leave its process alone
    }

    @Override
    public void onBackPressed() {
        // this is Home: Back has nowhere to go
    }

    /**
     * CleanFrame-style tile: a white mark and the name, no box of its own. Only the focused tile gets a pale glass
     * plate, so the eye goes straight to it. Marks are sized by area, so a tall N and a flat smile look as heavy.
     */
    private View tile(AppEntry app) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(px(12), px(14), px(12), px(12));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x00FFFFFF);
        bg.setCornerRadius(px(24));
        card.setBackground(bg);

        FrameLayout markBox = new FrameLayout(this);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(px(MARK_BOX_W), px(MARK_BOX_H));
        boxLp.bottomMargin = px(14);
        card.addView(markBox, boxLp);

        TextView label = new TextView(this);
        style(label, 17, LABEL, "sans-serif-medium");
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setText(app.label);
        card.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        boolean installed = true;
        try {
            getPackageManager().getApplicationInfo(app.pkg, 0);
        } catch (PackageManager.NameNotFoundException e) {
            installed = false;
        }
        if (app.textMark != null) {
            TextView mark = new TextView(this);
            style(mark, 23, MARK, "sans-serif-medium");   // about as heavy as the drawn marks
            mark.setIncludeFontPadding(false);
            mark.setText(app.textMark);
            markBox.addView(mark, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        } else {
            ImageView icon = new ImageView(this);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Drawable d = getResources().getDrawable(app.logoRes, null);
            icon.setImageDrawable(d);
            icon.setColorFilter(MARK);
            int[] size = markSize(d.getIntrinsicWidth(), d.getIntrinsicHeight());
            markBox.addView(icon, new FrameLayout.LayoutParams(size[0], size[1], Gravity.CENTER));
        }
        if (!installed) {
            label.setText(app.label + " • installieren");
        }

        card.setFocusable(true);
        boolean finalInstalled = installed;
        float restAlpha = installed ? 0.82f : 0.45f;
        card.setAlpha(restAlpha);
        card.setOnClickListener(v -> openApp(app, finalInstalled));
        card.setOnFocusChangeListener((v, focused) -> {
            bg.setColor(focused ? 0x38FFFFFF : 0x00FFFFFF);
            bg.setStroke(px(1), focused ? 0x40FFFFFF : 0x00FFFFFF);
            label.setTextColor(focused ? 0xFFFFFFFF : LABEL);
            float scale = focused ? 1.04f : 1f;
            // animator scale is 0 on this stick, so set the end state directly
            v.setScaleX(scale);
            v.setScaleY(scale);
            v.setAlpha(focused ? 1f : restAlpha);
        });
        return card;
    }

    /** Same visual weight for every mark: equal area, kept inside the mark box. */
    private int[] markSize(int w, int h) {
        float aspect = h > 0 ? (float) w / h : 1f;
        float area = MARK_AREA;
        float mw = (float) Math.sqrt(area * aspect);
        float mh = (float) Math.sqrt(area / aspect);
        float fit = Math.min(1f, Math.min(MARK_BOX_W / mw, MARK_BOX_H / mh));
        return new int[] {px(Math.round(mw * fit)), px(Math.round(mh * fit))};
    }

    /**
     * The pills sit centred under the grid, outside the columns of the outer tiles, so Android's focus search
     * found nothing below RTL+ or ZDF. Down from the last row goes to the pill under it, Up from a pill goes back.
     */
    private void linkFocus(TextView left, TextView middle, TextView right) {
        left.setId(View.generateViewId());
        middle.setId(View.generateViewId());
        right.setId(View.generateViewId());
        int columns = 4;
        int lastRow = (appTiles.size() - 1) / columns;
        int firstOfLastRow = lastRow * columns;
        for (int i = 0; i < appTiles.size(); i++) {
            appTiles.get(i).setId(View.generateViewId());
        }
        for (int i = firstOfLastRow; i < appTiles.size(); i++) {
            int column = i % columns;
            View pill = column == 0 ? left : (column == columns - 1 ? right : middle);
            appTiles.get(i).setNextFocusDownId(pill.getId());
        }
        int last = appTiles.size() - 1;
        left.setNextFocusUpId(appTiles.get(firstOfLastRow).getId());
        middle.setNextFocusUpId(appTiles.get(Math.min(firstOfLastRow + 1, last)).getId());
        right.setNextFocusUpId(appTiles.get(Math.min(firstOfLastRow + columns - 1, last)).getId());
    }

    private TextView pill(String label, View.OnClickListener click) {
        TextView pill = new TextView(this);
        style(pill, 16, 0xCCFFFFFF, "sans-serif");
        pill.setText(label);
        pill.setPadding(px(20), px(8), px(20), px(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(24));
        bg.setColor(0x22FFFFFF);
        pill.setBackground(bg);
        pill.setFocusable(true);
        pill.setOnClickListener(click);
        pill.setOnFocusChangeListener((v, focused) -> {
            bg.setColor(focused ? 0xFFFFFFFF : 0x22FFFFFF);
            pill.setTextColor(focused ? 0xFF000000 : 0xCCFFFFFF);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(px(10), 0, px(10), 0);
        pill.setLayoutParams(lp);
        return pill;
    }

    private void openApp(AppEntry app, boolean installed) {
        if (!installed) {
            Toast.makeText(this, app.label + " ist nicht installiert.", Toast.LENGTH_LONG).show();
            return;
        }
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(app.pkg);
        if (intent == null) {
            intent = pm.getLaunchIntentForPackage(app.pkg);
        }
        launch(intent, app.label + " lässt sich nicht öffnen.");
    }

    private void openSettings() {
        // Fire OS answers ACTION_SETTINGS with the launcher's SettingsActivity, which at once starts Amazon's
        // HomeActivity_vNext (category HOME) to show the grid. Without a pause the watcher took that over.
        HomeWatcher.pause(SETTINGS_PAUSE_MS);
        launch(new Intent(Settings.ACTION_SETTINGS), "Einstellungen lassen sich nicht öffnen.");
    }

    /**
     * The store front, as Amazon's own appstore tile opens it. The package's launcher activity is only the
     * "Meine Apps" grid, and the appstore:// link Amazon's tile uses needs a permission apps do not get.
     */
    private void openAppstore() {
        Intent store = new Intent("com.amazon.venezia.ade.SHORTCUT_LAUNCH")
                .setClassName(APPSTORE, APPSTORE + ".ade.ADEHomeActivity");
        if (getPackageManager().resolveActivity(store, 0) == null) {
            store = getPackageManager().getLaunchIntentForPackage(APPSTORE);
        }
        launch(store, "Appstore lässt sich nicht öffnen.");
    }

    private void openAmazonHome() {
        HomeWatcher.pause(AMAZON_PAUSE_MS);
        Toast.makeText(this, "Amazon-Startseite für die nächsten 10 Minuten", Toast.LENGTH_LONG).show();
        launch(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(AMAZON_HOME),
                "Amazon-Startseite lässt sich nicht öffnen.");
    }

    private void launch(Intent intent, String failure) {
        try {
            if (intent == null) {
                throw new ActivityNotFoundException();
            }
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            HomeWatcher.pause(0);
            Toast.makeText(this, failure, Toast.LENGTH_LONG).show();
        }
    }

    /** killBackgroundProcesses only ends processes in the background, so a playing app is never touched. */
    private void trimIdleApps() {
        long now = android.os.SystemClock.elapsedRealtime();   // time since boot
        if (now < BOOT_SETTLE_MS) {
            statusHandler.postDelayed(trimmer, BOOT_SETTLE_MS - now + 1000);
            return;
        }
        if (lastTrim != 0 && now - lastTrim < TRIM_INTERVAL_MS) {
            return;
        }
        lastTrim = now;
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            return;
        }
        kill(am, IDLE_AMAZON);
        if (!bootTrimDone) {
            bootTrimDone = true;
            kill(am, BOOT_STARTED_APPS);
        }
    }

    private static void kill(ActivityManager am, String[] packages) {
        for (String pkg : packages) {
            try {
                am.killBackgroundProcesses(pkg);
            } catch (RuntimeException e) {
                // not installed or not allowed: nothing to trim
            }
        }
    }

    private void style(TextView view, float sp, int color, String family) {
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(family, Typeface.NORMAL));
    }

    private FrameLayout.LayoutParams at(int gravity, int horizontalDp, int verticalDp) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
        lp.setMargins(px(horizontalDp), px(verticalDp), px(horizontalDp), px(verticalDp));
        return lp;
    }

    private int px(int dpValue) {
        return Math.round(dpValue * dp);
    }

    private float pxf(float dpValue) {
        return dpValue * dp;
    }

    private void refreshWifiStatus() {
        if (wifiIcon == null) {
            return;
        }
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = cm != null ? cm.getActiveNetworkInfo() : null;
            boolean connected = info != null && info.isConnected();
            boolean wifi = connected && info.getType() == ConnectivityManager.TYPE_WIFI;

            if (wifi) {
                wifiIcon.setState(true, wifiBars());
            } else if (connected) {
                wifiIcon.setState(false, 0);
            } else {
                wifiIcon.setState(false, 0);
            }
        } catch (Exception e) {
            wifiIcon.setState(false, 0);
        }
    }

    private int wifiBars() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null) {
                return 0;
            }
            WifiInfo info = wm.getConnectionInfo();
            if (info == null || info.getNetworkId() == -1) {
                return 0;
            }
            int level = WifiManager.calculateSignalLevel(info.getRssi(), 5); // 0..4
            if (level < 0) {
                return 0;
            }
            if (level > 4) {
                return 4;
            }
            return level;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Classic Wi-Fi mark: a dot and three arcs over a 90 degree fan, one more lit per signal level. Drawn with the
     * clock's colour and the same soft shadow, with strokes thick enough to read from the sofa.
     */
    private class WifiIconView extends View {
        private static final int LIT = 0xFFF6F8FB;
        private static final int DIM = 0x47F6F8FB;
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arc = new RectF();
        private int level = 0; // 0..4
        private boolean connected = false;

        WifiIconView(Context context) {
            super(context);
            setLayerType(LAYER_TYPE_SOFTWARE, null);   // shadow layers on strokes need software drawing
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            fill.setStyle(Paint.Style.FILL);
            stroke.setShadowLayer(pxf(2), 0, pxf(1), 0x55000000);
            fill.setShadowLayer(pxf(2), 0, pxf(1), 0x55000000);
        }

        void setState(boolean connected, int level) {
            int lv = Math.max(0, Math.min(4, level));
            if (connected == this.connected && lv == this.level) {
                return;
            }
            this.connected = connected;
            this.level = lv;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float pad = pxf(3);                       // room for the shadow
            float size = Math.min(h - 2 * pad, (w - 2 * pad) / 1.48f);
            float thick = size * 0.13f;
            float cx = w / 2f;
            float cy = h - pad - thick * 0.6f;        // centre of the fan, at the dot
            stroke.setStrokeWidth(thick);

            // level 1 lights the dot, 2..4 the arcs from the inside out; no Wi-Fi: everything dim
            int lit = connected ? Math.max(level, 1) : 0;
            fill.setColor(lit >= 1 ? LIT : DIM);
            canvas.drawCircle(cx, cy, thick * 0.95f, fill);
            float[] radii = {size * 0.40f, size * 0.66f, size * 0.92f};
            for (int i = 0; i < radii.length; i++) {
                float r = radii[i];
                arc.set(cx - r, cy - r, cx + r, cy + r);
                stroke.setColor(lit >= i + 2 ? LIT : DIM);
                canvas.drawArc(arc, 225f, 90f, false, stroke);
            }
        }
    }

    private GradientDrawable background(int top, int bottom) {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {top, bottom});
    }

    private GradientDrawable background(int top, int middle, int bottom) {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {top, middle, bottom});
    }

    private static class AppEntry {
        final String label;
        final String pkg;
        final int logoRes;
        final String textMark;

        AppEntry(String label, String pkg, int logoRes) {
            this.label = label;
            this.pkg = pkg;
            this.logoRes = logoRes;
            this.textMark = null;
        }

        AppEntry(String label, String pkg, String textMark) {
            this.label = label;
            this.pkg = pkg;
            this.logoRes = 0;
            this.textMark = textMark;
        }
    }
}
