package org.reprap.utilities;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Gets round the fact that Java DeleteOnExit() doesn't do it in the right
 * order.
 */
public class RrDeleteOnExit {
    private final List<File> toDelete = new ArrayList<File>();

    public void add(final File f) {
        toDelete.add(f);
    }

    public void killThem() {
        Collections.reverse(toDelete);
        for (final File file : toDelete) {
            if (!file.delete()) {
                Debug.e("RrDeleteOnExit.killThem(): Unable to delete: " + file.getAbsolutePath());
            }
        }
        toDelete.clear();
    }
}
