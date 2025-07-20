package alsaeeddev.filemanager.utils;

import static java.util.Objects.requireNonNull;

import android.webkit.MimeTypeMap;

import java.io.File;
import java.util.Locale;
import java.util.Objects;

public class FileUtils {
    public static String formatSize(long size) {
        if (size < 1024) return size + " B";
        int z = (63 - Long.numberOfLeadingZeros(size)) / 10;
        return String.format(Locale.getDefault(),"%.1f %sB",
                (double) size / (1L << (z * 10)),
                " KMGTPE".charAt(z));
    }

    public static boolean deleteRecursively(File file) {
        if (file.isDirectory()) {
            for (File child : requireNonNull(file.listFiles())) {
                deleteRecursively(child);
            }
        }
        return file.delete();
    }

    public static String getMimeType(String path) {
        String ext = MimeTypeMap.getFileExtensionFromUrl(path);
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                ext != null ? ext.toLowerCase(Locale.ROOT) : "");
    }
}
