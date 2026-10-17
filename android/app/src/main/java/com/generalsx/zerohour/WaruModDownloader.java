package com.generalsx.zerohour;

import android.os.Handler;
import android.os.Looper;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mod catalogue downloader adapted from GeneralsXZH-Android's ModDownloader.
 *
 * Files are fetched from GenLauncher's public MinIO/S3 catalogue. The catalogue
 * host currently supports HTTP only, so callers must warn the user before a
 * download. Object sizes and available single-part MD5 ETags are checked before
 * files are promoted from temporary files into the selected mod directory.
 */
final class WaruModDownloader {
    static final String HOST = "http://gen.insave.ovh:9000";

    static final class Catalog {
        final String name;
        final String kind;
        final String bucket;
        final String folder;

        Catalog(String name, String kind, String bucket, String folder) {
            this.name = name;
            this.kind = kind;
            this.bucket = bucket;
            this.folder = folder;
        }

        @Override public String toString() { return name + " (" + kind + ")"; }
    }

    // Names, buckets and prefixes from GenLauncherCfg.yaml in the reference project.
    static final Catalog[] CATALOG = {
        new Catalog("Rise of the Reds", "Mod", "rotr", "rotr-individual-files"),
        new Catalog("Hanpatch", "Patch", "rotr", "hanpatch-individual-files"),
        new Catalog("Anti Thesis Patch", "Patch", "atp", "atp-files"),
        new Catalog("ATP Boss Addon", "Addon", "atp", "boss-addon-atp"),
        new Catalog("ATP ControlBarPro Addon", "Addon", "atp", "cbp-addon-atp"),
        new Catalog("Empire Pacific Alliance", "Patch", "rotrepa", "rotrepaA"),
        new Catalog("EPA No MechFactions", "Addon", "rotrepa", "CNC_EPAADONB"),
        new Catalog("Unofficial Music Factions", "Addon", "rotrepa", "rotrepaM"),
        new Catalog("Vanilla Campaigns Remastered", "Addon", "vcr", "Vanilla Campaigns Remastered")
    };

    interface Listener {
        void onProgress(String message, int percent);
        void onFinished(boolean ok, String message);
    }

    interface SizeListener {
        void onSize(boolean ok, int files, long bytes, String error);
    }

    private static final class RemoteFile {
        final String key;
        final long size;
        final String md5;

        RemoteFile(String key, long size, String etag) {
            this.key = key;
            this.size = size;
            String clean = etag == null ? "" : etag.trim().replace("\"", "");
            this.md5 = clean.matches("(?i)[0-9a-f]{32}") ? clean : null;
        }

        String fileName() {
            int slash = key.lastIndexOf('/');
            return slash >= 0 ? key.substring(slash + 1) : key;
        }
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    void cancel() { cancelled.set(true); }

    void querySize(final Catalog entry, final SizeListener listener) {
        worker.execute(() -> {
            try {
                List<RemoteFile> files = list(entry);
                long bytes = 0L;
                for (RemoteFile f : files) bytes += f.size;
                if (files.isEmpty()) {
                    postSize(listener, false, 0, 0,
                        "The catalogue returned no files for " + entry.name + ". The upstream host may have changed.");
                } else {
                    postSize(listener, true, files.size(), bytes, null);
                }
            } catch (Exception e) {
                postSize(listener, false, 0, 0, readableError(e));
            }
        });
    }

