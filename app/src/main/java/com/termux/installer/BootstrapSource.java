package com.termux.installer;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.Iterator;

public class BootstrapSource implements Serializable {

    public final String id;
    public final String name;
    public final String description;
    public final String urlTemplate;
    public final String sha256;
    public final long size;
    public final String variant;

    public BootstrapSource(String id, String name, String description, String urlTemplate,
                           String sha256, long size, String variant) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.urlTemplate = urlTemplate;
        this.sha256 = sha256;
        this.size = size;
        this.variant = variant;
    }

    public String resolveUrl(String arch) {
        return urlTemplate.replace("{arch}", arch);
    }

    public static BootstrapSource fromJson(JSONObject obj, String deviceArch) throws JSONException {
        String urlTemplate = obj.getString("urlTemplate");

        JSONObject sha256ByArch = obj.optJSONObject("sha256ByArch");
        String sha256 = null;
        if (sha256ByArch != null) {
            if (sha256ByArch.has(deviceArch)) {
                sha256 = sha256ByArch.getString(deviceArch);
            } else {
                // fallback: take first available
                Iterator<String> keys = sha256ByArch.keys();
                if (keys.hasNext()) {
                    sha256 = sha256ByArch.getString(keys.next());
                }
            }
        }

        return new BootstrapSource(
            obj.getString("id"),
            obj.optString("name", obj.getString("id")),
            obj.optString("description", ""),
            urlTemplate,
            sha256,
            obj.optLong("size", 0),
            obj.optString("variant", obj.getString("id"))
        );
    }

    @Override
    public String toString() {
        return name;
    }
}
