package com.simpletv.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Rotating national park photos for the home screen background. Once a week it pulls
 * Wikimedia Commons' "Featured pictures" of well-known parks and keeps a few dozen on the TV.
 * With no internet it uses what it already has, or nothing.
 */
class BackgroundPhotos {

    private static final String[] PARKS = {
        "Yellowstone National Park", "Yosemite National Park", "Grand Canyon National Park",
        "Zion National Park", "Glacier National Park (U.S.)", "Grand Teton National Park",
        "Arches National Park", "Bryce Canyon National Park", "Canyonlands National Park",
        "Rocky Mountain National Park", "Olympic National Park", "Mount Rainier National Park",
        "Acadia National Park", "Great Smoky Mountains National Park", "Death Valley National Park",
        "Joshua Tree National Park", "Sequoia National Park", "Denali National Park and Preserve",
        "Crater Lake National Park", "Capitol Reef National Park", "Badlands National Park",
        "Shenandoah National Park",
    };

    private static final String API = "https://commons.wikimedia.org/w/api.php?action=query"
            + "&format=json&generator=categorymembers&gcmtype=file&gcmlimit=100"
            + "&prop=imageinfo&iiprop=url%7Csize%7Cmime%7Cextmetadata"
            + "&iiextmetadatafilter=Artist%7CLicenseShortName&iiurlwidth=1920&gcmtitle=";
    // Wikimedia asks API clients to identify themselves.
    private static final String USER_AGENT =
            "SimpleTV/1.0 (Fire TV home screen; https://github.com/heidenreich/todo)";
    private static final long REFRESH_MS = 7L * 24 * 60 * 60 * 1000;
    private static final int KEEP = 30;

    interface Callback {
        void onPhoto(Bitmap bitmap, String caption);
    }

    private final File dir;
    private final File index;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private List<String[]> photos = new ArrayList<>(); // {file name, caption}; worker thread only
    private String lastShown;
    private boolean refreshing;

    BackgroundPhotos(Context context) {
        dir = new File(context.getFilesDir(), "photos");
        index = new File(dir, "index.json");
    }

    /** Decodes a random saved photo (not the one on screen) and hands it back on the main thread. */
    void showNext(final int width, final int height, final Callback callback) {
        worker.execute(new Runnable() {
            @Override public void run() {
                if (photos.isEmpty()) photos = readIndex();
                if (!refreshing && (photos.isEmpty()
                        || System.currentTimeMillis() - index.lastModified() > REFRESH_MS)) {
                    refreshing = true;
                    // Runs after this task, so a saved photo still shows right away.
                    worker.execute(new Runnable() {
                        @Override public void run() {
                            refresh();
                            refreshing = false;
                            // First run: show the first download instead of waiting for the next turn.
                            if (lastShown == null) show(width, height, callback);
                        }
                    });
                }
                show(width, height, callback);
            }
        });
    }

    private void show(int width, int height, final Callback callback) {
        String[] photo = pick();
        if (photo == null) return;
        final Bitmap bitmap = decode(new File(dir, photo[0]), width, height);
        final String caption = photo[1];
        if (bitmap == null) return;
        lastShown = photo[0];
        main.post(new Runnable() {
            @Override public void run() {
                callback.onPhoto(bitmap, caption);
            }
        });
    }

    private String[] pick() {
        if (photos.isEmpty()) return null;
        if (photos.size() == 1) return photos.get(0);
        String[] photo;
        do {
            photo = photos.get(random.nextInt(photos.size()));
        } while (photo[0].equals(lastShown));
        return photo;
    }