    void download(final Catalog entry, final File destDir, final Listener listener) {
        cancelled.set(false);
        worker.execute(() -> {
            try {
                post(listener, "Contacting mod catalogue…", 0);
                List<RemoteFile> files = list(entry);
                if (files.isEmpty()) {
                    finish(listener, false, "The catalogue returned no files for " + entry.name + ".");
                    return;
                }

                long totalBytes = 0L;
                Set<String> names = new HashSet<>();
                for (RemoteFile f : files) {
                    totalBytes += f.size;
                    // The upstream catalogue is made of individual files. Its reference
                    // downloader places leaf names in the mod root because the engine
                    // expects mod archives there. Fail on collisions instead of silently
                    // replacing one different file with another.
                    if (!names.add(f.fileName().toLowerCase(Locale.ROOT))) {
                        finish(listener, false, "The catalogue has duplicate file names. Nothing was installed; use the upstream launcher or a corrected catalogue.");
                        return;
                    }
                }

                if (!destDir.isDirectory() && !destDir.mkdirs()) {
                    finish(listener, false, "Could not create mod directory:\n" + destDir.getAbsolutePath());
                    return;
                }

                long done = 0L;
                int downloaded = 0;
                int skipped = 0;
                for (RemoteFile remote : files) {
                    if (cancelled.get()) {
                        finish(listener, false, "Download cancelled.");
                        return;
                    }
                    File output = new File(destDir, remote.fileName());
                    if (!isDirectChild(destDir, output)) {
                        finish(listener, false, "Unsafe catalogue filename refused: " + remote.fileName());
                        return;
                    }
                    if (output.isFile() && output.length() == remote.size
                            && (remote.md5 == null || remote.md5.equalsIgnoreCase(md5Of(output)))) {
                        done += remote.size;
                        skipped++;
                        post(listener, "Already downloaded: " + output.getName(), percent(done, totalBytes));
                        continue;
                    }

                    post(listener, "Downloading " + output.getName() + " (" + human(remote.size) + ")",
                        percent(done, totalBytes));
                    String error = fetch(entry, remote, output, done, totalBytes, listener);
                    if (error != null) {
                        finish(listener, false, "Download failed for " + output.getName() + ":\n" + error);
                        return;
                    }
                    done += remote.size;
                    downloaded++;
                }

                if (cancelled.get()) {
                    finish(listener, false, "Download cancelled.");
                    return;
                }
                finish(listener, true, entry.name + " download completed.\n"
                    + downloaded + " downloaded, " + skipped + " already verified.\n"
                    + "Total: " + human(totalBytes) + "\n" + destDir.getAbsolutePath());
            } catch (Exception e) {
                finish(listener, false, "Could not download " + entry.name + ":\n" + readableError(e));
            }
        });
    }

    private List<RemoteFile> list(Catalog entry) throws Exception {
        List<RemoteFile> output = new ArrayList<>();
        String token = null;
        for (int page = 0; page < 40; page++) {
            String url = HOST + "/" + entry.bucket
                + "?list-type=2&max-keys=1000&prefix="
                + URLEncoder.encode(entry.folder + "/", "UTF-8");
            if (token != null) {
                url += "&continuation-token=" + URLEncoder.encode(token, "UTF-8");
            }

            HttpURLConnection connection = open(url);
            try (InputStream input = connection.getInputStream()) {
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) throw new IOException("Catalogue HTTP status " + code);
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(input, "UTF-8");
                String key = null;
                String etag = null;
                long size = -1;
                boolean inContents = false;
                boolean truncated = false;
                String nextToken = null;

                int event = parser.getEventType();
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        String tag = parser.getName();
                        if ("Contents".equals(tag)) {
                            inContents = true;
                            key = null;
                            etag = null;
                            size = -1;
                        } else if (inContents && ("Key".equals(tag) || "ETag".equals(tag)
                                || "Size".equals(tag))) {
                            String value = parser.nextText();
                            if ("Key".equals(tag)) key = value;
                            else if ("ETag".equals(tag)) etag = value;
                            else {
                                try { size = Long.parseLong(value.trim()); }
                                catch (NumberFormatException ignored) { size = -1; }
                            }
                        } else if ("IsTruncated".equals(tag)) {
                            truncated = "true".equalsIgnoreCase(parser.nextText().trim());
                        } else if ("NextContinuationToken".equals(tag)) {
                            nextToken = parser.nextText();
                        }
                    } else if (event == XmlPullParser.END_TAG && "Contents".equals(parser.getName())) {
                        if (inContents && key != null && !key.endsWith("/") && size >= 0) {
                            output.add(new RemoteFile(key, size, etag));
                        }
                        inContents = false;
                    }
                    event = parser.next();
                }

