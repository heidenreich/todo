package com.simpletv.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The home screen: one row of big tiles for the apps you chose, plus Add and Settings. */
public class HomeActivity extends Activity {

    // Apps shown on first run. Each entry: labels to match ("|"-separated), then known
    // package names (Fire TV build first). Packages are tried first, labels as a fallback.
    private static final String[][] DEFAULT_APPS = {
        {"Prime Video", "com.amazon.firebat", "com.amazon.avod", "com.amazon.avod.thirdpartyclient"},
        {"YouTube TV", "com.amazon.firetv.youtube.tv", "com.google.android.youtube.tvunplugged"},
        {"Apple TV", "com.apple.atve.amazon.appletv", "com.apple.atve.androidtv.appletv"},
        {"HBO Max|Max", "com.wbd.stream", "com.hbo.hbonow"},
    };

    static final String AMAZON_HOME_PACKAGE = "com.amazon.tv.launcher";
    private static final String PREFS = "simpletv";
    private static final String KEY_APPS = "apps";

    private final Handler handler = new Handler();
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            clock.setText(android.text.format.DateFormat.getTimeFormat(HomeActivity.this).format(new Date()));
            handler.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000);
        }
    };

    private LinearLayout row;
    private TextView clock;
    private TextView notice;
    private TextView addButton;
    private List<String> apps;
    private int focusIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(12, 14, 18));

        clock = new TextView(this);
        clock.setTextColor(Color.rgb(200, 200, 200));
        clock.setTextSize(28);
        FrameLayout.LayoutParams clockLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        clockLp.setMargins(0, dp(32), dp(48), 0);
        root.addView(clock, clockLp);

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipChildren(false);
        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(48), dp(24), dp(48), dp(24));
        row.setClipChildren(false);
        scroller.addView(row);
        root.addView(scroller, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        notice = makeButton("Home button not set up yet. Press here to turn it on.");
        notice.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                openAccessibilitySettings();
            }
        });
        FrameLayout.LayoutParams noticeLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        noticeLp.setMargins(dp(48), dp(28), 0, 0);
        root.addView(notice, noticeLp);

        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        addButton = makeButton("+  Add app");
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showAddMenu();
            }
        });
        TextView settingsButton = makeButton("Settings");
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showSettingsMenu();
            }
        });
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonLp.setMargins(dp(12), 0, dp(12), 0);
        bottomBar.addView(addButton, buttonLp);
        bottomBar.addView(settingsButton, buttonLp);
        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        barLp.setMargins(0, 0, 0, dp(40));
        root.addView(bottomBar, barLp);

        setContentView(root);
    }

    private TextView makeButton(String label) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(16);
        button.setPadding(dp(20), dp(10), dp(20), dp(10));
        button.setFocusable(true);
        button.setClickable(true);
        button.setBackground(tileBackground(false));
        button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                v.setBackground(tileBackground(hasFocus));
            }
        });
        return button;
    }

    @Override
    protected void onResume() {
        super.onResume();
        apps = loadApps();
        buildTiles();
        notice.setVisibility(HomeButtonService.isEnabled(this) ? View.GONE : View.VISIBLE);
        handler.post(clockTick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(clockTick);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already here: jump back to the first tile.
        focusIndex = 0;
        if (row.getChildCount() > 0) row.getChildAt(0).requestFocus();
    }

    @Override
    public void onBackPressed() {
        // This is the home screen; there is nothing to go back to.
    }

    // ---- Tiles ----

    private void buildTiles() {
        row.removeAllViews();
        PackageManager pm = getPackageManager();
        // Fit every tile on screen: split the width between them, capped at 256dp.
        float density = getResources().getDisplayMetrics().density;
        int screenDp = (int) (getResources().getDisplayMetrics().widthPixels / density);
        int tileDp = Math.min(256, (screenDp - 96) / Math.max(apps.size(), 1) - 44);
        for (int i = 0; i < apps.size(); i++) {
            final int index = i;
            final String pkg = apps.get(i);
            View tile = makeTile(appArt(pm, pkg), appLabel(pm, pkg), tileDp);
            tile.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    focusIndex = index;
                    launchApp(pkg);
                }
            });
            tile.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    focusIndex = index;
                    showEditMenu(index);
                    return true;
                }
            });
            row.addView(tile);
        }

        if (apps.isEmpty()) {
            addButton.requestFocus();
        } else {
            focusIndex = Math.min(focusIndex, apps.size() - 1);
            row.getChildAt(focusIndex).requestFocus();
        }
    }

    private View makeTile(Drawable art, String label, int widthDp) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setPadding(dp(6), dp(6), dp(6), dp(6));
        tile.setBackground(tileBackground(false));

        ImageView image = new ImageView(this);
        image.setImageDrawable(art);
        boolean isBanner = art != null && art.getIntrinsicWidth() > art.getIntrinsicHeight();
        image.setScaleType(isBanner ? ImageView.ScaleType.FIT_XY : ImageView.ScaleType.FIT_CENTER);
        if (!isBanner) image.setPadding(dp(widthDp / 6), dp(widthDp / 6), dp(widthDp / 6), dp(widthDp / 6));
        tile.addView(image, new LinearLayout.LayoutParams(dp(widthDp), dp(widthDp * 9 / 16)));

        TextView text = new TextView(this);
        text.setText(label);
        text.setTextColor(Color.WHITE);
        text.setTextSize(18);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setGravity(Gravity.CENTER);
        text.setPadding(0, dp(8), 0, dp(4));
        tile.addView(text, new LinearLayout.LayoutParams(dp(widthDp), LinearLayout.LayoutParams.WRAP_CONTENT));

        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                v.setBackground(tileBackground(hasFocus));
                float scale = hasFocus ? 1.1f : 1f;
                v.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(16), 0, dp(16), 0);
        tile.setLayoutParams(lp);
        return tile;
    }

    private GradientDrawable tileBackground(boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(focused ? Color.rgb(48, 56, 72) : Color.rgb(28, 32, 40));
        if (focused) bg.setStroke(dp(3), Color.WHITE);
        return bg;
    }

    // ---- Menus ----

    private void showEditMenu(final int index) {
        String[] items = {"Move left", "Move right", "Remove from home screen"};
        dialog().setTitle(appLabel(getPackageManager(), apps.get(index)))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (which == 0 && index > 0) {
                            Collections.swap(apps, index, index - 1);
                            focusIndex = index - 1;
                        } else if (which == 1 && index < apps.size() - 1) {
                            Collections.swap(apps, index, index + 1);
                            focusIndex = index + 1;
                        } else if (which == 2) {
                            apps.remove(index);
                        }
                        saveApps();
                        buildTiles();
                    }
                }).show();
    }

    private void showAddMenu() {
        final List<String> choices = new ArrayList<>();
        final PackageManager pm = getPackageManager();
        for (String pkg : launchableApps().keySet()) {
            if (!apps.contains(pkg)) choices.add(pkg);
        }
        if (choices.isEmpty()) {
            Toast.makeText(this, "Every installed app is already on the home screen.", Toast.LENGTH_LONG).show();
            return;
        }
        String[] labels = new String[choices.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = appLabel(pm, choices.get(i));
        dialog().setTitle("Add an app")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        apps.add(choices.get(which));
                        focusIndex = apps.size() - 1;
                        saveApps();
                        buildTiles();
                    }
                }).show();
    }

    private void showSettingsMenu() {
        String[] items = {"Fire TV settings", "Amazon home screen (app store, updates)", "Turn on Home button"};
        dialog().setTitle("Settings")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            start(new Intent(Settings.ACTION_SETTINGS));
                        } else if (which == 1) {
                            start(new Intent(Intent.ACTION_MAIN)
                                    .addCategory(Intent.CATEGORY_HOME)
                                    .setPackage(AMAZON_HOME_PACKAGE));
                        } else {
                            openAccessibilitySettings();
                        }
                    }
                }).show();
    }

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
    }

    // ---- Launching ----

    private void launchApp(String pkg) {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (intent == null) intent = pm.getLaunchIntentForPackage(pkg);
        if (intent == null) {
            Toast.makeText(this, "That app can't be opened.", Toast.LENGTH_LONG).show();
            return;
        }
        start(intent);
    }

    private void openAccessibilitySettings() {
        start(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void start(Intent intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "This TV can't open that screen.", Toast.LENGTH_LONG).show();
        }
    }

    // ---- App list ----

    private List<String> loadApps() {
        Map<String, ResolveInfo> installed = launchableApps();
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String saved = prefs.getString(KEY_APPS, null);
        List<String> result = new ArrayList<>();
        if (saved == null) {
            for (String[] app : DEFAULT_APPS) {
                String pkg = findDefault(app, installed);
                if (pkg != null && !result.contains(pkg)) result.add(pkg);
            }
        } else if (!saved.isEmpty()) {
            for (String pkg : saved.split(",")) {
                if (installed.containsKey(pkg)) result.add(pkg);
            }
        }
        prefs.edit().putString(KEY_APPS, TextUtils.join(",", result)).apply();
        return result;
    }

    private void saveApps() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_APPS, TextUtils.join(",", apps)).apply();
    }

    private String findDefault(String[] app, Map<String, ResolveInfo> installed) {
        for (int i = 1; i < app.length; i++) {
            if (installed.containsKey(app[i])) return app[i];
        }
        List<String> labels = Arrays.asList(app[0].split("\\|"));
        PackageManager pm = getPackageManager();
        for (Map.Entry<String, ResolveInfo> e : installed.entrySet()) {
            String label = e.getValue().loadLabel(pm).toString().trim();
            for (String wanted : labels) {
                if (label.equalsIgnoreCase(wanted)) return e.getKey();
            }
        }
        return null;
    }

    /** Every app with a TV or regular launcher entry, sorted by name, keyed by package. */
    private Map<String, ResolveInfo> launchableApps() {
        final PackageManager pm = getPackageManager();
        List<ResolveInfo> found = new ArrayList<>();
        found.addAll(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        found.addAll(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        Collections.sort(found, new Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo a, ResolveInfo b) {
                return a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString());
            }
        });
        Map<String, ResolveInfo> byPackage = new LinkedHashMap<>();
        for (ResolveInfo info : found) {
            String pkg = info.activityInfo.packageName;
            if (!pkg.equals(getPackageName()) && !byPackage.containsKey(pkg)) byPackage.put(pkg, info);
        }
        return byPackage;
    }

    private String appLabel(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    /** The app's wide TV banner if it has one, otherwise its icon. */
    private Drawable appArt(PackageManager pm, String pkg) {
        Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
        try {
            if (launch != null) {
                ComponentName component = launch.getComponent();
                Drawable banner = component != null ? pm.getActivityBanner(component) : null;
                if (banner != null) return banner;
            }
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            Drawable banner = pm.getApplicationBanner(info);
            return banner != null ? banner : pm.getApplicationIcon(info);
        } catch (PackageManager.NameNotFoundException e) {
            return getDrawable(android.R.drawable.sym_def_app_icon);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
