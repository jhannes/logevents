package org.logevents.observers;

import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;

import static org.junit.Assert.*;

public class FileChannelTrackerTest {

    private FileChannelTracker tracker = new FileChannelTracker();

    private Path directory = Paths.get("target", "test", "logs", "directory");

    @Test
    public void shouldWriteToChannel() throws IOException {
        Path file = directory.resolve("some-path.txt");
        Files.deleteIfExists(file);

        FileChannel chan = tracker.getChannel(file, Instant.now());
        ByteBuffer src = ByteBuffer.wrap("hello world".getBytes());
        chan.write(src);
        assertEquals(Arrays.asList("hello world"), Files.readAllLines(file));
    }

    @Test
    public void shouldKeepMultipleChannelsOpen() throws IOException {
        FileChannel chan1 = tracker.getChannel(directory.resolve("file-one.txt"), Instant.now());
        FileChannel chan2 = tracker.getChannel(directory.resolve("file-two.txt"), Instant.now());

        assertTrue(chan1.isOpen());
        assertTrue(chan2.isOpen());
    }

    @Test
    public void shouldCloseOldChannels() throws IOException {
        FileChannel oldChannel = tracker.getChannel(
                directory.resolve("old.txt"),
                Instant.now().minus(tracker.getTimeout()).minusMillis(100)
        );
        assertTrue(oldChannel.isOpen());
        tracker.getChannel(directory.resolve("new.txt"), Instant.now());

        assertFalse(oldChannel.isOpen());
    }

    @Test
    public void shouldLimitOpenedChannels() throws IOException {
        tracker.setMaxOpenChannels(2);
        Instant now = Instant.now();
        Path frequentlyUsed = directory.resolve("log.txt");
        FileChannel frequentlyUsedFile = tracker.getChannel(frequentlyUsed, now.minusMillis(1000));
        FileChannel rarelyUsedFile = tracker.getChannel(directory.resolve("log-for-user.txt"), now.minusMillis(500));
        tracker.getChannel(frequentlyUsed, now.minusMillis(400));
        FileChannel lastUsed = tracker.getChannel(directory.resolve("log-for-other-user.txt"), now);

        assertTrue(frequentlyUsedFile.isOpen());
        assertTrue(lastUsed.isOpen());
        assertFalse(rarelyUsedFile.isOpen());
    }

}