    private static Bitmap decode(File file, int width, int height) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), options);
        int sample = 1;
        while (width > 0 && height > 0
                && options.outWidth / (sample * 2) >= width && options.outHeight / (sample * 2) >= height) {
            sample *= 2;
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(file.getPath(), options);
    }

    // ---- Downloading (worker thread) ----

    private void refresh() {
        List<String[]> candidates = new ArrayList<>(); // {url, caption}
        for (String park : PARKS) {
            try {
                String category = "Category:Featured pictures of " + park;
                JSONObject query = new JSONObject(new String(
                        get(API + URLEncoder.encode(category, "UTF-8")), "UTF-8")).optJSONObject("query");
                JSONObject pages = query == null ? null : query.optJSONObject("pages");
                if (pages == null) continue;
                for (Iterator<String> it = pages.keys(); it.hasNext(); ) {
                    JSONArray infos = pages.getJSONObject(it.next()).optJSONArray("imageinfo");
                    if (infos == null || infos.length() == 0) continue;
                    JSONObject info = infos.getJSONObject(0);
                    int w = info.optInt("width"), h = info.optInt("height");
                    // Wide, sharp JPEGs only: they fill a TV screen without cropping much.
                    if (!"image/jpeg".equals(info.optString("mime")) || w < 1600 || w < h * 1.3) continue;
                    String url = info.optString("thumburl", info.optString("url"));
                    candidates.add(new String[] {url, caption(park, info.optJSONObject("extmetadata"))});
                }
            } catch (Exception e) {
                // Skip this park; offline or the category doesn't exist.
            }
        }
        if (candidates.isEmpty()) return;

        Collections.shuffle(candidates, random);
        List<String[]> fresh = new ArrayList<>();
        for (String[] candidate : candidates) {
            if (fresh.size() >= KEEP) break;
            String name = Integer.toHexString(candidate[0].hashCode()) + ".jpg";
            File file = new File(dir, name);
            try {
                if (!file.exists()) {
                    dir.mkdirs();
                    File tmp = new File(dir, name + ".tmp");
                    writeFile(tmp, get(candidate[0]));
                    if (!tmp.renameTo(file)) continue;
                }
                fresh.add(new String[] {name, candidate[1]});
            } catch (IOException e) {
                // Try the next one.
            }
        }
        if (fresh.isEmpty()) return;

        writeIndex(fresh);
        photos = fresh;
        Set<String> keep = new HashSet<>();
        for (String[] photo : fresh) keep.add(photo[0]);
        keep.add(index.getName());
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!keep.contains(file.getName())) file.delete();
            }
        }
    }

    private static String caption(String park, JSONObject meta) {
        String caption = park.replace(" (U.S.)", "");
        String artist = metaValue(meta, "Artist");
        String license = metaValue(meta, "LicenseShortName");
        if (!artist.isEmpty()) {
            if (artist.length() > 60) artist = artist.substring(0, 57) + "...";
            caption += "  ·  Photo: " + artist;
        }
        if (!license.isEmpty()) caption += " (" + license + ")";
        return caption;
    }

    @SuppressWarnings("deprecation")
    private static String metaValue(JSONObject meta, String key) {
        JSONObject field = meta == null ? null : meta.optJSONObject(key);
        if (field == null) return "";
        // Artist is HTML (often a link to the photographer's page).
        return Html.fromHtml(field.optString("value")).toString().replaceAll("\\s+", " ").trim();
    }

    private static byte[] get(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        try {
            if (connection.getResponseCode() != 200) throw new IOException("HTTP " + connection.getResponseCode());
            InputStream in = connection.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            for (int n; (n = in.read(buffer)) != -1; ) out.write(buffer, 0, n);
            return out.toByteArray();
        } finally {
            connection.disconnect();
        }
    }

    private static void writeFile(File file, byte[] data) throws IOException {
        OutputStream out = new FileOutputStream(file);
        try {
            out.write(data);
        } finally {
            out.close();
        }
    }

    // ---- Index of saved photos ----

    private List<String[]> readIndex() {
        List<String[]> result = new ArrayList<>();
        try {
            InputStream in = new java.io.FileInputStream(index);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8 * 1024];
            for (int n; (n = in.read(buffer)) != -1; ) out.write(buffer, 0, n);
            in.close();
            JSONArray array = new JSONArray(new String(out.toByteArray(), "UTF-8"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject photo = array.getJSONObject(i);
                if (new File(dir, photo.getString("file")).exists()) {
                    result.add(new String[] {photo.getString("file"), photo.optString("caption")});
                }
            }
        } catch (Exception e) {
            // No index yet.
        }
        return result;
    }

    private void writeIndex(List<String[]> list) {
        try {
            JSONArray array = new JSONArray();
            for (String[] photo : list) {
                array.put(new JSONObject().put("file", photo[0]).put("caption", photo[1]));
            }
            writeFile(index, array.toString().getBytes("UTF-8"));
        } catch (Exception e) {
            // Photos still show this session; the next start will refresh again.
        }
    }
}
