package org.reprap.utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.nio.channels.FileChannel;

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
        if (src.isDirectory()) {
            if (!dest.exists()) {
                dest.mkdir();
            }
            final String files[] = src.list();
            for (final String file : files) {
                final File srcFile = new File(src, file);
                final File destFile = new File(dest, file);
                copyTree(srcFile, destFile);
            }
        } else {
            copyFile(src, dest);
        }
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
}
