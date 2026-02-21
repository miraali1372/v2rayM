package com.v2ray.ang;

import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConfigOptimizer {
    private static final String TAG = "ConfigOptimizer";

    public static String fetchSubscription(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        int responseCode = connection.getResponseCode();
        if (responseCode == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine).append("\n");
            }
            in.close();
            connection.disconnect();
            return content.toString();
        } else {
            connection.disconnect();
            throw new IOException("HTTP error code: " + responseCode);
        }
    }

    public static List<String> parseConfigs(String rawData) {
        List<String> configs = new ArrayList<>();
        String[] lines = rawData.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("vless://") || line.startsWith("vmess://") || line.startsWith("trojan://")) {
                configs.add(line);
            }
        }
        return configs;
    }

    public static List<String> removeDuplicates(List<String> configs) {
        Set<String> seen = new HashSet<>();
        List<String> unique = new ArrayList<>();
        for (String config : configs) {
            String key = extractKey(config);
            if (!seen.contains(key)) {
                seen.add(key);
                unique.add(config);
            }
        }
        return unique;
    }

    private static String extractKey(String config) {
        try {
            if (config.contains("@")) {
                String afterAt = config.split("@")[1];
                String[] parts = afterAt.split(":");
                if (parts.length >= 2) {
                    return parts[0] + ":" + parts[1].split("\\?")[0];
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return config;
    }

    public static boolean tcpPing(String address, int port, int timeout) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address, port), timeout);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static String findBestConfig(String subscriptionUrl) {
        try {
            String raw = fetchSubscription(subscriptionUrl);
            List<String> allConfigs = parseConfigs(raw);
            List<String> uniqueConfigs = removeDuplicates(allConfigs);
            List<String> passedTcp = new ArrayList<>();
            for (String cfg : uniqueConfigs) {
                try {
                    String address = extractAddress(cfg);
                    int port = extractPort(cfg);
                    if (tcpPing(address, port, 2000)) {
                        passedTcp.add(cfg);
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
            if (!passedTcp.isEmpty()) {
                return passedTcp.get(0); // اولین کانفیگ سالم
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in findBestConfig", e);
        }
        return null;
    }

    private static String extractAddress(String config) {
        try {
            String afterAt = config.split("@")[1];
            String beforeColon = afterAt.split(":")[0];
            return beforeColon;
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static int extractPort(String config) {
        try {
            String afterAt = config.split("@")[1];
            String portPart = afterAt.split(":")[1];
            String portStr = portPart.split("\\?")[0];
            return Integer.parseInt(portStr);
        } catch (Exception e) {
            return 80;
        }
    }
}
