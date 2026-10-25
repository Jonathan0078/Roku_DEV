package br.com.jonathansistemas.brsmobile;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Validation shared by the native updater and local JVM regression tests. */
final class CoreFlixPackage {
    static final String FEED = "https://jonathan-sistemas.com.br/coreflix/roku-update.json";
    static final long MAX_BYTES = 64L * 1024 * 1024;

    static URL trustedUrl(String value) throws IOException {
        URL url = new URL(value);
        if (!"https".equals(url.getProtocol()) || !"jonathan-sistemas.com.br".equalsIgnoreCase(url.getHost())
                || (url.getPort() != -1 && url.getPort() != 443) || url.getUserInfo() != null
                || !url.getPath().startsWith("/coreflix/") || url.getPath().contains("..")
                || url.getPath().contains("%") || url.getRef() != null || url.getQuery() != null) {
            throw new IOException("Endereço de atualização inválido.");
        }
        return url;
    }

    static long versionCode(String version) throws IOException {
        if (version == null || !version.matches("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,5}"))
            throw new IOException("Versão inválida.");
        String[] parts = version.split("\\.");
        return Long.parseLong(parts[0]) * 100000000L + Long.parseLong(parts[1]) * 100000L + Long.parseLong(parts[2]);
    }

    static void validate(File file, String expectedHash, String version) throws Exception {
        if (file.length() <= 0 || file.length() > MAX_BYTES) throw new IOException("Tamanho de pacote inválido.");
        if (expectedHash == null || !expectedHash.matches("[a-fA-F0-9]{64}")) throw new IOException("SHA-256 inválido.");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[32768]; int n;
            while ((n = in.read(bytes)) != -1) digest.update(bytes, 0, n);
        }
        StringBuilder hash = new StringBuilder();
        for (byte b : digest.digest()) hash.append(String.format(Locale.ROOT, "%02x", b & 255));
        if (!hash.toString().equalsIgnoreCase(expectedHash)) throw new IOException("O pacote não corresponde ao SHA-256 publicado.");
        try (ZipFile zip = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            long total = 0; int count = 0; int manifests = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++count > 5000 || entry.getSize() < 0 || (total += entry.getSize()) > MAX_BYTES * 4)
                    throw new IOException("Pacote excede os limites de conteúdo.");
                if (entry.getName().equals("manifest")) manifests++;
                if (entry.getName().startsWith("/") || entry.getName().contains("..") || entry.getName().contains("\\"))
                    throw new IOException("Caminho inválido no pacote.");
            }
            ZipEntry manifest = zip.getEntry("manifest");
            if (manifests != 1 || manifest.isDirectory() || manifest.getSize() > 65536 || zip.getEntry("source/main.brs") == null)
                throw new IOException("Pacote CoreFlix sem manifest ou inicialização válida.");
            Properties props = new Properties();
            try (InputStream input = zip.getInputStream(manifest); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int n;
                while ((n = input.read(buffer)) != -1) {
                    if (bytes.size() + n > 65536) throw new IOException("Manifest excede o limite permitido.");
                    bytes.write(buffer, 0, n);
                }
                props.load(new StringReader(new String(bytes.toByteArray(), StandardCharsets.UTF_8)));
            }
            String actual = props.getProperty("major_version", "") + "." + props.getProperty("minor_version", "") + "." + props.getProperty("build_version", "");
            if (!"CoreFlix".equals(props.getProperty("title")) || versionCode(actual) != versionCode(version))
                throw new IOException("Identidade ou versão do pacote diferente da atualização.");
        }
    }
}