                if (!truncated) break;
                if (nextToken == null || nextToken.isEmpty()) {
                    throw new IOException("Catalogue paging token missing; refusing an incomplete listing.");
                }
                token = nextToken;
            } finally {
                connection.disconnect();
            }
        }
        return output;
    }

    private String fetch(Catalog entry, RemoteFile remote, File output,
                         long bytesBeforeFile, long totalBytes, Listener listener) {
        File temp = null;
        HttpURLConnection connection = null;
        boolean promoted = false;
        try {
            StringBuilder url = new StringBuilder(HOST).append('/').append(entry.bucket).append('/');
            String[] segments = remote.key.split("/");
            for (int i = 0; i < segments.length; i++) {
                if (i != 0) url.append('/');
                url.append(URLEncoder.encode(segments[i], "UTF-8").replace("+", "%20"));
            }

            connection = open(url.toString());
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) return "server returned HTTP " + status;

            File parent = output.getParentFile();
            if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
                return "could not create destination directory";
            }
            temp = File.createTempFile(output.getName() + ".part-", ".tmp", parent);
            MessageDigest digest = MessageDigest.getInstance("MD5");
            long readTotal = 0L;
            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    if (cancelled.get()) return "cancelled";
                    out.write(buffer, 0, count);
                    digest.update(buffer, 0, count);
                    readTotal += count;
                    int currentPercent = totalBytes <= 0 ? 0
                        : (int) Math.min(99, ((bytesBeforeFile + readTotal) * 100L) / totalBytes);
                    post(listener, "Downloading " + output.getName() + " (" + human(readTotal) + ")",
                        currentPercent);
                }
                out.flush();
                out.getFD().sync();
            }

            if (remote.size >= 0 && readTotal != remote.size) {
                return "size mismatch; expected " + remote.size + " bytes, received " + readTotal + ".";
            }
            if (remote.md5 != null && !remote.md5.equalsIgnoreCase(hex(digest.digest()))) {
                return "MD5 checksum mismatch. The incomplete/corrupted file was discarded.";
            }
            if (cancelled.get()) return "cancelled";

            File backup = null;
            if (output.exists()) {
                backup = new File(output.getAbsolutePath() + ".waru-old");
                if (backup.exists() && !backup.delete()) return "could not replace the previous file";
                if (!output.renameTo(backup)) return "could not preserve the previous file before replacement";
            }
            if (!temp.renameTo(output)) {
                if (backup != null) backup.renameTo(output);
                return "could not finalize the downloaded file";
            }
            promoted = true;
            if (backup != null) backup.delete();
            return null;
        } catch (Exception e) {
            return readableError(e);
        } finally {
            if (connection != null) connection.disconnect();
            if (!promoted && temp != null) temp.delete();
        }
    }

    private HttpURLConnection open(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(60000);
        connection.setRequestMethod("GET");
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "Waru-Edition-Android-Launcher");
        return connection;
    }

    private static boolean isDirectChild(File root, File file) {
        try {
            return file.getCanonicalFile().getParentFile().equals(root.getCanonicalFile());
        } catch (IOException e) {
            return false;
        }
    }

    private static String md5Of(File file) {
        try (InputStream input = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            return hex(digest.digest());
        } catch (Exception e) {
            return "";
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) result.append(String.format(Locale.ROOT, "%02x", b));
        return result.toString();
    }

    static String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static int percent(long done, long total) {
        return total <= 0 ? 0 : (int) Math.min(100, done * 100L / total);
    }

    private static String readableError(Exception e) {
        String message = e.getMessage();
        return message == null || message.isEmpty() ? e.getClass().getSimpleName() : message;
    }

    private void post(Listener listener, String message, int percent) {
        main.post(() -> listener.onProgress(message, percent));
    }

    private void finish(Listener listener, boolean ok, String message) {
        main.post(() -> listener.onFinished(ok, message));
    }

    private void postSize(SizeListener listener, boolean ok, int files, long bytes, String error) {
        main.post(() -> listener.onSize(ok, files, bytes, error));
    }
}
