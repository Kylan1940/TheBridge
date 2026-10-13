package org.kylan1940.thebridge.reset;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BlockTracker {

    private final Map<BlockKey, String> originals = new LinkedHashMap<>();
    private final Set<BlockKey> placed = new HashSet<>();

    private boolean dirty = false;

    /** A player placed a block. original is what was there before (usually air). */
    public void recordPlaced(BlockKey key, String original) {
        remember(key, original);
        placed.add(key);
    }

    /** A block was broken (by a player or an explosion). before is what it looked like. */
    public void recordBroken(BlockKey key, String before) {
        remember(key, before);
        placed.remove(key);
    }

    private void remember(BlockKey key, String original) {
        // putIfAbsent: if the block was changed twice we must restore the FIRST state.
        if (originals.putIfAbsent(key, original) == null) {
            dirty = true;
        }
    }

    public boolean isPlaced(BlockKey key) {
        return placed.contains(key);
    }

    public int size() {
        return originals.size();
    }

    public boolean isEmpty() {
        return originals.isEmpty();
    }

    /** True when the tracker changed since the last time the journal was saved. */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Removes up to max entries from the tracker and returns them. The caller restores
     * those blocks. Calling this again and again until it returns an empty list
     * restores the whole arena in small steps (so the server never freezes).
     */
    public List<Map.Entry<BlockKey, String>> takeBatch(int max) {
        List<Map.Entry<BlockKey, String>> batch = new ArrayList<>();
        Iterator<Map.Entry<BlockKey, String>> iterator = originals.entrySet().iterator();

        while (iterator.hasNext() && batch.size() < max) {
            Map.Entry<BlockKey, String> entry = iterator.next();
            batch.add(Map.entry(entry.getKey(), entry.getValue()));
            placed.remove(entry.getKey());
            iterator.remove();
            dirty = true;
        }
        return batch;
    }

    public void clear() {
        originals.clear();
        placed.clear();
        dirty = true;
    }

    // ------------------------------------------------------------------
    // Journal file (crash recovery)
    // ------------------------------------------------------------------

    public void saveTo(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        File temp = new File(file.getPath() + ".tmp");

        try (BufferedWriter writer = Files.newBufferedWriter(temp.toPath(), StandardCharsets.UTF_8)) {
            for (Map.Entry<BlockKey, String> entry : originals.entrySet()) {
                BlockKey key = entry.getKey();
                // Tab separated. Block data strings never contain tabs or new lines.
                writer.write(key.world() + "\t" + key.x() + "\t" + key.y() + "\t" + key.z() + "\t" + entry.getValue());
                writer.newLine();
            }
        }
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        dirty = false;
    }

    public static BlockTracker loadFrom(File file) throws IOException {
        BlockTracker tracker = new BlockTracker();

        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t", 5);
                if (parts.length < 5) {
                    continue;
                }
                try {
                    BlockKey key = new BlockKey(
                            parts[0],
                            Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2]),
                            Integer.parseInt(parts[3])
                    );
                    tracker.originals.putIfAbsent(key, parts[4]);
                } catch (NumberFormatException exception) {
                    // Skip this line, keep reading the rest.
                }
            }
        }
        tracker.dirty = false;
        return tracker;
    }
}
