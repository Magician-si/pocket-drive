package com.magician.pocketdrive;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.DocumentsContract;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.HttpURLConnection;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

import java.security.KeyStore;
import java.security.cert.Certificate;

public class MainActivity extends Activity {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int PORT = 8080;
    private static final int HTTPS_PORT = 8443;
    private static final int SOCKET_TIMEOUT_MS = 30000;
    private static final int DAV_MAX_CHILDREN = 500;
    private static final int REQ_TREE = 90;
    private static final String DEFAULT_USER = "admin";
    private static final String DEFAULT_PASS = "admin123456";

    private File shareDir;
    private File logFile;
    private File authFile;
    private File rootFile;
    private File trashDir;
    private File trashSettingFile;
    private PocketDriveServer server;
    private PocketDriveServer httpsServer;
    private String authUser = DEFAULT_USER;
    private String authPass = DEFAULT_PASS;
    private int trashDays = 30;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    private TextView stateBadge;
    private TextView addressText;
    private TextView folderText;
    private TextView hintText;
    private ImageView qrImage;
    private Button httpToggle;
    private Button httpsToggle;
    private boolean httpEnabled = false;
    private boolean httpsEnabled = true;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        File configDir = appFilesDir();
        rootFile = new File(configDir, "root.conf");
        shareDir = loadShareDir();
        logFile = new File(configDir, "nas.log");
        authFile = new File(configDir, "auth.conf");
        trashDir = new File(shareDir, ".trash");
        trashSettingFile = new File(configDir, "trash.conf");
        loadAuth();
        loadTrashSetting();
        buildUi();
        ensureFolders();
        requestStoragePermission();
        refresh();
        if (!hasSavedShareDir() && !isTvDevice()) {
            chooseShareFolder();
        }
    }

    private boolean isTvDevice() {
        return getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    private void buildUi() {
        if (isTvDevice()) {
            httpEnabled = true;
        }
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(20));
        root.setBackgroundColor(Color.rgb(238, 242, 246));
        scroll.addView(root);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18), dp(18), dp(18), dp(16));
        hero.setBackgroundColor(Color.rgb(20, 83, 91));
        root.addView(hero, matchWrap());

        TextView title = label("Pocket Drive", 26, true, Color.WHITE);
        hero.addView(title);
        TextView sub = label("独立网页文件共享", 14, false, Color.rgb(207, 250, 254));
        sub.setPadding(0, dp(4), 0, 0);
        hero.addView(sub);

        stateBadge = label("检测中", 18, true, Color.WHITE);
        stateBadge.setGravity(Gravity.CENTER);
        stateBadge.setPadding(dp(12), dp(14), dp(12), dp(14));
        root.addView(space(14));
        root.addView(stateBadge, matchWrap());

        LinearLayout mainActions = new LinearLayout(this);
        mainActions.setOrientation(LinearLayout.HORIZONTAL);
        Button startButton = button("开启共享");
        startButton.setTextSize(16);
        startButton.setPadding(dp(8), dp(12), dp(8), dp(12));
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                startNas();
            }
        });
        Button stopButton = button("关闭共享");
        stopButton.setTextSize(16);
        stopButton.setPadding(dp(8), dp(12), dp(8), dp(12));
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                stopNas();
            }
        });
        mainActions.addView(startButton, weight());
        mainActions.addView(stopButton, weight());
        root.addView(space(10));
        root.addView(mainActions, matchWrap());

        root.addView(section("连接"));
        LinearLayout protocolActions = new LinearLayout(this);
        protocolActions.setOrientation(LinearLayout.HORIZONTAL);
        httpToggle = button("HTTP 关闭");
        httpToggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                httpEnabled = !httpEnabled;
                if (!httpEnabled && !httpsEnabled) httpsEnabled = true;
                restartIfRunning();
                refresh();
            }
        });
        httpsToggle = button("HTTPS 开启");
        httpsToggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                httpsEnabled = !httpsEnabled;
                if (!httpEnabled && !httpsEnabled) httpEnabled = true;
                restartIfRunning();
                refresh();
            }
        });
        protocolActions.addView(httpToggle, weight());
        protocolActions.addView(httpsToggle, weight());
        root.addView(protocolActions, matchWrap());
        addressText = cardText("");
        folderText = cardText("");
        hintText = cardText("");
        root.addView(addressText);
        qrImage = new ImageView(this);
        qrImage.setBackgroundColor(Color.WHITE);
        qrImage.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(dp(172), dp(172));
        qrParams.gravity = Gravity.CENTER_HORIZONTAL;
        qrParams.setMargins(0, dp(10), 0, dp(10));
        root.addView(qrImage, qrParams);
        root.addView(folderText);
        root.addView(hintText);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button refresh = button("刷新状态");
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                refresh();
            }
        });
        Button copy = button("复制地址");
        copy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                copyAddress();
            }
        });
        Button cert = button("复制证书地址");
        cert.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                copyCertAddress();
            }
        });
        Button choose = button("选择文件夹");
        choose.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                chooseShareFolder();
            }
        });
        actions.addView(refresh, weight());
        actions.addView(copy, weight());
        actions.addView(cert, weight());
        root.addView(actions);
        root.addView(choose, matchWrap());

        setContentView(scroll);
    }

    private File loadShareDir() {
        File fallback = new File(appFilesDir(), "NAS");
        String path = savedRootPath();
        if (path.length() == 0) return fallback;
        File file = new File(path);
        return file.exists() || file.mkdirs() ? file : fallback;
    }

    private File appFilesDir() {
        File dir = getExternalFilesDir(null);
        return dir != null ? dir : getFilesDir();
    }

    private boolean hasSavedShareDir() {
        String path = savedRootPath();
        return path.length() > 0 && new File(path).exists();
    }

    private String savedRootPath() {
        String text = readFile(rootFile).trim();
        if (text.length() == 0) return "";
        String[] lines = text.split("\n");
        for (String line : lines) {
            if (line.startsWith("path=")) return line.substring(5).trim();
        }
        return text;
    }

    private void chooseShareFolder() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            startActivityForResult(intent, REQ_TREE);
        } catch (Exception e) {
            toast("无法打开文件夹选择器");
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQ_TREE && result == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    getContentResolver().takePersistableUriPermission(uri, data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
                } catch (Exception ignored) {
                }
                File picked = fileFromTreeUri(uri);
                if (picked != null) {
                    boolean wasRunning = isRunning();
                    if (wasRunning) stopNas();
                    shareDir = picked;
                    trashDir = new File(shareDir, ".trash");
                    writeFile(rootFile, "uri=" + uri.toString() + "\npath=" + shareDir.getAbsolutePath() + "\n");
                    ensureFolders();
                    if (wasRunning) startNas();
                    refresh();
                    toast("已选择: " + shareDir.getAbsolutePath());
                } else {
                    toast("这个文件夹无法转换为本地路径");
                }
            }
        }
    }

    private File fileFromTreeUri(Uri uri) {
        try {
            String id = DocumentsContract.getTreeDocumentId(uri);
            if (id == null) return null;
            int colon = id.indexOf(':');
            String volume = colon >= 0 ? id.substring(0, colon) : "primary";
            String rel = colon >= 0 ? id.substring(colon + 1) : "";
            File base;
            if ("primary".equalsIgnoreCase(volume)) {
                base = new File("/storage/emulated/0");
            } else {
                base = findStorageVolume(volume);
            }
            return rel.length() == 0 ? base : new File(base, rel);
        } catch (Exception e) {
            return null;
        }
    }

    private File findStorageVolume(String volume) {
        File exact = new File("/storage/" + volume);
        if (exact.exists()) return exact;
        File storage = new File("/storage");
        File[] files = storage.listFiles();
        if (files != null) {
            String wanted = volume.toLowerCase(Locale.US);
            for (File file : files) {
                String name = file.getName().toLowerCase(Locale.US);
                if (name.equals(wanted) || name.replace("-", "").equals(wanted.replace("-", ""))) {
                    return file;
                }
            }
        }
        return exact;
    }

    private void ensureFolders() {
        if (!shareDir.exists()) {
            shareDir.mkdirs();
        }
        if (!trashDir.exists()) {
            trashDir.mkdirs();
        }
        cleanTrash();
        File readme = new File(shareDir, "README.txt");
        if (!readme.exists()) {
            writeFile(readme, "Put NAS files here.\n上传的文件会保存在这个文件夹。\n");
        }
    }

    private void loadAuth() {
        String text = readFile(authFile).trim();
        if (text.length() == 0) return;
        String[] lines = text.split("\n");
        if (lines.length >= 2) {
            authUser = lines[0].trim().length() == 0 ? DEFAULT_USER : lines[0].trim();
            authPass = lines[1].trim().length() == 0 ? DEFAULT_PASS : lines[1].trim();
        }
    }

    private void saveAuth(String user, String pass) {
        authUser = user.length() == 0 ? DEFAULT_USER : user;
        authPass = pass.length() == 0 ? DEFAULT_PASS : pass;
        writeFile(authFile, authUser + "\n" + authPass + "\n");
    }

    private void loadTrashSetting() {
        String text = readFile(trashSettingFile).trim();
        if (text.length() == 0) return;
        try {
            trashDays = Integer.parseInt(text);
        } catch (Exception ignored) {
            trashDays = 30;
        }
        if (trashDays != 7 && trashDays != 30) trashDays = 30;
    }

    private void saveTrashSetting(int days) {
        trashDays = days == 7 ? 7 : 30;
        writeFile(trashSettingFile, String.valueOf(trashDays));
    }

    private void cleanTrash() {
        File[] files = trashDir.listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - trashDays * 24L * 60L * 60L * 1000L;
        for (File file : files) {
            if (file.lastModified() < cutoff) {
                deleteTree(file);
            }
        }
    }

    private void deleteTree(File file) {
        File[] files = file.listFiles();
        if (files != null) for (File child : files) deleteTree(child);
        file.delete();
    }

    private void startNas() {
        ensureFolders();
        acquireRunLocks();
        if (httpEnabled && (server == null || !server.isRunning())) {
            server = new PocketDriveServer(shareDir, false);
            server.start();
            appendLog("START Pocket Drive");
        } else if (!httpEnabled && server != null) {
            server.stopServer();
            server = null;
        }
        if (httpsEnabled && (httpsServer == null || !httpsServer.isRunning())) {
            httpsServer = new PocketDriveServer(shareDir, true);
            httpsServer.start();
            appendLog("START Pocket Drive HTTPS");
        } else if (!httpsEnabled && httpsServer != null) {
            httpsServer.stopServer();
            httpsServer = null;
        }
        refresh();
    }

    private void acquireRunLocks() {
        try {
            if (wakeLock == null) {
                PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
                if (power != null) {
                    wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PocketDrive:Server");
                    wakeLock.setReferenceCounted(false);
                }
            }
            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();
        } catch (Exception ignored) {
        }
        try {
            if (wifiLock == null) {
                WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                if (wifi != null) {
                    wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "PocketDrive:Wifi");
                    wifiLock.setReferenceCounted(false);
                }
            }
            if (wifiLock != null && !wifiLock.isHeld()) wifiLock.acquire();
        } catch (Exception ignored) {
        }
    }

    private void releaseRunLocks() {
        try {
            if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        } catch (Exception ignored) {
        }
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Exception ignored) {
        }
    }

    private void restartIfRunning() {
        if (!isRunning()) return;
        if (server != null && !httpEnabled) {
            server.stopServer();
            server = null;
        }
        if (httpsServer != null && !httpsEnabled) {
            httpsServer.stopServer();
            httpsServer = null;
        }
        startNas();
    }

    private void stopNas() {
        if (server != null) {
            server.stopServer();
            server = null;
            appendLog("STOP Pocket Drive");
        }
        if (httpsServer != null) {
            httpsServer.stopServer();
            httpsServer = null;
            appendLog("STOP Pocket Drive HTTPS");
        }
        releaseRunLocks();
        refresh();
    }

    private boolean isRunning() {
        return (server != null && server.isRunning()) || (httpsServer != null && httpsServer.isRunning());
    }

    private void refresh() {
        ensureFolders();
        String ip = getIpAddress();
        boolean running = isRunning();
        stateBadge.setText(running ? "已开启" : "已关闭");
        stateBadge.setBackgroundColor(running ? Color.rgb(13, 148, 136) : Color.rgb(190, 18, 60));
        String host = ip.length() == 0 ? "手机IP" : ip;
        String address = "http://" + host + ":" + PORT;
        String secureAddress = "https://" + host + ":" + HTTPS_PORT;
        boolean tv = isTvDevice();
        String primaryAddress = (httpsEnabled && !tv) ? secureAddress : address;
        StringBuilder addressInfo = new StringBuilder();
        addressInfo.append("地址\n");
        if (tv && httpEnabled) {
            addressInfo.append(address);
            if (httpsEnabled) addressInfo.append("\nHTTPS: ").append(secureAddress);
        } else {
            if (httpsEnabled) addressInfo.append(secureAddress);
            if (httpEnabled) {
                if (httpsEnabled) addressInfo.append("\n备用: ");
                addressInfo.append(address);
            }
        }
        addressText.setText(addressInfo.toString());
        updateProtocolButtons();
        if (qrImage != null) {
            qrImage.setImageBitmap(qrBitmap(primaryAddress, dp(156)));
            loadClientQr(primaryAddress);
        }
        folderText.setText("文件夹\n" + shareDir.getAbsolutePath());
        hintText.setText("账号: " + authUser + "\n密码: " + authPass);
    }

    private void updateProtocolButtons() {
        if (httpToggle != null) {
            httpToggle.setText(httpEnabled ? "HTTP 开启" : "HTTP 关闭");
            httpToggle.setTextColor((httpEnabled || httpToggle.hasFocus()) ? Color.WHITE : Color.rgb(15, 23, 42));
            httpToggle.setBackground(buttonBg(httpEnabled || httpToggle.hasFocus()));
        }
        if (httpsToggle != null) {
            httpsToggle.setText(httpsEnabled ? "HTTPS 开启" : "HTTPS 关闭");
            httpsToggle.setTextColor((httpsEnabled || httpsToggle.hasFocus()) ? Color.WHITE : Color.rgb(15, 23, 42));
            httpsToggle.setBackground(buttonBg(httpsEnabled || httpsToggle.hasFocus()));
        }
    }

    private void loadClientQr(final String address) {
        if (address.indexOf("手机IP") >= 0) return;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    String urlText = "https://api.qrserver.com/v1/create-qr-code/?size=260x260&data=" + URLEncoder.encode(address, "UTF-8");
                    HttpURLConnection conn = (HttpURLConnection) new URL(urlText).openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    final Bitmap bitmap = BitmapFactory.decodeStream(conn.getInputStream());
                    conn.disconnect();
                    if (bitmap == null) return;
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (qrImage != null) qrImage.setImageBitmap(bitmap);
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        }, "ClientQrLoader").start();
    }

    private String getIpAddress() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm != null) {
                WifiInfo info = wm.getConnectionInfo();
                int ip = info.getIpAddress();
                if (ip != 0) {
                    return String.format(Locale.US, "%d.%d.%d.%d", ip & 0xff, ip >> 8 & 0xff, ip >> 16 & 0xff, ip >> 24 & 0xff);
                }
            }
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface ni : interfaces) {
                List<java.net.InetAddress> addresses = Collections.list(ni.getInetAddresses());
                for (java.net.InetAddress address : addresses) {
                    if (!address.isLoopbackAddress() && address instanceof Inet4Address) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private void copyAddress() {
        String text = addressText.getText().toString().replace("地址\n", "").split("\n", 2)[0];
        ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText("NAS address", text));
            toast("已复制");
        }
    }

    private void copyCertAddress() {
        String ip = getIpAddress();
        String host = ip.length() == 0 ? "手机IP" : ip;
        String text = "https://" + host + ":" + HTTPS_PORT + "/cert.cer";
        ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText("PocketDrive certificate", text));
            toast("已复制证书地址");
        }
    }

    private void requestStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 30);
            }
        }
    }

    private void appendLog(String text) {
        String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + " " + text + "\n";
        writeFile(logFile, readFile(logFile) + line);
    }

    private String readFile(File file) {
        try {
            if (!file.exists()) {
                return "";
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), UTF8));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            reader.close();
            return builder.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void writeFile(File file, String text) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            FileOutputStream writer = new FileOutputStream(file);
            writer.write(text.getBytes(UTF8));
            writer.close();
        } catch (Exception e) {
            toast(e.getMessage());
        }
    }

    private TextView section(String text) {
        TextView view = label(text, 18, true, Color.rgb(15, 23, 42));
        view.setPadding(0, dp(18), 0, dp(8));
        return view;
    }

    private TextView cardText(String text) {
        TextView view = label(text, 14, false, Color.rgb(30, 41, 59));
        view.setPadding(dp(12), dp(10), dp(12), dp(10));
        view.setBackgroundColor(Color.WHITE);
        return view;
    }

    private TextView label(String text, int sp, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }
        view.setGravity(Gravity.START);
        return view;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setFocusable(true);
        button.setMinHeight(dp(46));
        button.setTextColor(Color.rgb(15, 23, 42));
        button.setBackground(buttonBg(false));
        button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean hasFocus) {
                Button focused = (Button) view;
                focused.setTextColor(hasFocus ? Color.WHITE : Color.rgb(15, 23, 42));
                focused.setBackground(buttonBg(hasFocus));
            }
        });
        return button;
    }

    private StateListDrawable buttonBg(boolean focused) {
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(Color.rgb(226, 232, 240));
        normal.setCornerRadius(dp(8));
        normal.setStroke(dp(1), Color.rgb(148, 163, 184));
        GradientDrawable focus = new GradientDrawable();
        focus.setColor(Color.rgb(15, 83, 91));
        focus.setCornerRadius(dp(8));
        focus.setStroke(dp(3), Color.rgb(45, 212, 191));
        StateListDrawable drawable = new StateListDrawable();
        drawable.addState(new int[]{android.R.attr.state_focused}, focus);
        drawable.addState(new int[]{android.R.attr.state_pressed}, focus);
        drawable.addState(new int[]{}, focused ? focus : normal);
        return drawable;
    }

    private View space(int dp) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(dp)));
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private Bitmap qrBitmap(String text, int size) {
        boolean[][] modules = qrModules(text == null ? "" : text);
        int count = modules.length;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        int quiet = 4;
        int fullCount = count + quiet * 2;
        int scale = Math.max(1, size / fullCount);
        int qrSize = scale * fullCount;
        int offset = Math.max(0, (size - qrSize) / 2);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                bitmap.setPixel(x, y, Color.WHITE);
            }
        }
        for (int y = 0; y < count; y++) {
            for (int x = 0; x < count; x++) {
                if (!modules[y][x]) continue;
                for (int dy = 0; dy < scale; dy++) {
                    for (int dx = 0; dx < scale; dx++) {
                        bitmap.setPixel(offset + (x + quiet) * scale + dx, offset + (y + quiet) * scale + dy, Color.BLACK);
                    }
                }
            }
        }
        return bitmap;
    }

    private boolean[][] qrModules(String text) {
        byte[] bytes = text.getBytes(UTF8);
        if (bytes.length > 32) {
            bytes = text.substring(0, Math.min(text.length(), 32)).getBytes(UTF8);
        }
        int[] data = qrDataCodewords(bytes);
        int[] ecc = qrEcc(data, 10);
        int[] all = new int[data.length + ecc.length];
        System.arraycopy(data, 0, all, 0, data.length);
        System.arraycopy(ecc, 0, all, data.length, ecc.length);
        int size = 25;
        boolean[][] m = new boolean[size][size];
        boolean[][] r = new boolean[size][size];
        addQrFinder(m, r, 0, 0);
        addQrFinder(m, r, size - 7, 0);
        addQrFinder(m, r, 0, size - 7);
        addQrAlignment(m, r, 18, 18);
        for (int i = 8; i < size - 8; i++) {
            setQr(m, r, i, 6, i % 2 == 0);
            setQr(m, r, 6, i, i % 2 == 0);
        }
        setQr(m, r, 8, size - 8, true);
        putQrData(m, r, all);
        addQrFormat(m, r);
        return m;
    }

    private int[] qrDataCodewords(byte[] bytes) {
        List<Integer> bits = new ArrayList<Integer>();
        appendBits(bits, 4, 4);
        appendBits(bits, bytes.length, 8);
        for (byte b : bytes) appendBits(bits, b & 0xff, 8);
        int capacityBits = 34 * 8;
        int terminator = Math.min(4, capacityBits - bits.size());
        appendBits(bits, 0, terminator);
        while (bits.size() % 8 != 0) bits.add(0);
        int[] data = new int[34];
        for (int i = 0; i < bits.size() / 8 && i < data.length; i++) {
            int v = 0;
            for (int j = 0; j < 8; j++) v = (v << 1) | bits.get(i * 8 + j);
            data[i] = v;
        }
        int pad = 0xec;
        for (int i = bits.size() / 8; i < data.length; i++) {
            data[i] = pad;
            pad = pad == 0xec ? 0x11 : 0xec;
        }
        return data;
    }

    private void appendBits(List<Integer> bits, int value, int count) {
        for (int i = count - 1; i >= 0; i--) bits.add((value >> i) & 1);
    }

    private int[] qrEcc(int[] data, int degree) {
        int[] gen = new int[]{1};
        for (int i = 0; i < degree; i++) {
            int[] next = new int[gen.length + 1];
            for (int j = 0; j < gen.length; j++) {
                next[j] ^= gfMul(gen[j], 1);
                next[j + 1] ^= gfMul(gen[j], gfPow(2, i));
            }
            gen = next;
        }
        int[] res = new int[degree];
        for (int b : data) {
            int factor = b ^ res[0];
            System.arraycopy(res, 1, res, 0, degree - 1);
            res[degree - 1] = 0;
            for (int i = 0; i < degree; i++) res[i] ^= gfMul(gen[i + 1], factor);
        }
        return res;
    }

    private int gfMul(int x, int y) {
        int z = 0;
        while (y != 0) {
            if ((y & 1) != 0) z ^= x;
            y >>>= 1;
            x <<= 1;
            if ((x & 0x100) != 0) x ^= 0x11d;
        }
        return z & 0xff;
    }

    private int gfPow(int x, int n) {
        int y = 1;
        for (int i = 0; i < n; i++) y = gfMul(y, x);
        return y;
    }

    private void addQrFinder(boolean[][] m, boolean[][] r, int left, int top) {
        for (int y = -1; y <= 7; y++) for (int x = -1; x <= 7; x++) {
            int xx = left + x, yy = top + y;
            if (xx < 0 || yy < 0 || xx >= m.length || yy >= m.length) continue;
            r[yy][xx] = true;
            m[yy][xx] = x >= 0 && x <= 6 && y >= 0 && y <= 6 && (x == 0 || x == 6 || y == 0 || y == 6 || (x >= 2 && x <= 4 && y >= 2 && y <= 4));
        }
    }

    private void addQrAlignment(boolean[][] m, boolean[][] r, int cx, int cy) {
        for (int y = -2; y <= 2; y++) for (int x = -2; x <= 2; x++) {
            boolean on = Math.max(Math.abs(x), Math.abs(y)) != 1;
            setQr(m, r, cx + x, cy + y, on);
        }
    }

    private void setQr(boolean[][] m, boolean[][] r, int x, int y, boolean on) {
        if (x < 0 || y < 0 || x >= m.length || y >= m.length) return;
        m[y][x] = on;
        r[y][x] = true;
    }

    private void putQrData(boolean[][] m, boolean[][] r, int[] codewords) {
        int bit = 0;
        int dir = -1;
        for (int right = m.length - 1; right >= 1; right -= 2) {
            if (right == 6) right--;
            for (int vert = 0; vert < m.length; vert++) {
                int y = dir == 1 ? vert : m.length - 1 - vert;
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    if (r[y][x]) continue;
                    boolean on = false;
                    if (bit < codewords.length * 8) on = ((codewords[bit >>> 3] >>> (7 - (bit & 7))) & 1) != 0;
                    if ((x + y) % 2 == 0) on = !on;
                    m[y][x] = on;
                    bit++;
                }
            }
            dir = -dir;
        }
    }

    private void addQrFormat(boolean[][] m, boolean[][] r) {
        int bits = 0x77c4;
        for (int i = 0; i < 15; i++) {
            boolean on = ((bits >> i) & 1) != 0;
            int[][] a = new int[][]{{8,0},{8,1},{8,2},{8,3},{8,4},{8,5},{8,7},{8,8},{7,8},{5,8},{4,8},{3,8},{2,8},{1,8},{0,8}};
            int[][] b = new int[][]{{24,8},{23,8},{22,8},{21,8},{20,8},{19,8},{18,8},{8,17},{8,18},{8,19},{8,20},{8,21},{8,22},{8,23},{8,24}};
            m[a[i][1]][a[i][0]] = on;
            m[b[i][1]][b[i][0]] = on;
        }
    }


    private class PocketDriveServer implements Runnable {
        private final File root;
        private volatile boolean running;
        private ServerSocket socket;
        private final Map<String, String> locks = new HashMap<String, String>();

        private final boolean secure;

        PocketDriveServer(File root, boolean secure) {
            this.root = root;
            this.secure = secure;
        }

        void start() {
            running = true;
            new Thread(this, secure ? "PocketDriveHttpsServer" : "PocketDriveServer").start();
        }

        boolean isRunning() {
            return running;
        }

        void stopServer() {
            running = false;
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (Exception ignored) {
            }
        }

        @Override
        public void run() {
            while (running) {
                try {
                    socket = secure ? sslServerSocket() : new ServerSocket(PORT);
                    while (running) {
                        final Socket client = socket.accept();
                        new Thread(new Runnable() {
                            @Override public void run() {
                                handle(client);
                            }
                        }).start();
                    }
                } catch (Exception e) {
                    if (!running) break;
                    appendLog((secure ? "HTTPS" : "HTTP") + " SERVER RESTART " + e.getMessage());
                    try {
                        if (socket != null) socket.close();
                    } catch (Exception ignored) {
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        private ServerSocket sslServerSocket() throws Exception {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            InputStream in = getAssets().open("pocketdrive_tls.p12");
            char[] password = "pocketdrive".toCharArray();
            keyStore.load(in, password);
            in.close();
            KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            factory.init(keyStore, password);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(factory.getKeyManagers(), null, null);
            SSLServerSocketFactory socketFactory = context.getServerSocketFactory();
            SSLServerSocket serverSocket = (SSLServerSocket) socketFactory.createServerSocket(HTTPS_PORT);
            try {
                serverSocket.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
            } catch (Exception ignored) {
                serverSocket.setEnabledProtocols(new String[]{"TLSv1.2"});
            }
            return serverSocket;
        }

        private void handle(Socket client) {
            try {
                client.setSoTimeout(SOCKET_TIMEOUT_MS);
                BufferedInputStream input = new BufferedInputStream(client.getInputStream());
                OutputStream output = client.getOutputStream();
                while (running && !client.isClosed()) {
                ByteArrayOutputStream headers = new ByteArrayOutputStream();
                int matched = 0;
                int b;
                byte[] end = new byte[]{'\r', '\n', '\r', '\n'};
                while ((b = input.read()) != -1) {
                    headers.write(b);
                    matched = b == end[matched] ? matched + 1 : (b == end[0] ? 1 : 0);
                    if (matched == end.length) {
                        break;
                    }
                }
                if (headers.size() == 0) break;
                String head = new String(headers.toByteArray(), UTF8);
                String[] lines = head.split("\r\n");
                if (lines.length == 0) {
                    break;
                }
                String[] request = lines[0].split(" ");
                if (request.length < 2) {
                    sendText(output, "400 Bad Request", "Bad request");
                    break;
                }
                String method = request[0];
                String path = requestPath(request[1]);
                boolean davRequest = isDavMethod(method);
                long length = 0;
                String type = "";
                String auth = "";
                String transferEncoding = "";
                String depth = "1";
                boolean expectContinue = false;
                for (String line : lines) {
                    String lower = line.toLowerCase(Locale.US);
                    if (lower.startsWith("content-length:")) {
                        length = Long.parseLong(line.substring(line.indexOf(':') + 1).trim());
                    } else if (lower.startsWith("content-type:")) {
                        type = line.substring(line.indexOf(':') + 1).trim();
                    } else if (lower.startsWith("authorization:")) {
                        auth = line.substring(line.indexOf(':') + 1).trim();
                    } else if (lower.startsWith("transfer-encoding:")) {
                        transferEncoding = line.substring(line.indexOf(':') + 1).trim().toLowerCase(Locale.US);
                    } else if (lower.startsWith("expect:")) {
                        expectContinue = "100-continue".equalsIgnoreCase(line.substring(line.indexOf(':') + 1).trim());
                    } else if (lower.startsWith("depth:")) {
                        depth = line.substring(line.indexOf(':') + 1).trim().toLowerCase(Locale.US);
                    }
                }
                if ("GET".equals(method) && (path.startsWith("/cert.cer") || path.startsWith("/__cert.cer"))) {
                    drainUnusedBody(input, length, transferEncoding);
                    sendCertificate(output);
                    break;
                }
                if (!authorized(auth)) {
                    output.write(("HTTP/1.1 401 Unauthorized\r\nWWW-Authenticate: Basic realm=\"NAS\"\r\nConnection: close\r\nContent-Length: 0\r\n\r\n").getBytes(UTF8));
                    break;
                }
                if (expectContinue) {
                    output.write(("HTTP/1.1 100 Continue\r\n\r\n").getBytes(UTF8));
                    output.flush();
                }
                if ("OPTIONS".equals(method)) {
                    drainUnusedBody(input, length, transferEncoding);
                    sendOptions(output);
                    logDav(method, path, "200");
                } else if ("PROPFIND".equals(method)) {
                    drainUnusedBody(input, length, transferEncoding);
                    File target = fileForPath(path);
                    if (target.exists()) {
                        sendPropfind(output, path, target, depth);
                        logDav(method, path, "207");
                    } else {
                        sendText(output, "404 Not Found", "Not found");
                        logDav(method, path, "404");
                    }
                } else if ("PROPPATCH".equals(method)) {
                    drainUnusedBody(input, length, transferEncoding);
                    File target = fileForPath(path);
                    if (target.exists()) {
                        sendPropfind(output, path, target, depth);
                        logDav(method, path, "207");
                    } else {
                        sendText(output, "404 Not Found", "Not found");
                        logDav(method, path, "404");
                    }
                } else if ("HEAD".equals(method)) {
                    drainUnusedBody(input, length, transferEncoding);
                    File target = fileForPath(path);
                    if (target.exists()) {
                        sendHead(output, target);
                        logDav(method, path, "200");
                    } else {
                        sendText(output, "404 Not Found", "Not found");
                        logDav(method, path, "404");
                    }
                } else if ("PUT".equals(method)) {
                    File target = fileForPath(path);
                    boolean existed = target.exists();
                    if (existed && isOverwriteFalse(lines)) {
                        drainRequestBody(input, length, transferEncoding);
                        sendText(output, "412 Precondition Failed", "Target exists");
                        logDav(method, path, "412");
                    } else {
                        try {
                            savePut(target, input, length, transferEncoding);
                            sendText(output, existed ? "204 No Content" : "201 Created", "");
                            logDav(method, path, (existed ? "204" : "201") + " size=" + target.length());
                        } catch (Exception e) {
                            sendText(output, "500 Internal Server Error", "PUT failed");
                            logDav(method, path, "500 " + e.getClass().getSimpleName());
                        }
                    }
                } else if ("MKCOL".equals(method)) {
                    drainUnusedBody(input, length, transferEncoding);
                    File target = fileForPath(path);
                    if (target.isDirectory()) {
                        sendText(output, "405 Method Not Allowed", "Already exists");
                        logDav(method, path, "405");
                    } else if (target.exists()) {
                        sendText(output, "405 Method Not Allowed", "Already exists");
                        logDav(method, path, "405");
                    } else if (target.mkdirs()) {
                        sendText(output, "201 Created", "Created");
                        logDav(method, path, "201");
                    } else {
                        sendText(output, "403 Forbidden", "Cannot create collection");
                        logDav(method, path, "403");
                    }
                } else if ("DELETE".equals(method)) {
                    drainUnusedBody(input, length, transferEncoding);
                    File target = fileForPath(path);
                    if (target.isDirectory()) deleteTree(target);
                    else if (target.isFile()) target.delete();
                    sendText(output, "204 No Content", "");
                    logDav(method, path, "204");
                } else if ("MOVE".equals(method)) {
                    drainUnusedBody(input, length, transferEncoding);
                    String status = handleDavMove(path, lines);
                    sendText(output, status, "");
                    logDav(method, path, status);
                } else if ("COPY".equals(method)) {
                    drainUnusedBody(input, length, transferEncoding);
                    String status = handleDavCopy(path, lines);
                    sendText(output, status, "");
                    logDav(method, path, status);
                } else if ("LOCK".equals(method)) {
                    drainUnusedBody(input, length, transferEncoding);
                    sendLock(output, path);
                    logDav(method, path, "200");
                } else if ("UNLOCK".equals(method)) {
                    drainUnusedBody(input, length, transferEncoding);
                    unlockPath(path, lines);
                    sendText(output, "204 No Content", "");
                    logDav(method, path, "204");
                } else if ("POST".equals(method)) {
                    String tab = handlePost(path, type, input, length);
                    redirect(output, path + (tab.length() == 0 ? "" : "#" + tab));
                    break;
                } else {
                    if (path.startsWith("/__qr.png")) {
                        String value = queryValue(path, "u");
                        sendPng(output, qrBitmap(value.length() == 0 ? ("http://" + getIpAddress() + ":" + PORT) : value, 240));
                        break;
                    }
                    File target = fileForPath(path);
                    if (target.isDirectory()) {
                        sendHtml(output, listPage(path, target));
                    } else if (target.isFile()) {
                        if (path.indexOf("thumb=1") >= 0 && isImage(target)) sendInlineFile(output, target);
                        else if (path.indexOf("preview=1") >= 0) sendPreview(output, target);
                        else sendFile(output, target);
                    } else {
                        sendText(output, "404 Not Found", "Not found");
                    }
                    break;
                }
                output.flush();
                if (secure || davRequest || shouldClose(lines)) break;
                }
                client.close();
            } catch (Exception e) {
                appendLog("HTTP ERROR " + e.getClass().getSimpleName() + " " + e.getMessage());
                try {
                    client.close();
                } catch (Exception ignoredAgain) {
                }
            }
        }

        private boolean authorized(String auth) {
            String expected = "Basic " + Base64.encodeToString((authUser + ":" + authPass).getBytes(UTF8), Base64.NO_WRAP);
            return expected.equals(auth);
        }

        private File fileForPath(String rawPath) throws Exception {
            String path = requestPath(rawPath).split("\\?", 2)[0];
            path = URLDecoder.decode(path, "UTF-8");
            File file = root;
            for (String part : path.split("/")) {
                if (part.length() == 0 || ".".equals(part) || "..".equals(part)) continue;
                file = new File(file, part);
            }
            return file;
        }

        private String requestPath(String rawPath) {
            if (rawPath == null || rawPath.length() == 0) return "/";
            int scheme = rawPath.indexOf("://");
            if (scheme >= 0) {
                int slash = rawPath.indexOf('/', scheme + 3);
                rawPath = slash >= 0 ? rawPath.substring(slash) : "/";
            }
            return rawPath.length() == 0 ? "/" : rawPath;
        }

        private String queryValue(String rawPath, String key) {
            int q = rawPath.indexOf('?');
            if (q < 0 || q + 1 >= rawPath.length()) return "";
            String[] parts = rawPath.substring(q + 1).split("&");
            for (String part : parts) {
                int eq = part.indexOf('=');
                String name = eq >= 0 ? part.substring(0, eq) : part;
                if (!key.equals(name)) continue;
                try {
                    return URLDecoder.decode(eq >= 0 ? part.substring(eq + 1) : "", "UTF-8");
                } catch (Exception ignored) {
                    return "";
                }
            }
            return "";
        }

        private boolean shouldClose(String[] lines) {
            for (String line : lines) {
                String lower = line.toLowerCase(Locale.US);
                if (lower.startsWith("connection:")) {
                    return lower.indexOf("close") >= 0;
                }
            }
            return false;
        }

        private boolean isDavMethod(String method) {
            return "OPTIONS".equals(method) || "PROPFIND".equals(method) || "PROPPATCH".equals(method)
                    || "PUT".equals(method) || "MKCOL".equals(method) || "DELETE".equals(method)
                    || "MOVE".equals(method) || "COPY".equals(method) || "LOCK".equals(method)
                    || "UNLOCK".equals(method);
        }

        private String connectionHeader() {
            return "Connection: close";
        }

        private String listPage(String rawPath, File dir) throws Exception {
            File[] files = dir.listFiles();
            List<File> list = new ArrayList<File>();
            if (files != null) {
                for (File file : files) {
                    if (!file.getName().startsWith(".")) list.add(file);
                }
            }
            Collections.sort(list, new Comparator<File>() {
                @Override public int compare(File a, File b) {
                    if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                }
            });
            String path = rawPath.split("\\?", 2)[0];
            long total = root.getTotalSpace();
            long free = root.getFreeSpace();
            long used = Math.max(0, total - free);
            int percent = total <= 0 ? 0 : (int) Math.min(100, (used * 100) / total);
            String ip = getIpAddress();
            String trashRows = trashRows();
            StringBuilder moveOptions = new StringBuilder();
            moveOptions.append("<option value=''>选择当前文件夹下的目录</option>");
            for (File file : list) {
                if (file.isDirectory()) {
                    String name = file.getName();
                    moveOptions.append("<option value='").append(escapeAttr(name)).append("/'>").append(escape(name)).append("/</option>");
                }
            }
            StringBuilder rows = new StringBuilder();
            if (!"/".equals(path)) {
                rows.append("<a class='file-item' href='../' data-type='folder' data-name='..'>")
                        .append("<div class='file-icon-wrap folder'>↩</div><div class='file-details'>")
                        .append("<div class='file-item-name'>返回上级</div><div class='file-meta'><span>Parent folder</span></div>")
                        .append("</div></a>");
            }
            for (File file : list) {
                String name = file.getName();
                String href = URLEncoder.encode(name, "UTF-8").replace("+", "%20") + (file.isDirectory() ? "/" : "");
                String escapedName = escape(name);
                String type = typeClass(file);
                String fileFormat = fileFormat(file);
                boolean previewable = canPreview(file);
                rows.append("<div class='file-item' data-type='").append(type).append("' data-name='").append(escapeAttr(name.toLowerCase(Locale.US))).append("' data-size='").append(file.isDirectory() ? -1 : file.length()).append("' data-time='").append(file.lastModified()).append("' data-format='").append(escapeAttr(fileFormat.toLowerCase(Locale.US))).append("' data-dir='").append(file.isDirectory() ? "1" : "0").append("'>")
                        .append("<input class='select-box' type='checkbox' value='").append(escapeAttr(name)).append("' onchange='updateSelection()'>")
                        .append("<a class='file-open' href='").append(href).append("'").append(file.isDirectory() ? "" : " onclick=\"openPreviewDirect(event,'" + escapeJs(name) + "','" + escapeJs(href) + "')\"").append("><div class='file-icon-wrap ").append(type).append("'>").append(iconFor(file, href)).append("</div>")
                        .append("<div class='file-details'><div class='file-item-name'>").append(escapedName).append(file.isDirectory() ? "/" : "").append("</div>")
                        .append("<div class='file-meta'><span>文件大小: ").append(file.isDirectory() ? "--" : formatSize(file.length())).append("</span><span>·</span><span>文件格式: ")
                        .append(fileFormat).append("</span><span>·</span><span>").append(modifiedDate(file)).append("</span></div></div></a>")
                        .append("<div class='file-actions'><span class='star-marker' data-star-name='").append(escapeAttr(name)).append("'>★</span><button class='action-btn more-btn' onclick=\"openFileMenu(event,'")
                        .append(escapeJs(name)).append("','").append(escapeJs(href)).append("',").append(previewable ? "true" : "false").append(")\" title='更多'>···</button></div>")
                        .append("</div>");
            }
            if (rows.length() == 0) {
                rows.append("<div class='empty'><div class='empty-icon'>◇</div>这个文件夹是空的</div>");
            }
            return "<!doctype html><html lang='zh-CN'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>Pocket Drive</title>"
                    + "<style>:root{--bg:#080c14;--surface:#0d1320;--surface2:#111827;--border:rgba(99,179,237,.12);--accent:#38bdf8;--accent2:#818cf8;--success:#34d399;--danger:#f87171;--warn:#fbbf24;--text:#e2e8f0;--muted:#64748b;--card-glow:0 0 0 1px rgba(56,189,248,.1),0 8px 32px rgba(0,0,0,.5)}*{box-sizing:border-box;margin:0;padding:0}body{font-family:system-ui,-apple-system,'Noto Sans SC',sans-serif;background:var(--bg);color:var(--text);min-height:100vh;overflow-x:hidden}body:before{content:'';position:fixed;inset:0;background-image:linear-gradient(rgba(56,189,248,.03) 1px,transparent 1px),linear-gradient(90deg,rgba(56,189,248,.03) 1px,transparent 1px);background-size:40px 40px;pointer-events:none}.page{display:none;position:relative;z-index:1;animation:fadeUp .3s ease both}.page.active{display:block}@keyframes fadeUp{from{opacity:0;transform:translateY(10px)}to{opacity:1;transform:translateY(0)}}.container{max-width:480px;margin:0 auto;padding:0 0 86px;position:relative}.header{padding:24px 20px 16px;display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.header-actions{display:flex;flex-direction:column;align-items:flex-end;gap:8px}.logo{font-family:monospace;font-size:26px;font-weight:700;background:linear-gradient(135deg,var(--accent),var(--accent2));-webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text}.logo-sub{font-size:11px;color:var(--muted);letter-spacing:2px;text-transform:uppercase;margin-top:4px}.badge{display:flex;align-items:center;gap:6px;padding:6px 14px;border-radius:20px;background:rgba(52,211,153,.1);border:1px solid rgba(52,211,153,.3);font-size:12px;color:var(--success);font-family:monospace;white-space:nowrap}.mode-pill{padding:5px 12px;border-radius:999px;background:rgba(56,189,248,.1);border:1px solid rgba(56,189,248,.28);color:var(--accent);font-size:11px;font-family:monospace;white-space:nowrap}.dot{width:6px;height:6px;border-radius:50%;background:var(--success);box-shadow:0 0 6px var(--success);animation:pulse 2s infinite}@keyframes pulse{50%{opacity:.5;transform:scale(.8)}}.stats-bar{margin:0 16px 16px;background:var(--surface);border:1px solid var(--border);border-radius:16px;padding:16px 20px;display:grid;grid-template-columns:repeat(3,1fr);gap:8px;box-shadow:var(--card-glow)}.stat{text-align:center}.stat-val{font-family:monospace;font-size:16px;font-weight:700;color:var(--accent)}.stat-label{font-size:10px;color:var(--muted);margin-top:2px;letter-spacing:1px;text-transform:uppercase}.storage-wrap,.card,.folder-card,.server-card{margin:0 16px 14px;background:var(--surface);border:1px solid var(--border);border-radius:16px;padding:16px;box-shadow:var(--card-glow)}.storage-header{display:flex;justify-content:space-between;font-size:12px;margin-bottom:10px}.storage-header span:first-child{color:var(--muted)}.storage-header span:last-child{color:var(--accent);font-family:monospace}.storage-track{height:6px;background:rgba(255,255,255,.06);border-radius:3px;overflow:hidden}.storage-fill{height:100%;background:linear-gradient(90deg,var(--accent),var(--accent2));border-radius:3px;box-shadow:0 0 8px rgba(56,189,248,.5)}.card-title{font-size:11px;color:var(--muted);letter-spacing:1.5px;text-transform:uppercase;margin-bottom:12px;font-family:monospace}.drop-zone{border:1.5px dashed rgba(56,189,248,.25);border-radius:12px;padding:20px;text-align:center;cursor:pointer;transition:all .2s;position:relative;overflow:hidden;margin-bottom:12px}.drop-zone:hover,.drop-zone.drag-over{border-color:var(--accent);background:rgba(56,189,248,.05)}.drop-zone input[type=file]{position:absolute;inset:0;opacity:0;cursor:pointer;width:100%;height:100%}.drop-icon{font-size:28px;margin-bottom:6px;display:block}.drop-text{font-size:13px;color:var(--muted)}.drop-text strong{color:var(--accent)}.progress{height:5px;background:rgba(255,255,255,.06);border-radius:3px;overflow:hidden;margin:10px 0 8px}.progress span{display:block;height:100%;width:0;background:linear-gradient(90deg,var(--accent),var(--success));transition:width .2s}.upload-queue{display:flex;flex-direction:column;gap:8px;margin-top:10px}.upload-row{background:rgba(255,255,255,.04);border:1px solid var(--border);border-radius:10px;padding:10px}.upload-row-top{display:flex;align-items:center;gap:8px}.upload-name{flex:1;min-width:0;font-size:12px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.upload-state{font-size:11px;color:var(--muted);white-space:nowrap}.upload-row.ok .upload-state{color:var(--success)}.upload-row.fail .upload-state{color:var(--danger)}.upload-bar{height:4px;background:rgba(255,255,255,.08);border-radius:2px;overflow:hidden;margin-top:8px}.upload-bar span{display:block;height:100%;width:0;background:linear-gradient(90deg,var(--accent),var(--success))}.retry-btn{margin-top:8px;width:auto;padding:7px 10px;border:0;border-radius:8px;background:rgba(248,113,113,.15);color:var(--danger);font-size:12px}.btn{display:flex;align-items:center;justify-content:center;gap:8px;width:100%;padding:12px;border-radius:10px;border:0;font-size:14px;font-weight:500;cursor:pointer;transition:all .2s;font-family:inherit}.btn-primary{background:linear-gradient(135deg,var(--accent),var(--accent2));color:#fff;box-shadow:0 4px 15px rgba(56,189,248,.3)}.btn-ghost{background:rgba(255,255,255,.06);color:var(--text);border:1px solid var(--border)}.btn-sm{padding:10px 16px;font-size:13px;width:auto;flex-shrink:0}.input{background:rgba(255,255,255,.05);border:1px solid var(--border);border-radius:10px;padding:10px 14px;color:var(--text);font-size:14px;outline:0;font-family:inherit;width:100%;min-width:0}.input:focus{border-color:var(--accent)}.folder-card{overflow:hidden}.folder-card-body{display:flex;flex-direction:column;gap:12px}.folder-icon-grid{display:flex;gap:8px;flex-wrap:wrap}.folder-icon-opt{width:38px;height:38px;border-radius:10px;border:1.5px solid var(--border);background:rgba(255,255,255,.04);display:flex;align-items:center;justify-content:center;font-size:18px;cursor:pointer}.folder-icon-opt.selected{border-color:var(--accent);background:rgba(56,189,248,.15);box-shadow:0 0 0 2px rgba(56,189,248,.2)}.folder-color-row{display:flex;gap:6px}.color-dot{width:22px;height:22px;border-radius:50%;cursor:pointer;border:2px solid transparent}.color-dot.selected{border-color:#fff;box-shadow:0 0 0 2px rgba(255,255,255,.3)}.folder-create-row{display:flex;gap:8px}.folder-preview{width:38px;height:38px;border-radius:10px;display:flex;align-items:center;justify-content:center;font-size:20px;flex-shrink:0;background:rgba(251,191,36,.15);border:1px solid rgba(251,191,36,.3)}.section-header{display:flex;justify-content:space-between;align-items:center;padding:0 20px;margin:4px 0 10px}.section-title,.settings-section-title,.fav-section-title{font-size:11px;color:var(--muted);letter-spacing:1.5px;text-transform:uppercase;font-family:monospace}.toolbar-actions{display:flex;align-items:center;gap:6px}.view-toggle{display:flex;gap:4px;background:rgba(255,255,255,.05);border-radius:8px;padding:3px}.view-btn{width:26px;height:26px;border:0;background:none;border-radius:6px;color:var(--muted)}.view-btn.active{background:rgba(56,189,248,.15);color:var(--accent)}.sort-btn{width:32px;height:32px;border:0;background:rgba(255,255,255,.05);border-radius:8px;color:var(--muted);font-size:15px}.sort-btn.active{background:rgba(56,189,248,.15);color:var(--accent)}.filter-bar{display:flex;gap:8px;padding:0 16px;margin-bottom:10px;overflow-x:auto}.filter-chip{padding:5px 12px;border-radius:20px;border:1px solid var(--border);background:none;color:var(--muted);font-size:12px;white-space:nowrap}.filter-chip.active{border-color:var(--accent);color:var(--accent);background:rgba(56,189,248,.1)}.search-wrap{padding:0 16px;margin-bottom:12px;position:relative}.search-icon{position:absolute;left:28px;top:50%;transform:translateY(-50%);color:var(--muted);font-size:14px}.search-input{width:100%;background:var(--surface);border:1px solid var(--border);border-radius:10px;padding:10px 14px 10px 36px;color:var(--text);font-size:13px;outline:0}.file-list{padding:0 16px;display:flex;flex-direction:column;gap:8px}.file-list.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));align-items:start}.file-list.grid .file-item{flex-direction:column;align-items:flex-start;min-height:154px}.file-list.grid .file-open{width:100%;align-items:flex-start}.file-list.grid .file-actions{width:100%;justify-content:flex-end}.file-list.grid .file-icon-wrap{width:52px;height:52px;font-size:24px}.file-item{background:var(--surface);border:1px solid var(--border);border-radius:14px;padding:14px 16px;display:flex;align-items:center;gap:12px;box-shadow:var(--card-glow);transition:all .2s;position:relative;overflow:hidden;color:var(--text)}.file-item:before{content:'';position:absolute;left:0;top:0;bottom:0;width:3px;background:transparent}.file-item:hover{border-color:rgba(56,189,248,.25);transform:translateX(2px)}.file-item:hover:before{background:var(--accent)}.select-box,.trash-select-box{display:none;width:20px;height:20px;accent-color:#38bdf8}.selecting .select-box{display:block}.trash-select-box{display:block}.selecting .file-open{pointer-events:none}.file-open{display:flex;align-items:center;gap:12px;flex:1;min-width:0;color:inherit;text-decoration:none}.file-icon-wrap{width:44px;height:44px;border-radius:12px;display:flex;align-items:center;justify-content:center;font-size:20px;flex-shrink:0;overflow:hidden}.file-thumb{width:100%;height:100%;display:block;object-fit:cover}.folder{background:linear-gradient(135deg,rgba(251,191,36,.2),rgba(251,191,36,.05));border:1px solid rgba(251,191,36,.2);color:var(--warn)}.txt{background:linear-gradient(135deg,rgba(99,179,237,.2),rgba(99,179,237,.05));border:1px solid rgba(99,179,237,.2);color:var(--accent)}.doc{background:linear-gradient(135deg,rgba(129,140,248,.2),rgba(129,140,248,.05));border:1px solid rgba(129,140,248,.2);color:var(--accent2)}.img{background:rgba(15,23,42,.75);border:1px solid rgba(52,211,153,.25);color:var(--success)}.audio{background:linear-gradient(135deg,rgba(244,114,182,.22),rgba(244,114,182,.06));border:1px solid rgba(244,114,182,.25);color:#f9a8d4;font-size:24px;font-weight:700}.video{background:linear-gradient(135deg,rgba(248,113,113,.22),rgba(248,113,113,.06));border:1px solid rgba(248,113,113,.25);color:#fca5a5;font-size:20px}.other{background:linear-gradient(135deg,rgba(100,116,139,.2),rgba(100,116,139,.05));border:1px solid rgba(100,116,139,.2);color:var(--muted)}.file-details{flex:1;min-width:0}.file-item-name{font-size:14px;font-weight:500;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;margin-bottom:3px}.file-meta{font-size:11px;color:var(--muted);display:flex;gap:8px;align-items:center;flex-wrap:wrap}.file-actions{display:flex;gap:4px;transition:opacity .2s}.action-btn{width:30px;height:30px;border:0;background:rgba(255,255,255,.06);border-radius:8px;color:var(--muted);cursor:pointer;display:flex;align-items:center;justify-content:center;font-size:14px;text-decoration:none}.action-btn:hover{background:rgba(56,189,248,.15);color:var(--accent)}.more-btn{font-size:18px;letter-spacing:1px}.star-marker{display:none;align-items:center;justify-content:center;width:22px;height:30px;color:var(--warn);font-size:15px}.menu-list{display:flex;flex-direction:column;gap:8px}.menu-action{width:100%;padding:12px 14px;border:0;border-radius:12px;background:rgba(255,255,255,.06);color:var(--text);display:flex;align-items:center;gap:10px;font-size:14px;text-decoration:none;font-family:inherit}.menu-action.danger{color:var(--danger);background:rgba(248,113,113,.08)}.menu-action.starred{color:var(--warn)}.action-btn.danger:hover{background:rgba(248,113,113,.15);color:var(--danger)}.mini-form{display:block}.tabbar{position:fixed;bottom:0;left:50%;transform:translateX(-50%);width:100%;max-width:480px;background:rgba(13,19,32,.95);backdrop-filter:blur(16px);border-top:1px solid var(--border);display:flex;justify-content:space-around;padding:10px 0 14px;z-index:50}.tab{display:flex;flex-direction:column;align-items:center;gap:3px;padding:4px 16px;border-radius:10px;color:var(--muted);font-size:10px;letter-spacing:.5px;cursor:pointer}.tab.active{color:var(--accent)}.tab-icon{font-size:18px}.modal-overlay{position:fixed;inset:0;background:rgba(0,0,0,.7);z-index:150;display:none;align-items:flex-end;justify-content:center}.modal-overlay.show{display:flex}.modal{background:var(--surface2);border:1px solid rgba(255,255,255,.1);border-radius:20px 20px 0 0;padding:20px;width:100%;max-width:480px}.modal h3{font-size:16px;margin-bottom:14px}.modal-btns{display:flex;gap:8px;margin-top:14px}.empty{text-align:center;padding:40px 20px;color:var(--muted);font-size:13px}.empty-icon{font-size:36px;margin-bottom:8px}#uploadText{color:var(--muted);font-size:12px}.server-line{margin:0 16px 14px;color:var(--muted);font-size:12px;overflow-wrap:anywhere}.qr-wrap{display:flex;align-items:center;gap:14px}.qr-img{width:132px;height:132px;border-radius:10px;background:#fff;padding:8px;flex-shrink:0}.qr-url{font-size:12px;color:var(--muted);line-height:1.5;overflow-wrap:anywhere}.settings-section,.fav-section{margin:0 16px 20px}.settings-section-title,.fav-section-title{margin-bottom:10px;padding:0 4px}.settings-group{background:var(--surface);border:1px solid var(--border);border-radius:16px;overflow:hidden}.settings-item{display:flex;align-items:center;gap:12px;padding:14px 16px;border-bottom:1px solid var(--border)}.settings-item:last-child{border-bottom:0}.settings-item-icon{width:36px;height:36px;border-radius:10px;display:flex;align-items:center;justify-content:center;font-size:16px;background:rgba(56,189,248,.15)}.settings-item-body{flex:1}.settings-item-label{font-size:14px;font-weight:500}.settings-item-desc{font-size:11px;color:var(--muted);margin-top:2px}.settings-item-right,.server-val{color:var(--accent);font-family:monospace;font-size:12px}.toggle{width:40px;height:22px;border-radius:11px;background:rgba(255,255,255,.1);border:0;position:relative}.toggle.on{background:linear-gradient(135deg,var(--accent),var(--accent2))}.toggle:after{content:'';position:absolute;width:16px;height:16px;border-radius:50%;background:#fff;top:3px;left:3px;transition:transform .2s}.toggle.on:after{transform:translateX(18px)}.server-row{display:flex;align-items:center;justify-content:space-between;margin-bottom:8px}.server-label{font-size:11px;color:var(--muted);letter-spacing:1px;text-transform:uppercase}.fav-list{display:flex;flex-direction:column;gap:8px}.fav-row{background:var(--surface);border:1px solid var(--border);border-radius:14px;padding:14px 16px;display:flex;align-items:center;gap:12px;color:var(--text);text-decoration:none}.fav-row-icon{width:40px;height:40px;border-radius:12px;display:flex;align-items:center;justify-content:center;background:rgba(251,191,36,.15);border:1px solid rgba(251,191,36,.25);color:var(--warn);font-size:18px;flex-shrink:0}.fav-row-body{flex:1;min-width:0}.fav-row-name{font-size:14px;font-weight:500;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.fav-row-meta{font-size:11px;color:var(--muted);margin-top:3px}.toast-wrap{position:fixed;bottom:80px;left:50%;transform:translateX(-50%);z-index:200}.toast{padding:10px 18px;border-radius:20px;font-size:13px;background:var(--surface2);border:1px solid var(--border);box-shadow:0 4px 20px rgba(0,0,0,.5);white-space:nowrap}@media(min-width:860px){.container{max-width:1100px}.tabbar{max-width:1100px}.stats-bar{grid-template-columns:repeat(3,1fr)}.modal{border-radius:20px;max-width:560px}.modal-overlay{align-items:center}}@media(max-width:420px){.file-actions{opacity:1}.header{padding-top:20px}.stats-bar{padding:14px 12px}.file-item{padding:12px}.action-btn{width:28px;height:28px}}</style></head><body><div class='page active' id='page-files'><div class='container'>"
                    + "<div class='header'><div><div class='logo'>Pocket Drive</div><div class='logo-sub'>" + escape(ip.length() == 0 ? "Pocket Drive" : ip + " · 本地存储") + "</div></div><div class='header-actions'><div class='badge'><div class='dot'></div>已连接</div><div class='mode-pill' id='modePill'>手机端</div></div></div>"
                    + "<div class='stats-bar'><div class='stat'><div class='stat-val' id='statFiles'>" + list.size() + "</div><div class='stat-label'>项目</div></div><div class='stat'><div class='stat-val'>" + formatSize(total) + "</div><div class='stat-label'>总容量</div></div><div class='stat'><div class='stat-val'>" + formatSize(free) + "</div><div class='stat-label'>剩余</div></div></div>"
                    + "<div class='storage-wrap'><div class='storage-header'><span>磁盘使用</span><span>" + formatSize(used) + " / " + formatSize(total) + "</span></div><div class='storage-track'><div class='storage-fill' style='width:" + percent + "%'></div></div></div>"
                    + uploadCard()
                    + "<div class='folder-card'><div class='card-title'>新建文件夹</div><form method='post' class='folder-create-row'><div class='folder-preview' id='folderPreview'>📁</div><input class='input' name='dirname' placeholder='文件夹名称…'><button class='btn btn-primary btn-sm' name='action' value='mkdir'>创建</button></form></div>"
                    + "<div class='section-header'><div class='section-title'>文件列表 · " + escape(path) + "</div><div class='toolbar-actions'><button class='sort-btn' id='sortBtn' onclick='openSortMenu()' title='排序'>↕</button><div class='view-toggle'><button class='view-btn active' onclick=\"setViewMode('list',this)\">≡</button><button class='view-btn' onclick=\"setViewMode('grid',this)\">⊞</button></div></div></div>"
                    + "<div class='filter-bar'><button class='filter-chip active' onclick=\"filterFiles('all',this)\">全部</button><button class='filter-chip' onclick=\"filterFiles('folder',this)\">文件夹</button><button class='filter-chip' onclick=\"filterFiles('doc',this)\">文档</button><button class='filter-chip' onclick=\"filterFiles('img',this)\">图片</button><button class='filter-chip' onclick=\"filterFiles('audio',this)\">音乐</button><button class='filter-chip' onclick=\"filterFiles('video',this)\">视频</button><button class='filter-chip' onclick=\"filterFiles('other',this)\">其他</button></div>"
                    + "<div class='search-wrap'><span class='search-icon'>⌕</span><input class='search-input' type='text' placeholder='搜索文件…' oninput='searchFiles(this.value)'></div><div class='card' id='bulkBar' style='display:none'><div class='card-title'><span id='selectedCount'>已选择 0 项</span></div><form method='post' id='bulkForm'><input type='hidden' name='targets' id='bulkTargets'><select class='input' name='dest' id='bulkDest'><option value=''>移动到...</option>" + moveOptions + "</select><div class='modal-btns'><button class='btn btn-ghost' name='action' value='bulkMove' onclick='return prepareBulkMove()'>移动</button><button class='btn btn-ghost' name='action' value='bulkDelete' onclick='return prepareBulkDelete()'>删除</button><button type='button' class='btn btn-primary' onclick='exitSelectMode()'>取消</button></div></form></div><div class='file-list' id='fileList'>" + rows + "</div>"
                    + "<div class='server-line'>当前地址: <span id='currentUrl'>http://" + escape(ip.length() == 0 ? "手机IP" : ip) + ":" + PORT + escape(path) + "</span></div></div></div>"
                    + "<div class='page' id='page-upload'><div class='container'><div class='header'><div><div class='logo'>回收站</div><div class='logo-sub'>已删除文件</div></div></div><div class='card'><div class='card-title'>自动清理</div><form method='post' class='folder-create-row'><input type='hidden' name='action' value='trashSetting'><select class='input' name='days'><option value='7'" + (trashDays == 7 ? " selected" : "") + ">7 天后永久删除</option><option value='30'" + (trashDays == 30 ? " selected" : "") + ">30 天后永久删除</option></select><button class='btn btn-primary btn-sm'>保存</button></form></div><div class='card' id='trashBulkBar' style='display:none'><div class='card-title'><span id='trashSelectedCount'>已选择 0 项</span></div><form method='post'><input type='hidden' name='targets' id='trashTargets'><div class='modal-btns'><button class='btn btn-ghost' name='action' value='bulkRestore' onclick='return prepareTrashBulk(false)'>恢复</button><button class='btn btn-ghost' name='action' value='bulkPurge' onclick='return prepareTrashBulk(true)'>永久删除</button><button type='button' class='btn btn-primary' onclick='clearTrashSelection()'>取消</button></div></form></div><div class='section-header'><div class='section-title'>回收站文件</div></div><div class='file-list'>" + trashRows + "</div></div></div>"
                    + "<div class='page' id='page-favorites'><div class='container'><div class='header'><div><div class='logo'>收藏</div><div class='logo-sub'>快速访问</div></div><div style='font-size:11px;color:var(--muted);padding-top:8px'>本地保存</div></div><div class='fav-section'><div class='fav-section-title'>已收藏文件</div><div class='fav-list' id='favContent'></div></div></div></div>"
                    + "<div class='page' id='page-settings'><div class='container'><div class='header'><div><div class='logo'>设置</div><div class='logo-sub'>系统配置</div></div></div><div class='server-card'><div class='server-row'><span class='server-label'>服务器地址</span><span class='server-val'>" + escape(ip.length() == 0 ? "手机IP" : ip) + "</span></div><div class='server-row'><span class='server-label'>端口</span><span class='server-val'>:8080</span></div><div class='server-row'><span class='server-label'>账号</span><span class='server-val'>" + escape(authUser) + "</span></div><div class='server-row' style='margin-bottom:0'><span class='server-label'>状态</span><span style='color:var(--success);font-size:12px'>● 在线运行中</span></div></div><div class='server-card'><div class='card-title'>扫码访问</div><div class='qr-wrap'><img class='qr-img' id='qrImg' alt='二维码'><div class='qr-url' id='qrUrl'>正在生成当前地址...</div></div></div><div class='settings-section'><div class='settings-section-title'>存储</div><div class='settings-group'><div class='settings-item'><div class='settings-item-icon'>💾</div><div class='settings-item-body'><div class='settings-item-label'>存储目录</div><div class='settings-item-desc'>" + escape(root.getAbsolutePath()) + "</div></div><div class='settings-item-right'>Drive</div></div><div class='settings-item'><div class='settings-item-icon'>📊</div><div class='settings-item-body'><div class='settings-item-label'>磁盘使用</div><div class='settings-item-desc'>" + formatSize(used) + " / " + formatSize(total) + "</div></div><div class='settings-item-right'>" + percent + "%</div></div></div></div><div class='settings-section'><div class='settings-section-title'>安全</div><div class='settings-group'><form method='post' style='padding:14px 16px;display:flex;flex-direction:column;gap:10px'><input type='hidden' name='action' value='auth'><input class='input' name='user' value='" + escapeAttr(authUser) + "' placeholder='管理员账号'><input class='input' name='pass' value='" + escapeAttr(authPass) + "' placeholder='管理员密码'><button class='btn btn-primary'>保存账号密码</button></form><div class='settings-item'><div class='settings-item-icon'>🌐</div><div class='settings-item-body'><div class='settings-item-label'>WebDAV</div><div class='settings-item-desc'>支持 OPTIONS / PROPFIND / PUT / MKCOL / DELETE / MOVE</div></div><button class='toggle on'></button></div></div></div><div class='settings-section'><div class='settings-section-title'>关于</div><div class='settings-group'><div class='settings-item'><div class='settings-item-icon'>ℹ</div><div class='settings-item-body'><div class='settings-item-label'>版本</div><div class='settings-item-desc'>Pocket Drive v2.0</div></div><div class='settings-item-right'>Pocket Drive</div></div></div></div></div></div>"
                    + "<div class='tabbar'><div class='tab active' onclick=\"switchTab('files',this)\"><div class='tab-icon'>📁</div><span>文件</span></div><div class='tab' onclick=\"switchTab('upload',this)\"><div class='tab-icon'>🗑</div><span>回收站</span></div><div class='tab' onclick=\"switchTab('favorites',this)\"><div class='tab-icon'>⭐</div><span>收藏</span></div><div class='tab' onclick=\"switchTab('settings',this)\"><div class='tab-icon'>⚙</div><span>设置</span></div></div>"
                    + "<div class='modal-overlay' id='renameModal'><div class='modal'><h3>重命名</h3><form method='post'><input type='hidden' name='target' id='renameTarget'><input class='input' name='dest' id='renameInput' placeholder='新文件名…'><div class='modal-btns'><button type='button' class='btn btn-ghost' onclick='closeModal()'>取消</button><button class='btn btn-primary' name='action' value='move'>确认</button></div></form></div></div>"
                    + "<div class='modal-overlay' id='moveModal'><div class='modal'><h3>移动到文件夹</h3><form method='post'><input type='hidden' name='target' id='moveTarget'><select class='input' id='moveFolder'>" + moveOptions + "</select><input type='hidden' name='dest' id='moveDest'><div class='modal-btns'><button type='button' class='btn btn-ghost' onclick='closeMoveModal()'>取消</button><button class='btn btn-primary' name='action' value='move' onclick='return setMoveDest()'>移动</button></div></form></div></div>"
                    + "<div class='modal-overlay' id='sortMenu'><div class='modal'><h3>文件排序</h3><div class='menu-list'><button class='menu-action' onclick='chooseSortMode(&quot;name&quot;)'>↕ 名称</button><button class='menu-action' onclick='chooseSortMode(&quot;size&quot;)'>↕ 大小</button><button class='menu-action' onclick='chooseSortMode(&quot;time&quot;)'>↕ 时间</button><button class='menu-action' onclick='chooseSortMode(&quot;type&quot;)'>↕ 类型</button><button class='menu-action' onclick='closeSortMenu()'>取消</button></div></div></div><div class='modal-overlay' id='fileMenu'><div class='modal'><h3 id='menuTitle'>文件操作</h3><div class='menu-list'><button class='menu-action' id='menuPreview' onclick='menuPreview()'>👁 预览</button><a class='menu-action' id='menuDownload' href='#'>⬇ 下载</a><button class='menu-action' onclick='menuRename()'>✎ 重命名</button><button class='menu-action' onclick='menuMove()'>↪ 移动</button><button class='menu-action' onclick='enterSelectMode()'>☑ 选择</button><button class='menu-action' id='menuStar' onclick='menuStar()'>⭐ 收藏</button><form method='post' id='menuDeleteForm'><input type='hidden' name='target' id='menuDeleteTarget'><button class='menu-action danger' name='action' value='delete'>× 删除</button></form><button class='menu-action' onclick='closeFileMenu()'>取消</button></div></div></div>"
                    + "<div class='modal-overlay' id='previewModal' style='align-items:center;overflow:hidden'><div class='modal' id='previewBox' style='width:calc(100vw - 32px);height:72vh;max-height:620px;max-width:760px;border-radius:20px;margin:16px;display:flex;flex-direction:column;overflow:hidden'><div style='display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:14px;flex-shrink:0'><h3 id='previewTitle' style='margin:0;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap'>预览</h3><button type='button' onclick='closePreview()' title='关闭' style='width:34px;height:34px;border:0;border-radius:9px;background:rgba(255,255,255,.08);color:var(--text);font-size:22px;line-height:1;cursor:pointer;flex-shrink:0'>×</button></div><iframe id='previewFrame' scrolling='no' style='width:100%;flex:1;min-height:0;border:0;border-radius:12px;background:white;display:block'></iframe></div></div>"
                    + "<div class='toast-wrap' id='toastWrap'></div><script>var currentMenuName='',currentMenuHref='',currentCanPreview=false,isUploading=false;window.addEventListener('beforeunload',function(e){if(isUploading){e.preventDefault();e.returnValue='正在上传未完成';return '正在上传未完成';}});function hist(type,name){var a=JSON.parse(localStorage.getItem('pocketdrive-history')||'[]');a.unshift({type:type,name:name,time:new Date().toLocaleString()});localStorage.setItem('pocketdrive-history',JSON.stringify(a.slice(0,50)));renderHistory();}function renderHistory(){var el=document.getElementById('transferHistory');if(!el)return;var a=JSON.parse(localStorage.getItem('pocketdrive-history')||'[]');el.innerHTML=a.length?a.map(function(r){return '<div style=\"display:flex;align-items:center;gap:10px;padding:8px 0;border-bottom:1px solid var(--border)\"><div style=\"font-size:20px\">'+(r.type==='up'?'⬆':'⬇')+'</div><div style=\"flex:1;min-width:0\"><div style=\"font-size:13px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis\">'+esc(r.name)+'</div><div style=\"font-size:11px;color:var(--muted)\">'+esc(r.time)+'</div></div><div style=\"font-size:11px;color:var(--success)\">'+(r.type==='up'?'上传':'下载')+'</div></div>';}).join(''):'<div class=\"empty\"><div class=\"empty-icon\">◇</div>暂无历史</div>';}function wireUpload(card){var f=card.querySelector('form'),bar=card.querySelector('.progress span'),txt=card.querySelector('.uploadText'),inp=card.querySelector('input[type=file]'),dz=card.querySelector('.drop-zone'),queue=card.querySelector('.upload-queue');f.onsubmit=function(e){e.preventDefault();var files=Array.prototype.slice.call(inp.files);if(!files.length){txt.textContent='请选择文件';return;}queue.innerHTML='';bar.style.width='0%';uploadQueue(files,0,bar,txt,queue);};dz.addEventListener('dragover',function(e){e.preventDefault();dz.classList.add('drag-over')});dz.addEventListener('dragleave',function(){dz.classList.remove('drag-over')});dz.addEventListener('drop',function(e){e.preventDefault();dz.classList.remove('drag-over');inp.files=e.dataTransfer.files;txt.textContent='已选择 '+inp.files.length+' 个文件';});inp.addEventListener('change',function(){txt.textContent=inp.files.length?'已选择 '+inp.files.length+' 个文件':'等待上传';});}function uploadQueue(files,i,bar,txt,queue){isUploading=true;if(i>=files.length){isUploading=false;txt.textContent='队列完成，正在刷新';setTimeout(function(){location.reload()},800);return;}uploadOne(files[i],bar,txt,queue,i,files.length,function(ok){if(ok)uploadQueue(files,i+1,bar,txt,queue);else{isUploading=false;txt.textContent='有文件上传失败，可单独重试';}});}function uploadOne(file,bar,txt,queue,i,total,next){var row=document.createElement('div');row.className='upload-row';row.innerHTML='<div class=\"upload-row-top\"><div class=\"upload-name\"></div><div class=\"upload-state\">等待</div></div><div class=\"upload-bar\"><span></span></div>';row.querySelector('.upload-name').textContent=file.name;queue.appendChild(row);var state=row.querySelector('.upload-state'),oneBar=row.querySelector('.upload-bar span'),fd=new FormData(),xhr=new XMLHttpRequest(),done=false,stallTimer=null;function armStall(){clearTimeout(stallTimer);stallTimer=setTimeout(function(){try{xhr.abort();}catch(e){}fail();},45000);}function finish(ok){if(done)return;done=true;clearTimeout(stallTimer);next(ok);}fd.append('file',file);fd.append('action','upload');xhr.open('POST',location.pathname);xhr.upload.onprogress=function(ev){armStall();if(ev.lengthComputable){var pct=Math.round(ev.loaded*100/ev.total);oneBar.style.width=pct+'%';bar.style.width=Math.round(((i+ev.loaded/ev.total)/total)*100)+'%';state.textContent=pct+'%';txt.textContent='上传 '+(i+1)+'/'+total+' · '+file.name+' · '+pct+'%';}};xhr.onload=function(){if(xhr.status>=200&&xhr.status<400){row.classList.add('ok');oneBar.style.width='100%';state.textContent='完成';hist('up',file.name);finish(true);}else fail();};xhr.onerror=fail;xhr.ontimeout=fail;xhr.onabort=fail;function fail(){if(done)return;row.classList.add('fail');state.textContent='失败';txt.textContent='有文件上传失败，可单独重试';var b=document.createElement('button');b.type='button';b.className='retry-btn';b.textContent='重试';b.onclick=function(){row.remove();uploadOne(file,bar,txt,queue,i,total,next);};row.appendChild(b);finish(false);}armStall();xhr.send(fd);}document.querySelectorAll('.upload-card').forEach(wireUpload);function switchTab(name,el){document.querySelectorAll('.page').forEach(function(p){p.classList.remove('active')});document.querySelectorAll('.tab').forEach(function(t){t.classList.remove('active')});document.getElementById('page-'+name).classList.add('active');el.classList.add('active');if(name==='favorites')renderFavs();if(name==='upload')renderHistory();window.scrollTo(0,0);}function filterFiles(type,btn){document.querySelectorAll('.filter-chip').forEach(function(c){c.classList.remove('active')});btn.classList.add('active');document.querySelectorAll('.file-item').forEach(function(it){it.style.display=(type==='all'||it.dataset.type===type)?'flex':'none'});}function searchFiles(q){q=q.toLowerCase();document.querySelectorAll('.file-item').forEach(function(it){it.style.display=it.dataset.name.indexOf(q)>=0?'flex':'none'});}function enterSelectMode(){closeFileMenu();document.body.classList.add('selecting');document.querySelectorAll('.select-box').forEach(function(b){if(b.value===currentMenuName)b.checked=true;});updateSelection();}function exitSelectMode(){document.body.classList.remove('selecting');document.querySelectorAll('.select-box').forEach(function(b){b.checked=false});updateSelection();}function selectedNames(){return Array.prototype.slice.call(document.querySelectorAll('.select-box:checked')).map(function(b){return b.value;});}function trashSelectedNames(){return Array.prototype.slice.call(document.querySelectorAll('.trash-select-box:checked')).map(function(b){return b.value;});}function updateTrashSelection(){var a=trashSelectedNames(),bar=document.getElementById('trashBulkBar');if(!bar)return;document.getElementById('trashSelectedCount').textContent='已选择 '+a.length+' 项';bar.style.display=a.length?'block':'none';}function clearTrashSelection(){document.querySelectorAll('.trash-select-box').forEach(function(b){b.checked=false});updateTrashSelection();}function prepareTrashBulk(purge){var a=trashSelectedNames();if(!a.length){toast('请选择文件');return false;}document.getElementById('trashTargets').value=a.join('|');return purge?confirm('永久删除选中的 '+a.length+' 项 ?'):true;}function updateSelection(){var a=selectedNames(),bar=document.getElementById('bulkBar');document.getElementById('selectedCount').textContent='已选择 '+a.length+' 项';bar.style.display=a.length?'block':'none';}function prepareBulkDelete(){var a=selectedNames();if(!a.length){toast('请选择文件');return false;}document.getElementById('bulkTargets').value=a.join('|');return confirm('删除选中的 '+a.length+' 项 ?');}function prepareBulkMove(){var a=selectedNames(),d=document.getElementById('bulkDest').value;if(!a.length){toast('请选择文件');return false;}if(!d){toast('请选择目标文件夹');return false;}document.getElementById('bulkTargets').value=a.join('|');return true;}function openRename(name){document.getElementById('renameTarget').value=name;document.getElementById('renameInput').value=name;document.getElementById('renameModal').classList.add('show');}function closeModal(){document.getElementById('renameModal').classList.remove('show');}function closeMoveModal(){document.getElementById('moveModal').classList.remove('show');}function menuMove(){closeFileMenu();document.getElementById('moveTarget').value=currentMenuName;document.getElementById('moveModal').classList.add('show');}function setMoveDest(){var f=document.getElementById('moveFolder').value;if(!f){toast('请选择文件夹');return false;}document.getElementById('moveDest').value=f+currentMenuName;return true;}function openFileMenu(e,name,href,canPreview){e.preventDefault();e.stopPropagation();currentMenuName=name;currentMenuHref=href;currentCanPreview=!!canPreview;document.getElementById('menuTitle').textContent=name;document.getElementById('menuPreview').style.display=currentCanPreview?'flex':'none';document.getElementById('menuDownload').href=href;document.getElementById('menuDownload').onclick=function(){hist('down',name);};document.getElementById('menuDeleteTarget').value=name;document.getElementById('menuStar').className=isStarred(name)?'menu-action starred':'menu-action';document.getElementById('menuStar').textContent=isStarred(name)?'★ 已收藏':'⭐ 收藏';document.getElementById('fileMenu').classList.add('show');}function closeFileMenu(){document.getElementById('fileMenu').classList.remove('show');}function showPreview(name,href){var box=document.getElementById('previewBox'),frame=document.getElementById('previewFrame'),audio=/\\.(mp3|wav|ogg|m4a|aac|flac)$/i.test(name);box.style.height=audio?'170px':'72vh';box.style.maxHeight=audio?'190px':'620px';box.style.maxWidth=audio?'420px':'760px';document.getElementById('previewTitle').textContent=name;frame.src=href+(href.indexOf('?')>=0?'&':'?')+'preview=1';document.getElementById('previewModal').classList.add('show');}function openPreviewDirect(e,name,href){e.preventDefault();e.stopPropagation();showPreview(name,href);}function menuPreview(){closeFileMenu();showPreview(currentMenuName,currentMenuHref);}function closePreview(){document.getElementById('previewFrame').src='about:blank';document.getElementById('previewModal').classList.remove('show');}function menuRename(){closeFileMenu();openRename(currentMenuName);}function starKey(name){return 'pocketdrive-star-'+location.pathname+'-'+name;}function isStarred(name){return localStorage.getItem(starKey(name))==='1';}function menuStar(){if(isStarred(currentMenuName)){localStorage.removeItem(starKey(currentMenuName));localStorage.removeItem(starKey(currentMenuName)+'-href');toast('已取消收藏');}else{localStorage.setItem(starKey(currentMenuName),'1');localStorage.setItem(starKey(currentMenuName)+'-href',currentMenuHref);toast('已收藏');}renderFavs();markStars();closeFileMenu();}function markStars(){document.querySelectorAll('.star-marker').forEach(function(e){e.style.display=isStarred(e.dataset.starName)?'inline-flex':'none';});}function renderFavs(){var el=document.getElementById('favContent');if(!el)return;var html='';for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);if(k.indexOf('pocketdrive-star-'+location.pathname+'-')===0&&k.indexOf('-href')<0&&localStorage.getItem(k)==='1'){var name=k.substring(('pocketdrive-star-'+location.pathname+'-').length),href=localStorage.getItem(k+'-href')||'#';html+='<a class=\"fav-row\" href=\"'+href+'\"><div class=\"fav-row-icon\">⭐</div><div class=\"fav-row-body\"><div class=\"fav-row-name\">'+esc(name)+'</div><div class=\"fav-row-meta\">点击打开</div></div></a>';}}el.innerHTML=html||'<div class=\"empty\"><div class=\"empty-icon\">⭐</div>还没有收藏</div>';}document.getElementById('menuDeleteForm').onsubmit=function(){return confirm('删除 '+currentMenuName+' ?');};document.querySelectorAll('.toggle').forEach(function(t){t.onclick=function(){t.classList.toggle('on')}});function esc(s){return String(s).replace(/[&<>]/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;'}[c];});}function toast(msg){var w=document.getElementById('toastWrap'),e=document.createElement('div');e.className='toast';e.textContent=msg;w.appendChild(e);setTimeout(function(){e.remove()},2400);}function setViewMode(mode,btn){var list=document.getElementById('fileList');if(!list)return;list.classList.toggle('grid',mode==='grid');document.querySelectorAll('.view-btn').forEach(function(b){b.classList.remove('active')});if(btn)btn.classList.add('active');localStorage.setItem('pocketdrive-view',mode);}function restoreViewMode(){var mode=localStorage.getItem('pocketdrive-view')||'list',btns=document.querySelectorAll('.view-btn');setViewMode(mode,mode==='grid'?btns[1]:btns[0]);}function sortLabel(mode){return mode==='size'?'大小':mode==='time'?'时间':mode==='type'?'类型':'名称';}function openSortMenu(){document.getElementById('sortMenu').classList.add('show');}function closeSortMenu(){document.getElementById('sortMenu').classList.remove('show');}function chooseSortMode(mode){setSortMode(mode);closeSortMenu();}function setSortMode(mode){localStorage.setItem('pocketdrive-sort',mode);var b=document.getElementById('sortBtn');if(b){b.textContent='↕';b.title='排序: '+sortLabel(mode);b.classList.add('active');}sortFiles(mode);}function sortFiles(mode){var list=document.getElementById('fileList');if(!list)return;var items=Array.prototype.slice.call(list.querySelectorAll('.file-item'));items.sort(function(a,b){var ad=a.dataset.dir==='1',bd=b.dataset.dir==='1';if(ad!==bd)return ad?-1:1;var r=0;if(mode==='size')r=(Number(a.dataset.size)||0)-(Number(b.dataset.size)||0);else if(mode==='time')r=(Number(b.dataset.time)||0)-(Number(a.dataset.time)||0);else if(mode==='type')r=(a.dataset.format||'').localeCompare(b.dataset.format||'');else r=(a.dataset.name||'').localeCompare(b.dataset.name||'');if(!r)r=(a.dataset.name||'').localeCompare(b.dataset.name||'');return r;});items.forEach(function(it){list.appendChild(it);});}function restoreSortMode(){setSortMode(localStorage.getItem('pocketdrive-sort')||'name');}function makeQr(){var url=location.href.split('#')[0],cur=document.getElementById('currentUrl'),img=document.getElementById('qrImg'),txt=document.getElementById('qrUrl');if(cur)cur.textContent=url;if(txt)txt.textContent=url;if(img)img.src='/__qr.png?u='+encodeURIComponent(url);}function updateMode(){var desktop=window.matchMedia('(min-width:860px)').matches;document.body.classList.toggle('desktop',desktop);var p=document.getElementById('modePill');if(p)p.textContent=desktop?'网页版':'手机端';}window.addEventListener('resize',updateMode);updateMode();restoreViewMode();restoreSortMode();makeQr();renderHistory();renderFavs();markStars();updateTrashSelection();if(location.hash==='#upload'){switchTab('upload',document.querySelectorAll('.tab')[1]);}</script></body></html>";
        }

        private String uploadCard() {
            return "<div class='card upload-card'><div class='card-title'>上传文件</div><form method='post' enctype='multipart/form-data'><div class='drop-zone'><input type='file' name='file' multiple><span class='drop-icon'>↑</span><div class='drop-text'><strong>点击选择</strong>或拖拽文件到此处</div></div><button class='btn btn-primary' name='action' value='upload'>开始上传</button></form><div class='progress'><span></span></div><div class='uploadText'>等待上传</div><div class='upload-queue'></div></div>";
        }

        private String trashRows() {
            cleanTrash();
            File[] files = trashDir.listFiles();
            if (files == null || files.length == 0) {
                return "<div class='empty'><div class='empty-icon'>🗑</div>回收站是空的</div>";
            }
            List<File> list = new ArrayList<File>();
            Collections.addAll(list, files);
            Collections.sort(list, new Comparator<File>() {
                @Override public int compare(File a, File b) {
                    return Long.valueOf(b.lastModified()).compareTo(Long.valueOf(a.lastModified()));
                }
            });
            StringBuilder html = new StringBuilder();
            for (File file : list) {
                String name = file.getName();
                html.append("<div class='file-item'><input class='trash-select-box' type='checkbox' value='").append(escapeAttr(name)).append("' onchange='updateTrashSelection()'><div class='file-icon-wrap other'>🗑</div><div class='file-details'><div class='file-item-name'>")
                        .append(escape(originalTrashName(name))).append("</div><div class='file-meta'><span>")
                        .append(file.isDirectory() ? "文件夹" : formatSize(file.length())).append("</span><span>·</span><span>")
                        .append(modifiedDate(file)).append("</span></div></div><div class='file-actions'>")
                        .append("<form method='post' class='mini-form'><input type='hidden' name='target' value='").append(escapeAttr(name)).append("'><button class='action-btn' name='action' value='restore' title='恢复'>↩</button></form>")
                        .append("<form method='post' class='mini-form' onsubmit=\"return confirm('永久删除 ").append(escapeJs(originalTrashName(name))).append(" ?')\"><input type='hidden' name='target' value='").append(escapeAttr(name)).append("'><button class='action-btn danger' name='action' value='purge' title='永久删除'>×</button></form>")
                        .append("</div></div>");
            }
            return html.toString();
        }

        private String originalTrashName(String name) {
            int split = name.indexOf("__");
            return split >= 0 && split + 2 < name.length() ? name.substring(split + 2) : name;
        }

        private String formatSize(long size) {
            double value = size;
            String[] units = new String[]{"B", "KB", "MB", "GB"};
            int index = 0;
            while (value >= 1024 && index < units.length - 1) {
                value = value / 1024;
                index++;
            }
            return index == 0 ? String.valueOf((long) value) + " " + units[index] : String.format(Locale.US, "%.1f %s", value, units[index]);
        }

        private String modifiedDate(File file) {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(file.lastModified()));
        }

        private String typeClass(File file) {
            if (file.isDirectory()) return "folder";
            String name = file.getName().toLowerCase(Locale.US);
            if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".gif") || name.endsWith(".webp")) return "img";
            if (name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".ogg") || name.endsWith(".m4a") || name.endsWith(".aac") || name.endsWith(".flac")) return "audio";
            if (name.endsWith(".mp4") || name.endsWith(".webm") || name.endsWith(".m4v") || name.endsWith(".mov")) return "video";
            if (name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".log") || name.endsWith(".json") || name.endsWith(".xml")) return "txt";
            if (name.endsWith(".pdf") || name.endsWith(".doc") || name.endsWith(".docx") || name.endsWith(".xls") || name.endsWith(".xlsx") || name.endsWith(".ppt") || name.endsWith(".pptx")) return "doc";
            return "other";
        }

        private String iconFor(File file, String href) {
            String type = typeClass(file);
            if ("folder".equals(type)) return "📁";
            if ("img".equals(type)) return "<img class='file-thumb' src='" + href + "?thumb=1' alt=''>";
            if ("audio".equals(type)) return "♪";
            if ("video".equals(type)) return "▶";
            if ("txt".equals(type)) return "📄";
            if ("doc".equals(type)) return "📝";
            return "📦";
        }

        private boolean canPreview(File file) {
            if (file.isDirectory()) return false;
            String name = file.getName().toLowerCase(Locale.US);
            return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".gif") || name.endsWith(".webp")
                    || name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".log") || name.endsWith(".json") || name.endsWith(".xml")
                    || name.endsWith(".pdf")
                    || isAudio(file) || isVideo(file);
        }

        private boolean isImage(File file) {
            String name = file.getName().toLowerCase(Locale.US);
            return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".gif") || name.endsWith(".webp");
        }

        private boolean isAudio(File file) {
            String name = file.getName().toLowerCase(Locale.US);
            return name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".ogg") || name.endsWith(".m4a") || name.endsWith(".aac") || name.endsWith(".flac");
        }

        private boolean isVideo(File file) {
            String name = file.getName().toLowerCase(Locale.US);
            return name.endsWith(".mp4") || name.endsWith(".webm") || name.endsWith(".m4v") || name.endsWith(".mov");
        }

        private String fileFormat(File file) {
            if (file.isDirectory()) return "文件夹";
            String name = file.getName();
            int dot = name.lastIndexOf('.');
            return dot >= 0 && dot + 1 < name.length() ? name.substring(dot + 1).toUpperCase(Locale.US) : "未知";
        }

        private String handlePost(String path, String type, BufferedInputStream input, long length) throws Exception {
            File dir = fileForPath(path);
            if (type.indexOf("multipart/form-data") >= 0) {
                String boundary = type.substring(type.indexOf("boundary=") + 9);
                saveMultipartStream(dir, boundary, input, length);
            } else {
                byte[] body = readBody(input, length);
                String form = new String(body, UTF8);
                String action = formValue(form, "action");
                if ("mkdir".equals(action)) {
                    String name = safeName(formValue(form, "dirname"));
                    if (name.length() > 0) new File(dir, name).mkdirs();
                } else if ("delete".equals(action)) {
                    File target = new File(dir, safeName(formValue(form, "target")));
                    moveToTrash(target);
                } else if ("purge".equals(action)) {
                    File target = new File(trashDir, safeName(formValue(form, "target")));
                    if (target.getCanonicalPath().startsWith(trashDir.getCanonicalPath())) deleteTree(target);
                } else if ("restore".equals(action)) {
                    restoreFromTrash(safeName(formValue(form, "target")));
                } else if ("bulkPurge".equals(action)) {
                    String[] targets = formValue(form, "targets").split("\\|");
                    for (String value : targets) {
                        String name = safeName(value.trim());
                        if (name.length() == 0) continue;
                        File target = new File(trashDir, name);
                        if (target.getCanonicalPath().startsWith(trashDir.getCanonicalPath())) deleteTree(target);
                    }
                } else if ("bulkRestore".equals(action)) {
                    String[] targets = formValue(form, "targets").split("\\|");
                    for (String value : targets) {
                        String name = safeName(value.trim());
                        if (name.length() > 0) restoreFromTrash(name);
                    }
                } else if ("trashSetting".equals(action)) {
                    int days = 30;
                    try {
                        days = Integer.parseInt(formValue(form, "days"));
                    } catch (Exception ignored) {
                    }
                    saveTrashSetting(days);
                } else if ("move".equals(action)) {
                    File target = new File(dir, safeName(formValue(form, "target")));
                    String destValue = formValue(form, "dest").trim();
                    File dest = safeDestination(dir, destValue);
                    if (target.exists() && dest != null) {
                        File parent = dest.getParentFile();
                        if (parent != null) parent.mkdirs();
                        target.renameTo(dest);
                    }
                } else if ("bulkDelete".equals(action)) {
                    String[] targets = formValue(form, "targets").split("\\|");
                    for (String value : targets) {
                        String name = safeName(value.trim());
                        if (name.length() > 0) moveToTrash(new File(dir, name));
                    }
                } else if ("bulkMove".equals(action)) {
                    String destDirValue = formValue(form, "dest").trim();
                    String[] targets = formValue(form, "targets").split("\\|");
                    for (String value : targets) {
                        String name = safeName(value.trim());
                        if (name.length() == 0) continue;
                        File target = new File(dir, name);
                        File dest = safeDestination(dir, destDirValue + name);
                        if (target.exists() && dest != null) {
                            File parent = dest.getParentFile();
                            if (parent != null) parent.mkdirs();
                            target.renameTo(dest);
                        }
                    }
                } else if ("auth".equals(action)) {
                    saveAuth(formValue(form, "user").trim(), formValue(form, "pass").trim());
                }
                if ("purge".equals(action) || "restore".equals(action) || "trashSetting".equals(action) || "bulkPurge".equals(action) || "bulkRestore".equals(action)) {
                    return "upload";
                }
            }
            return "";
        }

        private void moveToTrash(File target) throws Exception {
            if (!target.exists()) return;
            if (target.getCanonicalPath().startsWith(trashDir.getCanonicalPath())) {
                deleteTree(target);
                return;
            }
            String stamp = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(new Date());
            File dest = uniqueFile(trashDir, stamp + "__" + target.getName());
            target.renameTo(dest);
        }

        private void restoreFromTrash(String trashName) throws Exception {
            File source = new File(trashDir, trashName);
            if (!source.exists() || !source.getCanonicalPath().startsWith(trashDir.getCanonicalPath())) return;
            String original = originalTrashName(trashName);
            source.renameTo(uniqueFile(root, original));
        }

        private void savePut(File target, BufferedInputStream input, long length, String transferEncoding) throws Exception {
            File parent = target.getParentFile();
            if (parent != null) parent.mkdirs();
            File tempFile = new File(parent == null ? root : parent, ".uploading-" + System.currentTimeMillis() + "-" + safeName(target.getName()));
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(tempFile);
                copyRequestBody(input, out, length, transferEncoding);
                out.getFD().sync();
                out.close();
                out = null;
                if (target.isDirectory()) {
                    deleteTree(target);
                } else if (target.isFile()) {
                    target.delete();
                }
                if (!tempFile.renameTo(target)) {
                    copyFile(tempFile, target);
                    tempFile.delete();
                }
            } catch (Exception e) {
                if (out != null) out.close();
                tempFile.delete();
                appendLog("PUT ERROR " + target.getAbsolutePath() + " " + e.getClass().getSimpleName() + " " + e.getMessage());
                throw e;
            }
        }

        private String handleDavMove(String path, String[] lines) throws Exception {
            DavTarget davTarget = davTarget(lines);
            if (davTarget.destination.length() == 0) return "400 Bad Request";
            File src = fileForPath(path);
            if (!src.exists()) return "404 Not Found";
            File dest = fileForPath(davTarget.destination);
            boolean existed = dest.exists();
            if (existed && !davTarget.overwrite) return "412 Precondition Failed";
            if (existed) {
                if (dest.isDirectory()) deleteTree(dest);
                else dest.delete();
            }
            File parent = dest.getParentFile();
            if (parent != null) parent.mkdirs();
            if (!src.renameTo(dest)) {
                copyTree(src, dest);
                if (src.isDirectory()) deleteTree(src);
                else src.delete();
            }
            return existed ? "204 No Content" : "201 Created";
        }

        private String handleDavCopy(String path, String[] lines) throws Exception {
            DavTarget davTarget = davTarget(lines);
            if (davTarget.destination.length() == 0) return "400 Bad Request";
            File src = fileForPath(path);
            if (!src.exists()) return "404 Not Found";
            File dest = fileForPath(davTarget.destination);
            boolean existed = dest.exists();
            if (existed && !davTarget.overwrite) return "412 Precondition Failed";
            if (existed) {
                if (dest.isDirectory()) deleteTree(dest);
                else dest.delete();
            }
            copyTree(src, dest);
            return existed ? "204 No Content" : "201 Created";
        }

        private DavTarget davTarget(String[] lines) {
            String destination = "";
            boolean overwrite = true;
            for (String line : lines) {
                String lower = line.toLowerCase(Locale.US);
                if (lower.startsWith("destination:")) {
                    destination = line.substring(line.indexOf(':') + 1).trim();
                } else if (lower.startsWith("overwrite:")) {
                    overwrite = !"f".equalsIgnoreCase(line.substring(line.indexOf(':') + 1).trim());
                }
            }
            int scheme = destination.indexOf("://");
            if (scheme >= 0) {
                int slash = destination.indexOf('/', scheme + 3);
                destination = slash >= 0 ? destination.substring(slash) : "/";
            }
            return new DavTarget(destination, overwrite);
        }

        private boolean isOverwriteFalse(String[] lines) {
            for (String line : lines) {
                String lower = line.toLowerCase(Locale.US);
                if (lower.startsWith("overwrite:")) {
                    return "f".equalsIgnoreCase(line.substring(line.indexOf(':') + 1).trim());
                }
            }
            return false;
        }

        private class DavTarget {
            final String destination;
            final boolean overwrite;

            DavTarget(String destination, boolean overwrite) {
                this.destination = destination;
                this.overwrite = overwrite;
            }
        }

        private void copyTree(File src, File dest) throws Exception {
            if (src.isDirectory()) {
                dest.mkdirs();
                File[] files = src.listFiles();
                if (files != null) for (File child : files) copyTree(child, new File(dest, child.getName()));
            } else {
                File parent = dest.getParentFile();
                if (parent != null) parent.mkdirs();
                FileInputStream in = new FileInputStream(src);
                FileOutputStream out = new FileOutputStream(dest);
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
                in.close();
                out.close();
            }
        }

        private void sendOptions(OutputStream out) throws Exception {
            out.write(("HTTP/1.1 200 OK\r\nDAV: 1, 2\r\nMS-Author-Via: DAV\r\nAllow: OPTIONS, GET, HEAD, POST, PUT, DELETE, MKCOL, MOVE, COPY, PROPFIND, PROPPATCH, LOCK, UNLOCK\r\n" + connectionHeader() + "\r\nContent-Length: 0\r\n\r\n").getBytes(UTF8));
        }

        private void logDav(String method, String path, String status) {
            try {
                FileOutputStream out = new FileOutputStream(logFile, true);
                String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
                out.write((time + " DAV " + method + " " + status + " " + path + "\n").getBytes(UTF8));
                out.close();
            } catch (Exception ignored) {
            }
        }

        private void sendPropfind(OutputStream out, String path, File file, String depth) throws Exception {
            if (file.isDirectory() && !path.endsWith("/")) path += "/";
            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><D:multistatus xmlns:D=\"DAV:\">");
            appendDavResponse(xml, path, file);
            int[] count = new int[]{0};
            if (file.isDirectory() && !"0".equals(depth)) {
                appendDavChildren(xml, path, file, "infinity".equals(depth) ? 2 : 1, count);
            }
            xml.append("</D:multistatus>");
            byte[] data = xml.toString().getBytes(UTF8);
            out.write(("HTTP/1.1 207 Multi-Status\r\nContent-Type: application/xml; charset=utf-8\r\n" + connectionHeader() + "\r\nContent-Length: " + data.length + "\r\n\r\n").getBytes(UTF8));
            out.write(data);
        }

        private void appendDavChildren(StringBuilder xml, String parentHref, File dir, int depth, int[] count) throws Exception {
            if (depth == 0) return;
            File[] files = dir.listFiles();
            if (files == null) return;
            for (File child : files) {
                if (count[0] >= DAV_MAX_CHILDREN) return;
                if (".trash".equals(child.getName()) || child.getName().startsWith(".uploading-")) continue;
                count[0]++;
                String href = parentHref;
                if (!href.endsWith("/")) href += "/";
                href += URLEncoder.encode(child.getName(), "UTF-8").replace("+", "%20");
                if (child.isDirectory()) href += "/";
                appendDavResponse(xml, href, child);
                if (child.isDirectory() && depth != 1) {
                    appendDavChildren(xml, href, child, depth - 1, count);
                }
            }
        }

        private void appendDavResponse(StringBuilder xml, String href, File file) {
            xml.append("<D:response><D:href>").append(escape(href)).append("</D:href><D:propstat><D:prop>");
            xml.append("<D:displayname>").append(escape(file.getName().length() == 0 ? "NAS" : file.getName())).append("</D:displayname>");
            xml.append("<D:getlastmodified>").append(new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).format(new Date(file.lastModified()))).append("</D:getlastmodified>");
            xml.append("<D:creationdate>").append(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date(file.lastModified()))).append("</D:creationdate>");
            xml.append("<D:getetag>\"").append(file.lastModified()).append("-").append(file.length()).append("\"</D:getetag>");
            if (file.isDirectory()) {
                xml.append("<D:resourcetype><D:collection/></D:resourcetype>");
            } else {
                xml.append("<D:resourcetype/><D:getcontentlength>").append(file.length()).append("</D:getcontentlength><D:getcontenttype>application/octet-stream</D:getcontenttype>");
            }
            xml.append("<D:supportedlock><D:lockentry><D:lockscope><D:exclusive/></D:lockscope><D:locktype><D:write/></D:locktype></D:lockentry></D:supportedlock><D:lockdiscovery/>");
            xml.append("</D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>");
        }

        private File safeDestination(File dir, String value) throws Exception {
            if (value.length() == 0 || value.indexOf('\0') >= 0) return null;
            File dest = new File(dir, value);
            String rootPath = root.getCanonicalPath();
            String destPath = dest.getCanonicalPath();
            if (!destPath.equals(rootPath) && !destPath.startsWith(rootPath + File.separator)) return null;
            return dest;
        }

        private void saveMultipartStream(File dir, String boundary, BufferedInputStream input, long length) throws Exception {
            if (boundary.startsWith("\"") && boundary.endsWith("\"") && boundary.length() > 1) {
                boundary = boundary.substring(1, boundary.length() - 1);
            }
            long[] remaining = new long[]{length};
            String first = readBodyLine(input, remaining);
            if (!first.startsWith("--" + boundary)) {
                drain(input, remaining[0]);
                return;
            }
            StringBuilder headers = new StringBuilder();
            String line;
            while ((line = readBodyLine(input, remaining)) != null && line.length() > 0) {
                headers.append(line).append("\r\n");
            }
            String header = headers.toString();
            int fn = header.indexOf("filename=\"");
            if (fn < 0) {
                drain(input, remaining[0]);
                return;
            }
            int start = fn + 10;
            int end = header.indexOf('"', start);
            if (end <= start) {
                drain(input, remaining[0]);
                return;
            }
            String name = safeName(header.substring(start, end));
            if (name.length() == 0) {
                drain(input, remaining[0]);
                return;
            }
            File finalFile = uniqueFile(dir, name);
            File tempFile = new File(dir, ".uploading-" + System.currentTimeMillis() + "-" + safeName(name));
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(tempFile);
                boolean complete = streamUntilBoundary(input, out, boundary, remaining);
                out.close();
                out = null;
                if (!complete) {
                    throw new Exception("Incomplete multipart upload");
                }
                if (!tempFile.renameTo(finalFile)) {
                    copyFile(tempFile, finalFile);
                    tempFile.delete();
                }
                drain(input, remaining[0]);
            } catch (Exception e) {
                if (out != null) out.close();
                tempFile.delete();
                throw e;
            }
        }

        private boolean streamUntilBoundary(BufferedInputStream input, OutputStream out, String boundary, long[] remaining) throws Exception {
            byte[] marker = ("\r\n--" + boundary).getBytes(UTF8);
            byte[] buffer = new byte[1024 * 256];
            byte[] pending = new byte[0];
            while (remaining[0] > 0) {
                int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining[0]));
                if (read < 0) return false;
                remaining[0] -= read;
                byte[] combined = new byte[pending.length + read];
                System.arraycopy(pending, 0, combined, 0, pending.length);
                System.arraycopy(buffer, 0, combined, pending.length, read);
                int found = indexOf(combined, marker, 0);
                if (found >= 0) {
                    if (found > 0) out.write(combined, 0, found);
                    drain(input, remaining[0]);
                    remaining[0] = 0;
                    return true;
                }
                int keep = Math.min(marker.length - 1, combined.length);
                int writeLen = combined.length - keep;
                if (writeLen > 0) out.write(combined, 0, writeLen);
                pending = new byte[keep];
                if (keep > 0) System.arraycopy(combined, combined.length - keep, pending, 0, keep);
            }
            return false;
        }

        private int indexOf(byte[] data, byte[] pattern, int start) {
            outer: for (int i = start; i <= data.length - pattern.length; i++) {
                for (int j = 0; j < pattern.length; j++) {
                    if (data[i + j] != pattern[j]) continue outer;
                }
                return i;
            }
            return -1;
        }

        private String readBodyLine(InputStream input, long[] remaining) throws Exception {
            if (remaining[0] <= 0) return null;
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            while (remaining[0] > 0) {
                int b = input.read();
                if (b < 0) break;
                remaining[0]--;
                if (b == '\n') break;
                if (b != '\r') line.write(b);
            }
            return new String(line.toByteArray(), UTF8);
        }

        private byte[] readBody(InputStream input, long length) throws Exception {
            if (length > 1024L * 1024L) {
                throw new Exception("Form body too large");
            }
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            copyFixed(input, body, length);
            return body.toByteArray();
        }

        private void copyFixed(InputStream input, OutputStream out, long length) throws Exception {
            byte[] buffer = new byte[1024 * 128];
            long remaining = length;
            while (remaining > 0) {
                int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) throw new Exception("Unexpected end of request body, remaining=" + remaining);
                out.write(buffer, 0, read);
                remaining -= read;
            }
        }

        private void copyRequestBody(BufferedInputStream input, OutputStream out, long length, String transferEncoding) throws Exception {
            if (transferEncoding.indexOf("chunked") >= 0) {
                copyChunked(input, out);
            } else {
                copyFixed(input, out, length);
            }
        }

        private void drainRequestBody(BufferedInputStream input, long length, String transferEncoding) throws Exception {
            if (transferEncoding.indexOf("chunked") >= 0) {
                copyChunked(input, null);
            } else {
                drain(input, length);
            }
        }

        private void drainUnusedBody(BufferedInputStream input, long length, String transferEncoding) throws Exception {
            if (length > 0 || transferEncoding.indexOf("chunked") >= 0) {
                drainRequestBody(input, length, transferEncoding);
            }
        }

        private void copyChunked(BufferedInputStream input, OutputStream out) throws Exception {
            byte[] buffer = new byte[1024 * 128];
            while (true) {
                String line = readRawLine(input);
                if (line == null) break;
                int semi = line.indexOf(';');
                String sizeText = (semi >= 0 ? line.substring(0, semi) : line).trim();
                if (sizeText.length() == 0) continue;
                int size = Integer.parseInt(sizeText, 16);
                if (size == 0) {
                    while (true) {
                        String trailer = readRawLine(input);
                        if (trailer == null || trailer.length() == 0) break;
                    }
                    break;
                }
                int remaining = size;
                while (remaining > 0) {
                    int read = input.read(buffer, 0, Math.min(buffer.length, remaining));
                    if (read < 0) throw new Exception("Unexpected end of chunked body");
                    if (out != null) out.write(buffer, 0, read);
                    remaining -= read;
                }
                readRawLine(input);
            }
        }

        private String readRawLine(InputStream input) throws Exception {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            while (true) {
                int b = input.read();
                if (b < 0) {
                    return line.size() == 0 ? null : new String(line.toByteArray(), UTF8);
                }
                if (b == '\n') break;
                if (b != '\r') line.write(b);
            }
            return new String(line.toByteArray(), UTF8);
        }

        private void drain(InputStream input, long length) throws Exception {
            byte[] buffer = new byte[8192];
            long remaining = length;
            while (remaining > 0) {
                int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) break;
                remaining -= read;
            }
        }

        private void copyFile(File source, File dest) throws Exception {
            FileInputStream in = new FileInputStream(source);
            FileOutputStream out = new FileOutputStream(dest);
            byte[] buffer = new byte[1024 * 128];
            int read;
            while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
            in.close();
            out.close();
        }

        private File uniqueFile(File dir, String name) {
            File file = new File(dir, name);
            if (!file.exists()) return file;
            String base = name;
            String ext = "";
            int dot = name.lastIndexOf('.');
            if (dot > 0) {
                base = name.substring(0, dot);
                ext = name.substring(dot);
            }
            int index = 1;
            while (true) {
                file = new File(dir, base + " (" + index + ")" + ext);
                if (!file.exists()) return file;
                index++;
            }
        }

        private String formValue(String form, String key) throws Exception {
            for (String pair : form.split("&")) {
                String[] parts = pair.split("=", 2);
                if (parts.length == 2 && key.equals(URLDecoder.decode(parts[0], "UTF-8"))) {
                    return URLDecoder.decode(parts[1], "UTF-8");
                }
            }
            return "";
        }

        private String safeName(String name) {
            return new File(name).getName();
        }

        private void deleteTree(File file) {
            File[] files = file.listFiles();
            if (files != null) for (File child : files) deleteTree(child);
            file.delete();
        }

        private String escape(String value) {
            return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }

        private String escapeAttr(String value) {
            return escape(value).replace("\"", "&quot;").replace("'", "&#39;");
        }

        private String escapeJs(String value) {
            return value.replace("\\", "\\\\").replace("'", "\\'").replace("\r", "").replace("\n", "");
        }

        private void sendHtml(OutputStream out, String html) throws Exception {
            byte[] data = html.getBytes(UTF8);
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" + connectionHeader() + "\r\nContent-Length: " + data.length + "\r\n\r\n").getBytes(UTF8));
            out.write(data);
        }

        private void sendText(OutputStream out, String status, String text) throws Exception {
            byte[] data = text.getBytes(UTF8);
            out.write(("HTTP/1.1 " + status + "\r\nContent-Type: text/plain; charset=utf-8\r\n" + connectionHeader() + "\r\nContent-Length: " + data.length + "\r\n\r\n").getBytes(UTF8));
            out.write(data);
        }

        private void redirect(OutputStream out, String path) throws Exception {
            out.write(("HTTP/1.1 303 See Other\r\nLocation: " + path + "\r\n" + connectionHeader() + "\r\nContent-Length: 0\r\n\r\n").getBytes(UTF8));
        }

        private void sendHead(OutputStream out, File file) throws Exception {
            long length = file.isDirectory() ? 0 : file.length();
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nAccept-Ranges: bytes\r\n" + connectionHeader() + "\r\nContent-Length: " + length + "\r\n\r\n").getBytes(UTF8));
        }

        private void sendLock(OutputStream out, String path) throws Exception {
            String token = "opaquelocktoken:pocketdrive-" + System.currentTimeMillis() + "-" + Math.abs(path.hashCode());
            synchronized (locks) {
                locks.put(path, token);
            }
            String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?><D:prop xmlns:D=\"DAV:\"><D:lockdiscovery><D:activelock><D:locktype><D:write/></D:locktype><D:lockscope><D:exclusive/></D:lockscope><D:depth>Infinity</D:depth><D:owner/><D:timeout>Second-3600</D:timeout><D:locktoken><D:href>" + token + "</D:href></D:locktoken></D:activelock></D:lockdiscovery></D:prop>";
            byte[] data = xml.getBytes(UTF8);
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/xml; charset=utf-8\r\nLock-Token: <" + token + ">\r\nTimeout: Second-3600\r\n" + connectionHeader() + "\r\nContent-Length: " + data.length + "\r\n\r\n").getBytes(UTF8));
            out.write(data);
        }

        private void unlockPath(String path, String[] lines) {
            String token = "";
            for (String line : lines) {
                String lower = line.toLowerCase(Locale.US);
                if (lower.startsWith("lock-token:")) {
                    token = line.substring(line.indexOf(':') + 1).trim();
                    token = token.replace("<", "").replace(">", "");
                }
            }
            synchronized (locks) {
                locks.remove(path);
                if (token.length() > 0) {
                    String found = null;
                    for (Map.Entry<String, String> entry : locks.entrySet()) {
                        if (token.equals(entry.getValue())) {
                            found = entry.getKey();
                            break;
                        }
                    }
                    if (found != null) locks.remove(found);
                }
            }
        }

        private void sendPreview(OutputStream out, File file) throws Exception {
            if (!canPreview(file)) {
                sendUnsupportedPreviewPage(out, file);
                return;
            }
            if (isImage(file)) {
                sendImagePreviewPage(out, file);
                return;
            }
            if (isAudio(file)) {
                sendMediaPreviewPage(out, file, "audio");
                return;
            }
            if (isVideo(file)) {
                sendMediaPreviewPage(out, file, "video");
                return;
            }
            sendInlineFile(out, file);
        }

        private void sendUnsupportedPreviewPage(OutputStream out, File file) throws Exception {
            String name = escape(file.getName());
            String html = "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><style>html,body{margin:0;width:100%;height:100%;background:#05070c;color:#e2e8f0;font-family:system-ui,-apple-system,sans-serif}body{display:flex;align-items:center;justify-content:center;padding:18px;text-align:center}.box{max-width:360px}.icon{font-size:34px;margin-bottom:10px}.title{font-size:16px;font-weight:700;margin-bottom:8px}.desc{font-size:13px;line-height:1.5;color:#94a3b8}</style></head><body><div class='box'><div class='icon'>📦</div><div class='title'>此格式暂不支持预览</div><div class='desc'>" + name + "<br>请从文件菜单选择下载。</div></div></body></html>";
            byte[] data = html.getBytes(UTF8);
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" + connectionHeader() + "\r\nContent-Length: " + data.length + "\r\n\r\n").getBytes(UTF8));
            out.write(data);
        }

        private void sendImagePreviewPage(OutputStream out, File file) throws Exception {
            String src = URLEncoder.encode(file.getName(), "UTF-8").replace("+", "%20");
            String html = "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><style>html,body{margin:0;width:100%;height:100%;background:#05070c;overflow:hidden}body{display:flex;align-items:center;justify-content:center}img{max-width:100vw;max-height:100vh;width:auto;height:auto;object-fit:contain;display:block}</style></head><body><img src='" + src + "' alt='" + escapeAttr(file.getName()) + "'></body></html>";
            byte[] data = html.getBytes(UTF8);
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" + connectionHeader() + "\r\nContent-Length: " + data.length + "\r\n\r\n").getBytes(UTF8));
            out.write(data);
        }

        private void sendMediaPreviewPage(OutputStream out, File file, String tag) throws Exception {
            String src = URLEncoder.encode(file.getName(), "UTF-8").replace("+", "%20");
            String name = escape(file.getName());
            String media = "audio".equals(tag)
                    ? "<audio controls autoplay src='" + src + "'></audio>"
                    : "<video controls autoplay playsinline src='" + src + "'></video>";
            String html = "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#05070c;color:#e2e8f0;font-family:system-ui,-apple-system,sans-serif}body{display:flex;align-items:center;justify-content:center;padding:12px}.wrap{width:100%;height:100%;min-width:0;display:flex;flex-direction:column;gap:10px;overflow:hidden}.name{font-size:13px;line-height:18px;color:#94a3b8;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;flex-shrink:0}audio{width:100%;height:48px;flex-shrink:0}video{width:100%;height:100%;min-height:0;object-fit:contain;border-radius:12px;background:#000;outline:0}</style></head><body><div class='wrap'><div class='name'>" + name + "</div>" + media + "</div></body></html>";
            byte[] data = html.getBytes(UTF8);
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" + connectionHeader() + "\r\nContent-Length: " + data.length + "\r\n\r\n").getBytes(UTF8));
            out.write(data);
        }

        private void sendInlineFile(OutputStream out, File file) throws Exception {
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: " + mimeType(file) + "\r\nAccept-Ranges: bytes\r\n" + connectionHeader() + "\r\nContent-Length: " + file.length() + "\r\nContent-Disposition: inline; filename=\"" + file.getName() + "\"\r\n\r\n").getBytes(UTF8));
            FileInputStream in = new FileInputStream(file);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            in.close();
        }

        private void sendPng(OutputStream out, Bitmap bitmap) throws Exception {
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, png);
            byte[] data = png.toByteArray();
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nCache-Control: no-store\r\n" + connectionHeader() + "\r\nContent-Length: " + data.length + "\r\n\r\n").getBytes(UTF8));
            out.write(data);
        }

        private void sendCertificate(OutputStream out) throws Exception {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            InputStream in = getAssets().open("pocketdrive_tls.p12");
            char[] password = "pocketdrive".toCharArray();
            keyStore.load(in, password);
            in.close();
            Certificate cert = keyStore.getCertificate("pocketdrive");
            byte[] data = cert.getEncoded();
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/x-x509-ca-cert\r\nContent-Disposition: attachment; filename=\"PocketDrive.cer\"\r\nCache-Control: no-store\r\n" + connectionHeader() + "\r\nContent-Length: " + data.length + "\r\n\r\n").getBytes(UTF8));
            out.write(data);
        }

        private String mimeType(File file) {
            String name = file.getName().toLowerCase(Locale.US);
            if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
            if (name.endsWith(".png")) return "image/png";
            if (name.endsWith(".gif")) return "image/gif";
            if (name.endsWith(".webp")) return "image/webp";
            if (name.endsWith(".mp3")) return "audio/mpeg";
            if (name.endsWith(".wav")) return "audio/wav";
            if (name.endsWith(".ogg")) return "audio/ogg";
            if (name.endsWith(".m4a")) return "audio/mp4";
            if (name.endsWith(".aac")) return "audio/aac";
            if (name.endsWith(".flac")) return "audio/flac";
            if (name.endsWith(".mp4") || name.endsWith(".m4v")) return "video/mp4";
            if (name.endsWith(".webm")) return "video/webm";
            if (name.endsWith(".mov")) return "video/quicktime";
            if (name.endsWith(".pdf")) return "application/pdf";
            if (name.endsWith(".html") || name.endsWith(".htm")) return "text/html; charset=utf-8";
            if (name.endsWith(".json")) return "application/json; charset=utf-8";
            if (name.endsWith(".xml")) return "application/xml; charset=utf-8";
            return "text/plain; charset=utf-8";
        }

        private void sendFile(OutputStream out, File file) throws Exception {
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nAccept-Ranges: bytes\r\n" + connectionHeader() + "\r\nContent-Length: " + file.length() + "\r\nContent-Disposition: attachment; filename=\"" + file.getName() + "\"\r\n\r\n").getBytes(UTF8));
            FileInputStream in = new FileInputStream(file);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            in.close();
        }
    }
}
