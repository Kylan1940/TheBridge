package org.kylan1940.thebridge.reset;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BlockTrackerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final BlockKey a = new BlockKey("world", 1, 64, 1);
    private final BlockKey b = new BlockKey("world", 2, 64, 1);
    private final BlockKey c = new BlockKey("world", 3, 64, 1);

    @Test
    public void placedBlockIsRememberedAsPlaced() {
        BlockTracker tracker = new BlockTracker();
        tracker.recordPlaced(a, "minecraft:air");
        assertTrue(tracker.isPlaced(a));
        assertFalse(tracker.isPlaced(b));
        assertEquals(1, tracker.size());
    }

    @Test
    public void firstOriginalStateWins() {
        BlockTracker tracker = new BlockTracker();
        tracker.recordPlaced(a, "minecraft:air");
        // The player breaks it and somebody places another block there later.
        tracker.recordBroken(a, "minecraft:red_terracotta");
        tracker.recordPlaced(a, "minecraft:air");

        List<Map.Entry<BlockKey, String>> batch = tracker.takeBatch(10);
        assertEquals(1, batch.size());
        assertEquals("minecraft:air", batch.get(0).getValue());
    }

    @Test
    public void breakingAMapBlockIsRestoredToThatBlock() {
        BlockTracker tracker = new BlockTracker();
        tracker.recordBroken(a, "minecraft:stone");
        assertFalse(tracker.isPlaced(a));
        assertEquals("minecraft:stone", tracker.takeBatch(10).get(0).getValue());
    }

    @Test
    public void brokenBlockIsNoLongerPlaced() {
        BlockTracker tracker = new BlockTracker();
        tracker.recordPlaced(a, "minecraft:air");
        tracker.recordBroken(a, "minecraft:red_terracotta");
        assertFalse(tracker.isPlaced(a));
    }

    @Test
    public void batchesAreSmallAndEmptyTheTracker() {
        BlockTracker tracker = new BlockTracker();
        tracker.recordPlaced(a, "minecraft:air");
        tracker.recordPlaced(b, "minecraft:air");
        tracker.recordPlaced(c, "minecraft:air");

        assertEquals(2, tracker.takeBatch(2).size());
        assertEquals(1, tracker.size());
        assertEquals(1, tracker.takeBatch(2).size());
        assertTrue(tracker.isEmpty());
        assertTrue(tracker.takeBatch(2).isEmpty());
        assertFalse(tracker.isPlaced(a));
    }

    @Test
    public void clearForgetsEverything() {
        BlockTracker tracker = new BlockTracker();
        tracker.recordPlaced(a, "minecraft:air");
        tracker.clear();
        assertTrue(tracker.isEmpty());
        assertFalse(tracker.isPlaced(a));
    }

    @Test
    public void journalSurvivesSaveAndLoad() throws Exception {
        BlockTracker tracker = new BlockTracker();
        tracker.recordPlaced(a, "minecraft:air");
        tracker.recordBroken(b, "minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]");
        assertTrue(tracker.isDirty());

        File file = new File(folder.getRoot(), "journals/test.journal");
        tracker.saveTo(file);
        assertFalse(tracker.isDirty());
        assertTrue(file.exists());

        BlockTracker loaded = BlockTracker.loadFrom(file);
        assertEquals(2, loaded.size());
        List<Map.Entry<BlockKey, String>> batch = loaded.takeBatch(10);
        assertEquals(a, batch.get(0).getKey());
        assertEquals("minecraft:air", batch.get(0).getValue());
        assertEquals("minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]", batch.get(1).getValue());
    }

    @Test
    public void damagedJournalLinesAreSkipped() throws Exception {
        File file = folder.newFile("broken.journal");
        java.nio.file.Files.write(file.toPath(),
                ("world\t1\t2\t3\tminecraft:air\n"
                        + "garbage line\n"
                        + "world\tx\ty\tz\tminecraft:stone\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        BlockTracker loaded = BlockTracker.loadFrom(file);
        assertEquals(1, loaded.size());
    }
}
