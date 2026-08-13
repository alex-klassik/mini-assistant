package com.miniassistant.store;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SeenStoreTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void newIdIsNotSeenByDefault() {
        SeenStore store = new SeenStore(pathTo("seen.txt"));

        assertFalse(store.isSeen("msg-1"));
    }

    @Test
    public void markSeenThenIsSeenReturnsTrue() {
        SeenStore store = new SeenStore(pathTo("seen.txt"));

        store.markSeen("msg-1");

        assertTrue(store.isSeen("msg-1"));
    }

    @Test
    public void newInstanceOverSameFileSeesPreviouslyMarkedIdsAfterRestart() {
        Path path = pathTo("seen.txt");
        SeenStore beforeRestart = new SeenStore(path);
        beforeRestart.markSeen("msg-1");

        SeenStore afterRestart = new SeenStore(path);

        assertTrue(afterRestart.isSeen("msg-1"));
        assertFalse(afterRestart.isSeen("msg-2"));
    }

    @Test
    public void toleratesMissingFileAndMissingParentDirectory() {
        SeenStore store = new SeenStore(pathTo("nested/does-not-exist-yet/seen.txt"));

        assertFalse(store.isSeen("msg-1"));
    }

    private Path pathTo(String relative) {
        return new File(tempFolder.getRoot(), relative).toPath();
    }
}
