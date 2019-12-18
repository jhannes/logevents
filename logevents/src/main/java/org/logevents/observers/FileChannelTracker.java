package org.logevents.observers;

import org.logevents.status.LogEventStatus;
import org.logevents.util.ExceptionUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utility class to keep open files for {@link FileLogEventObserver} for efficiency. Will close
 * files when necessary to avoid resource leak.
 */
public class FileChannelTracker {

    private int maxOpenChannels = 100;
    private boolean lockOnWrite;

    public FileChannelTracker(boolean lockOnWrite) {
        this.lockOnWrite = lockOnWrite;
    }

    public void setMaxOpenChannels(int maxOpenChannels) {
        this.maxOpenChannels = maxOpenChannels;
    }

    static class Entry<T> {
        private final T target;
        private Instant accessTime;

        private Entry(T target) {
            this.target = target;
        }

        public T getTarget() {
            return target;
        }

        public Instant getAccessTime() {
            return accessTime;
        }

        public void setAccessTime(Instant accessTime) {
            this.accessTime = accessTime;
        }
    }


    private Map<Path, Entry<FileChannel>> channels = Collections.synchronizedMap(new LinkedHashMap<>());

    private Duration timeout = Duration.ofMinutes(10);

    public void writeToFile(Path path, String message) throws IOException {
        FileChannel channel = getChannel(path, Instant.now());
        try {
            ByteBuffer src = ByteBuffer.wrap(message.getBytes());
            if (lockOnWrite) {
                try(FileLock ignored = channel.tryLock()) {
                    channel.write(src);
                }
            } else {
                channel.write(src);
            }
        } catch (IOException e) {
            try {
                channel.close();
            } catch (IOException ignored) {
            }
            channels.remove(path);
            throw e;
        }
    }

    /**
     * Returns a channel for the specified path, considering it as opened with the now parameter. If the
     * requested channel is already opened, this is returned, otherwise a new channel is returned. If a
     * new channel is opened, the {@link FileChannelTracker} reviews existing opened channels and make sure
     * that no channel has been idle for longer than {@link #timeout} and that now more than {@link #maxOpenChannels}
     * are opened. If channels need to be closed, the least recently accessed channels are closed first.
     *
     * @throws IOException if openChannel throws
     */
    FileChannel getChannel(Path path, Instant now) throws IOException {
        Entry<FileChannel> result = channels.get(path);
        if (result == null) {
            result = openChannel(path, now);
            channels.put(path, result);
        }
        result.setAccessTime(now);
        return result.getTarget();
    }

    Entry<FileChannel> openChannel(Path p, Instant now) throws IOException {
        for (Iterator<Map.Entry<Path, Entry<FileChannel>>> iterator = channels.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<Path, Entry<FileChannel>> e = iterator.next();
            if (e.getValue().getAccessTime().isBefore(now.minus(getTimeout()))) {
                e.getValue().getTarget().close();
                iterator.remove();
            }
        }
        int mustBeRemoved = channels.size() - maxOpenChannels + 1;
        if (mustBeRemoved > 0) {
            channels.entrySet().stream()
                    .sorted(Comparator.comparing(a -> a.getValue().getAccessTime()))
                    .limit(mustBeRemoved)
                    .map(Map.Entry::getKey)
                    .forEach(ExceptionUtil.softenExceptions(k -> channels.remove(k).getTarget().close()));
        }
        createDirectory(p.getParent());
        return new Entry<>(FileChannel.open(p, StandardOpenOption.APPEND, StandardOpenOption.CREATE));
    }

    private void createDirectory(Path directory) {
        if (directory != null) {
            try {
                Files.createDirectories(directory);
            } catch (IOException e) {
                LogEventStatus.getInstance().addFatal(this, "Can't create directory " + directory, e);
            }
        }
    }

    public Duration getTimeout() {
        return timeout;
    }
}
