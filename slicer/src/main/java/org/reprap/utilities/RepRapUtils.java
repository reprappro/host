package org.reprap.utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.util.Collections;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

public class RepRapUtils {
    /**
     * Copy a file from one place to another
     */
    public static void copyFile(final File src, final File dest) throws IOException {
        if (!dest.exists()) {
            dest.createNewFile();
        }

        FileChannel source = null;
        FileChannel destination = null;

        try {
            source = new FileInputStream(src).getChannel();
            destination = new FileOutputStream(dest).getChannel();
            destination.transferFrom(source, 0, source.size());
        } finally {
            if (source != null) {
                source.close();
            }
            if (destination != null) {
                destination.close();
            }
        }
    }

    /**
     * Copy a directory tree from one place to another
     */
    public static void copyTree(final File src, final File dest) throws IOException {
        FileUtils.copyDirectory(src, dest);
    }

    public static int countLines(final File f) throws IOException {
        final LineNumberReader reader = new LineNumberReader(new InputStreamReader(new FileInputStream(f)));
        try {
            while ((reader.readLine()) != null) {
            }
            return reader.getLineNumber();
        } finally {
            reader.close();
        }
    }

    public static void copyResourceTree(final URL source, final File target) throws IOException {
        switch (source.getProtocol()) {
        case "file":
            copyTree(toFile(source), target);
            break;
        case "jar":
            copyJarTree(source, target);
            break;
        default:
            throw new IllegalArgumentException("Cant copy resource stream from " + source);
        }
    }

    private static File toFile(final URL source) {
        return new File(URI.create(source.toString()));
    }

    private static void copyJarTree(final URL source, final File target) throws IOException {
        final JarURLConnection jarConnection = (JarURLConnection) source.openConnection();
        final String prefix = jarConnection.getEntryName();
        final JarFile jarFile = jarConnection.getJarFile();
        for (final JarEntry jarEntry : Collections.list(jarFile.entries())) {
            final String entryName = jarEntry.getName();
            if (entryName.startsWith(prefix)) {
                if (!jarEntry.isDirectory()) {
                    final String fileName = StringUtils.removeStart(entryName, prefix);
                    final InputStream fileStream = jarFile.getInputStream(jarEntry);
                    try {
                        FileUtils.copyInputStreamToFile(fileStream, new File(target, fileName));
                    } finally {
                        fileStream.close();
                    }
                }
            }
        }
    }
}
