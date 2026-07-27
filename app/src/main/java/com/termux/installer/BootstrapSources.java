package com.termux.installer;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class BootstrapSources {

    private BootstrapSources() {}

    public static List<BootstrapSource> loadFromResources(Context context) throws IOException, JSONException {
        String deviceArch = AbiUtils.getDeviceArch();
        String json;
        try (InputStream in = context.getResources().openRawResource(
                com.termux.R.raw.bootstrap_sources)) {
            byte[] bytes = new byte[in.available()];
            int offset = 0;
            while (offset < bytes.length) {
                int read = in.read(bytes, offset, bytes.length - offset);
                if (read == -1) break;
                offset += read;
            }
            json = new String(bytes, StandardCharsets.UTF_8);
        }

        JSONObject root = new JSONObject(json);
        JSONArray sourcesArray = root.getJSONArray("sources");
        List<BootstrapSource> sources = new ArrayList<>(sourcesArray.length());
        for (int i = 0; i < sourcesArray.length(); i++) {
            sources.add(BootstrapSource.fromJson(sourcesArray.getJSONObject(i), deviceArch));
        }
        return sources;
    }
}
