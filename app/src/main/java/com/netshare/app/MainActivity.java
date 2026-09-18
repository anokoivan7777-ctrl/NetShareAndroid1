package com.netshare.app;

import android.app.Activity;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class MainActivity extends Activity {

    private boolean isRunning = false;
    private ServerSocket serverSocket;
    private TextView tvStatus, tvIp;
    private Button btnToggle;
    private final int PORT = 8080;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 100, 60, 60);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("NetShare Сервер");
        title.setTextSize(26);
        title.setTextColor(Color.BLACK);
        layout.addView(title);

        tvStatus = new TextView(this);
        tvStatus.setText("Статус: ОСТАНОВЛЕН");
        tvStatus.setTextSize(18);
        tvStatus.setPadding(0, 40, 0, 20);
        layout.addView(tvStatus);

        tvIp = new TextView(this);
        tvIp.setText("IP: не определен");
        tvIp.setTextSize(16);
        layout.addView(tvIp);

        btnToggle = new Button(this);
        btnToggle.setText("ЗАПУСТИТЬ РАЗДАЧУ");
        btnToggle.setTextSize(18);
        btnToggle.setBackgroundColor(Color.parseColor("#007ACC"));
        btnToggle.setTextColor(Color.WHITE);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 150);
        params.setMargins(0, 80, 0, 0);
        btnToggle.setLayoutParams(params);
        layout.addView(btnToggle);

        setContentView(layout);

        btnToggle.setOnClickListener(v -> {
            if (!isRunning) {
                startProxy();
            } else {
                stopProxy();
            }
        });
    }

    private void startProxy() {
        isRunning = true;
        btnToggle.setText("ОСТАНОВИТЬ");
        btnToggle.setBackgroundColor(Color.RED);
        tvStatus.setText("Статус: РАБОТАЕТ (Порт " + PORT + ")");
        tvStatus.setTextColor(Color.parseColor("#008000"));

        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
            tvIp.setText("Локальный IP для Wi-Fi: " + ip);
        } catch (Exception e) {
            tvIp.setText("IP: режим кабеля USB");
        }

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                while (isRunning) {
                    Socket client = serverSocket.accept();
                    new Thread(new ProxyTask(client)).start();
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void stopProxy() {
        isRunning = false;
        btnToggle.setText("ЗАПУСТИТЬ РАЗДАЧУ");
        btnToggle.setBackgroundColor(Color.parseColor("#007ACC"));
        tvStatus.setText("Статус: ОСТАНОВЛЕН");
        tvStatus.setTextColor(Color.BLACK);
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception ignored) {}
    }

    private static class ProxyTask implements Runnable {
        private final Socket client;

        public ProxyTask(Socket client) {
            this.client = client;
        }

        @Override
        public void run() {
            try {
                InputStream in = client.getInputStream();
                OutputStream out = client.getOutputStream();

                byte[] buffer = new byte[8192];
                int read = in.read(buffer);
                if (read <= 0) return;

                String request = new String(buffer, 0, read);
                String[] parts = request.split(" ");
                if (parts.length < 2) return;

                String method = parts[0];
                String hostPort = parts[1];

                String host;
                int port = 80;

                if (method.equalsIgnoreCase("CONNECT")) {
                    String[] hp = hostPort.split(":");
                    host = hp[0];
                    if (hp.length > 1) port = Integer.parseInt(hp[1]);

                    Socket remote = new Socket(host, port);
                    out.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
                    out.flush();

                    pipe(client, remote);
                } else {
                    if (hostPort.startsWith("http://")) hostPort = hostPort.substring(7);
                    int slashIdx = hostPort.indexOf('/');
                    if (slashIdx > 0) hostPort = hostPort.substring(0, slashIdx);
                    String[] hp = hostPort.split(":");
                    host = hp[0];
                    if (hp.length > 1) port = Integer.parseInt(hp[1]);

                    Socket remote = new Socket(host, port);
                    remote.getOutputStream().write(buffer, 0, read);
                    pipe(client, remote);
                }
            } catch (Exception ignored) {}
        }

        private void pipe(Socket a, Socket b) {
            new Thread(() -> forward(a, b)).start();
            forward(b, a);
        }

        private void forward(Socket src, Socket dst) {
            try {
                byte[] buf = new byte[8192];
                InputStream in = src.getInputStream();
                OutputStream out = dst.getOutputStream();
                int len;
                while ((len = in.read(buf)) != -1) {
                    out.write(buf, 0, len);
                    out.flush();
                }
            } catch (Exception ignored) {}
            try { src.close(); } catch (Exception ignored) {}
            try { dst.close(); } catch (Exception ignored) {}
        }
    }
}
