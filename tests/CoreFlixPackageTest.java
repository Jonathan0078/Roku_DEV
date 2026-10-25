package br.com.jonathansistemas.brsmobile;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

public class CoreFlixPackageTest {
    interface Checked {void run() throws Exception;}
    static int checks;
    static void rejected(Checked call) throws Exception {
        try {call.run();} catch (Exception expected) {checks++; return;}
        throw new AssertionError("Invalid update was accepted");
    }
    static String hash(File file) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath()));
        StringBuilder value = new StringBuilder();
        for (byte b : digest) value.append(String.format(Locale.ROOT, "%02x", b & 255));
        return value.toString();
    }
    public static void main(String[] args) throws Exception {
        File real = new File(args[0]);
        CoreFlixPackage.validate(real, hash(real), "0.7.0"); checks++;
        if (CoreFlixPackage.versionCode("0.10.0") <= CoreFlixPackage.versionCode("0.9.99")) throw new AssertionError("Version sorting"); checks++;
        rejected(() -> CoreFlixPackage.versionCode("0.7.0-beta"));
        rejected(() -> CoreFlixPackage.versionCode("1.1000.0"));
        CoreFlixPackage.trustedUrl(CoreFlixPackage.FEED); checks++;
        for (String url : new String[]{"http://jonathan-sistemas.com.br/coreflix/update.zip", "https://evil.test/coreflix/update.zip",
            "https://jonathan-sistemas.com.br/coreflix/../update.zip", "https://jonathan-sistemas.com.br/coreflix/%2e%2e/update.zip",
            "https://user@jonathan-sistemas.com.br/coreflix/update.zip", "https://jonathan-sistemas.com.br:8443/coreflix/update.zip"}) {
            rejected(() -> CoreFlixPackage.trustedUrl(url));
        }
        rejected(() -> CoreFlixPackage.validate(real, "0".repeat(64), "0.7.0"));
        rejected(() -> CoreFlixPackage.validate(real, hash(real), "0.8.0"));
        File fixture = File.createTempFile("coreflix-update-test", ".zip");
        try {
            for (String title : new String[]{"OtherApp", "CoreFlix"}) {
                try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(fixture))) {
                    zip.putNextEntry(new ZipEntry(title.equals("CoreFlix") ? "nested/manifest" : "manifest"));
                    zip.write(("title=" + title + "\nmajor_version=0\nminor_version=7\nbuild_version=0\n").getBytes()); zip.closeEntry();
                    zip.putNextEntry(new ZipEntry("source/main.brs")); zip.write("sub Main()\nend sub".getBytes()); zip.closeEntry();
                }
                rejected(() -> CoreFlixPackage.validate(fixture, hash(fixture), "0.7.0"));
            }
        } finally {fixture.delete();}
        System.out.println("PASS: CoreFlix update validation (" + checks + " checks)");
    }
}
