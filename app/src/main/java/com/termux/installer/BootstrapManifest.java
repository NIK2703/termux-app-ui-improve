package com.termux.installer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class BootstrapManifest {

    public final String variant;
    public final String arch;
    public final String version;

    public BootstrapManifest(String variant, String arch, String version) {
        this.variant = variant;
        this.arch = arch;
        this.version = version;
    }

    public static BootstrapManifest fromZip(File zipFile) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile)) {
            ZipEntry entry = zip.getEntry("BOOTSTRAP_INFO");
            if (entry == null) {
                throw new IOException("Missing BOOTSTRAP_INFO in bootstrap zip");
            }
            Properties props = new Properties();
            try (InputStream in = zip.getInputStream(entry)) {
                props.load(in);
            }
            String variant = props.getProperty("variant");
            String arch = props.getProperty("arch");
            String version = props.getProperty("version", "0");
            if (variant == null || variant.isEmpty()) {
                throw new IOException("Missing 'variant' in BOOTSTRAP_INFO");
            }
            if (arch == null || arch.isEmpty()) {
                throw new IOException("Missing 'arch' in BOOTSTRAP_INFO");
            }
            return new BootstrapManifest(variant, arch, version);
        }
    }
}
