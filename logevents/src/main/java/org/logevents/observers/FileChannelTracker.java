package org.logevents.observers;

import org.logevents.status.LogEventStatus;
import org.logevents.util.ExceptionUtil;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class FileChannelTracker {

    private int maxOpenChannels = 100;

    public void setMaxOpenChannels(int maxOpenChannels) {
        this.maxOpenChannels = maxOpenChannels;
    }

    static class Entry {
        private final FileChannel channel;
        private Instant accessTime;

        private Entry(FileChannel channel) {
            this.channel = channel;
        }

        public FileChannel getChannel() {
            return channel;
        }

        public Instant getAccessTime() {
            return accessTime;
        }

        public void setAccessTime(Instant accessTime) {
            this.accessTime = accessTime;
        }
    }


    private Map<Path, Entry> channels = new LinkedHashMap<>();

    private Duration timeout = Duration.ofMinutes(10);

    /**
     * Returns a channel for the specified path, considering it as opened with the now parameter. If the
     * requested channel is already opened, this is returned, otherwise a new channel is returned. If a
     * new channel is opened, the {@link FileChannelTracker} reviews existing opened channels and make sure
     * that no channel has been idle for longer than {@link #timeout} and that now more than {@link #maxOpenChannels}
     * are opened. If channels need to be closed, the least recently accessed channels are closed first.
     */
    FileChannel getChannel(Path path, Instant now) throws IOException {
        Entry result = channels.computeIfAbsent(path, ExceptionUtil.softenFunctionExceptions(p -> openChannel(p, now)));
        result.setAccessTime(now);
        return result.getChannel();
    }

    Entry openChannel(Path p, Instant now) throws IOException {
        for (Iterator<Map.Entry<Path, Entry>> iterator = channels.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<Path, Entry> e = iterator.next();
            if (e.getValue().getAccessTime().isBefore(now.minus(getTimeout()))) {
                e.getValue().getChannel().close();
                iterator.remove();
            }
        }
        int mustBeRemoved = channels.size() - maxOpenChannels + 1;
        if (mustBeRemoved > 0) {
            channels.entrySet().stream()
                    .sorted(Comparator.comparing(a -> a.getValue().getAccessTime()))
                    .limit(mustBeRemoved)
                    .map(Map.Entry::getKey)
                    .forEach(ExceptionUtil.softenExceptions(k -> channels.remove(k).getChannel().close()));
        }
        createDirectory(p.getParent());
        return new Entry(FileChannel.open(p, StandardOpenOption.APPEND, StandardOpenOption.CREATE));
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
