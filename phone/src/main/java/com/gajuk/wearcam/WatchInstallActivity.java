package com.gajuk.wearcam;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Install the watch app from the phone itself: the phone pairs with the watch's wireless
 * debugging (Android 11+ pairing) and pushes the bundled APK with Kadb.
 */
public class WatchInstallActivity extends Activity {

    private EditText pairingEndpoint;
    private EditText pairingCode;
    private EditText connectEndpoint;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int p = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p, p, p, p);

        TextView title = new TextView(this);
        title.setText("Install on watch");
        title.setTextSize(26);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title);

        TextView help = new TextView(this);
        help.setTextSize(14);
        help.setText("1. On the watch: Developer options → Wireless debugging → "
                + "\"Pair device with pairing code\".\n"
                + "2. Enter the pairing IP:port and the 6-digit code below.\n"
                + "3. Enter the connect IP:port shown on the Wireless debugging screen.\n"
                + "4. Pair & install — the watch app is pushed over Wi-Fi.");
        root.addView(help);

        Button find = new Button(this);
        find.setText("Find watch automatically");
        find.setAllCaps(false);
        find.setOnClickListener(v -> findWatch());
        root.addView(find);

        pairingEndpoint = field("Pairing IP:port (e.g. 192.168.1.19:37000)",
                InputType.TYPE_CLASS_TEXT);
        root.addView(pairingEndpoint);
        pairingCode = field("Pairing code", InputType.TYPE_CLASS_NUMBER);
        root.addView(pairingCode);
        connectEndpoint = field("Connect IP:port (Wireless debugging screen)",
                InputType.TYPE_CLASS_TEXT);
        root.addView(connectEndpoint);

        status = new TextView(this);
        status.setTextSize(14);
        status.setPadding(0, dp(12), 0, dp(12));
        root.addView(status);

        Button install = new Button(this);
        install.setText("Pair & install");
        install.setAllCaps(false);
        install.setOnClickListener(v -> install());
        root.addView(install);

        setContentView(root);
    }

    private WatchDiscovery discovery;

    private void findWatch() {
        if (discovery != null) return;
        status.setText("Searching for the watch…");
        discovery = new WatchDiscovery(this, new WatchDiscovery.Listener() {
            @Override
            public void onFound(String pairingHost, int pairingPort,
                                String connectHost, int connectPort) {
                runOnUiThread(() -> {
                    pairingEndpoint.setText(pairingHost + ":" + pairingPort);
                    connectEndpoint.setText(connectHost + ":" + connectPort);
                    status.setText("Watch found — enter the 6-digit code and tap Pair & install.");
                    pairingCode.requestFocus();
                });
            }

            @Override
            public void onStatus(String text) {
                runOnUiThread(() -> status.setText(text));
            }
        });
        discovery.start();
    }

    @Override
    protected void onStop() {
        if (discovery != null) {
            discovery.stop();
            discovery = null;
        }
        super.onStop();
    }

    private EditText field(String hint, int type) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setInputType(type);
        edit.setSingleLine(true);
        edit.setTextSize(16);
        return edit;
    }

    private void install() {
        try {
            String[] pair = pairingEndpoint.getText().toString().trim().split(":");
            String[] connect = connectEndpoint.getText().toString().trim().split(":");
            String code = pairingCode.getText().toString().trim();
            if (pair.length != 2 || connect.length != 2 || code.isEmpty()) {
                Toast.makeText(this, "Fill both endpoints and the code", Toast.LENGTH_SHORT).show();
                return;
            }
            String host = pair[0];
            int pairingPort = Integer.parseInt(pair[1]);
            int connectPort = Integer.parseInt(connect[1]);

            File apk = new File(getCacheDir(), "install-watch.apk");
            InputStream in = getAssets().open("watch.apk");
            FileOutputStream out = new FileOutputStream(apk);
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            out.close();
            in.close();

            status.setText("Starting…");
            WatchAdbInstaller.INSTANCE.pairAndInstall(host, pairingPort, code, connectPort, apk,
                    new WatchAdbInstaller.Callback() {
                        @Override
                        public void onStatus(String text) {
                            runOnUiThread(() -> status.setText(text));
                        }

                        @Override
                        public void onResult(boolean ok, String message) {
                            runOnUiThread(() -> {
                                status.setText((ok ? "✓ " : "✗ ") + message);
                                Toast.makeText(WatchInstallActivity.this, message,
                                        Toast.LENGTH_LONG).show();
                            });
                        }
                    });
        } catch (Exception e) {
            status.setText("Failed: " + e);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
