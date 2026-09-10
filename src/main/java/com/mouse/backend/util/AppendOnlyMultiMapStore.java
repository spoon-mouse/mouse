package com.mouse.backend.util;

import com.mouse.backend.Kit;
import com.mouse.backend.csv.CsvScriptExtension;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A simple, thread-safe, append-only persisted multimap store.
 * Each key maps to a SET of values (like Guava's Multimap), rather
 * than a single value.
 *
 * Design:
 *  - All data lives in memory (a HashMap<String, LinkedHashSet<String>>)
 *    for fast reads. LinkedHashSet keeps values in insertion order.
 *  - Every mutation is appended as a single line to a log file
 *    (no rewriting the whole file on each write).
 *  - On construction, the log is replayed from the start to rebuild
 *    the in-memory multimap.
 *  - Each append is flushed + fsynced before returning, so a write
 *    that returns successfully is durable on disk.
 *
 * Log line format (tab-separated, values escaped):
 *   ADD\t<key>\t<value>       -- add one value to key's set
 *   DEL\t<key>\t<value>       -- remove one value from key's set
 *   DELALL\t<key>             -- remove all values for key
 *
 * Limitations (by design, for simplicity):
 *  - The log file grows forever — every add/remove appends a line.
 *    Call compact() periodically if you're doing lots of writes over
 *    a long running process.
 *  - Keys/values are Strings; escape/encode if you need binary data.
 */
public class AppendOnlyMultiMapStore implements Closeable {

    private static Logger log = LoggerFactory.getLogger(AppendOnlyMultiMapStore.class);

    private static final String ADD = "ADD";
    private static final String DEL = "DEL";
    private static final String DELALL = "DELALL";

    private final Path logPath;
    private final Map<String, LinkedHashSet<String>> data = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private BufferedWriter writer;

    public AppendOnlyMultiMapStore(String logFilePath) throws IOException {
        this.logPath = Paths.get(logFilePath);
        replay();
        openWriterForAppend();
    }

    /** Replay the log from disk to rebuild the in-memory multimap. */
    private void replay() throws IOException {
        if (!Files.exists(logPath)) {
            return;
        }
        List<String> lines = Files.readAllLines(logPath, StandardCharsets.UTF_8);
        for (String line : lines) {
            if (line.isEmpty()) continue;
            String[] parts = line.split("\t", 3);
            if (parts.length < 2) continue; // skip malformed/truncated lines

            String op = parts[0];
            String key = unescape(parts[1]);

            if (ADD.equals(op) && parts.length == 3) {
                data.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(unescape(parts[2]));
            } else if (DEL.equals(op) && parts.length == 3) {
                LinkedHashSet<String> set = data.get(key);
                if (set != null) {
                    set.remove(unescape(parts[2]));
                    if (set.isEmpty()) data.remove(key);
                }
            } else if (DELALL.equals(op)) {
                data.remove(key);
            }
            // any other/malformed op is silently skipped (robust to a
            // partially-written last line from a crash mid-append)
        }
    }

    private void openWriterForAppend() throws IOException {
        writer = Files.newBufferedWriter(
                logPath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    /** Add a value to the set for this key. No-op (and no log entry) if already present. */
    public void put(String key, String value) throws IOException {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        lock.writeLock().lock();
        try {
            LinkedHashSet<String> set = data.computeIfAbsent(key, k -> new LinkedHashSet<>());
            if (set.add(value)) {
                appendLine(ADD + "\t" + escape(key) + "\t" + escape(value));
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Remove a single value from the set for this key. */
    public void remove(String key, String value) throws IOException {
        lock.writeLock().lock();
        try {
            LinkedHashSet<String> set = data.get(key);
            if (set != null && set.remove(value)) {
                appendLine(DEL + "\t" + escape(key) + "\t" + escape(value));
                if (set.isEmpty()) {
                    data.remove(key);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Remove all values for this key. */
    public void removeAll(String key) throws IOException {
        lock.writeLock().lock();
        try {
            if (data.containsKey(key)) {
                appendLine(DELALL + "\t" + escape(key));
                data.remove(key);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Returns an unmodifiable snapshot of the values for a key (empty set if key not present). */
    public Set<String> get(String key) {
        lock.readLock().lock();
        try {
            LinkedHashSet<String> set = data.get(key);
            return set == null ? Collections.emptySet() : new LinkedHashSet<>(set);
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean containsKey(String key) {
        lock.readLock().lock();
        try {
            return data.containsKey(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean containsEntry(String key, String value) {
        lock.readLock().lock();
        try {
            LinkedHashSet<String> set = data.get(key);
            return set != null && set.contains(value);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Set<String> keys() {
        lock.readLock().lock();
        try {
            return new HashSet<>(data.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Total number of key-value pairs (not number of keys). */
    public int size() {
        lock.readLock().lock();
        try {
            int total = 0;
            for (LinkedHashSet<String> set : data.values()) {
                total += set.size();
            }
            return total;
        } finally {
            lock.readLock().unlock();
        }
    }

    private void appendLine(String line) throws IOException {
        writer.write(line);
        writer.newLine();
        writer.flush(); // ensure it leaves the JVM
        // fsync to disk so a returned mutation is actually durable
        // (skip this if you want higher throughput and can tolerate
        // losing the last few writes on an OS crash / power loss)
        try (FileChannel channel = FileChannel.open(logPath, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    /**
     * Rewrites the log to contain only the current state (one ADD line
     * per live entry, no removed history). Useful to call periodically
     * if the log has grown large relative to the actual data size.
     */
    public void compact() throws IOException {
        lock.writeLock().lock();
        try {
            Path tempFile = Files.createTempFile(
                    logPath.toAbsolutePath().getParent() != null
                            ? logPath.toAbsolutePath().getParent()
                            : Paths.get("."),
                    "mmlog", ".tmp");
            try (BufferedWriter tempWriter = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                for (Map.Entry<String, LinkedHashSet<String>> entry : data.entrySet()) {
                    for (String value : entry.getValue()) {
                        tempWriter.write(ADD + "\t" + escape(entry.getKey()) + "\t" + escape(value));
                        tempWriter.newLine();
                    }
                }
            }
            writer.close();
            try {
                Files.move(tempFile, logPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, logPath, StandardCopyOption.REPLACE_EXISTING);
            }
            openWriterForAppend();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i);
                switch (next) {
                    case 't': sb.append('\t'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case '\\': sb.append('\\'); break;
                    default: sb.append(next);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override
    public void close() throws IOException {
        lock.writeLock().lock();
        try {
            if (writer != null) {
                writer.close();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }


    public void restoreRedeemScripts(Wallet wallet, CsvScriptExtension csv) {

        data.keySet().stream().map(wallet::parseAddress).filter(wallet::isAddressMine).forEach(address ->
                data.get(address.toString()).forEach(script -> {
                    csv.addRedeemScript(script);
                    log.info("Restoring redeem script: {}", script);
                })
        );
    }

}
