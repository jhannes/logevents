package org.logevents.benchmark.analyze;

import ch.qos.logback.core.recovery.ResilientFileOutputStream;
import org.logevents.observers.file.FileDestination;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@State(Scope.Benchmark)
public class FileoutputBenchmarks {

    private final String text = "Lorem ipsum dolor sit amet, pri augue euripidis vituperata ea, veri minim conceptam qui ad. Ei brute civibus eos, scribentur reformidans instructior at eam, quas postea explicari vis id. Qui ea veritus honestatis, epicuri dolores ne eam. Ea atqui corrumpit nec.\n" +
            "\n" +
            "Movet vocent at est. Nam te dicam delicata dissentiet. Duo voluptatum consectetuer te, eam facilis maiorum fabellas ut. Tollit soleat quaerendum duo ne, meliore intellegebat qui an, erat insolens argumentum ex quo. Wisi malis pri eu. Vel et praesent petentium.\n" +
            "\n" +
            "At mei eligendi dignissim necessitatibus. Solet ullamcorper eu qui, veri similique nec in. Idque saepe deterruisset usu te. Vix tota oblique sententiae an, id ubique explicari eam, animal intellegebat ut usu. Pri an falli liberavisse, ad vis eirmod iuvaret.\n" +
            "\n" +
            "Elitr facilisi similique qui an. Wisi bonorum usu cu, commune senserit quo no, augue ubique tibique ei usu. Labore noluisse ex eam. Vidisse inciderint ex mea, eu has inimicus partiendo inciderint. No pro elitr numquam moderatius.\n" +
            "\n" +
            "Ut cetero laoreet his. Eam ex tation incorrupte, cu sea utinam impedit probatus. In mutat vivendum oportere pro. Ea sint movet sed, ea pri sale petentium, congue aliquid eum in. Graece graecis nam te, nisl purto oportere cum ad.";

    private OutputStream outputStream;

    private OutputStream bufferedOutputStream;

    private FileChannel channelLocked;
    private FileChannel channel;

    private ResilientFileOutputStream logbackOutputStream;

    private FileDestination logeventsFiles = new FileDestination(false);
    private Path logeventsPath;


    @Setup
    public void openFiles() throws IOException {
        Path dir = Paths.get("target", "benchmark", "org.logevents.benchmark.analyze", "files-" + System.currentTimeMillis());
        Files.createDirectories(dir);

        outputStream = new FileOutputStream(dir.resolve("outputStream.txt").toFile());
        bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(dir.resolve("outputStream.txt").toFile()), 8192);
        logbackOutputStream = new ResilientFileOutputStream(dir.resolve("logback.txt").toFile(), true, 8192);
        channelLocked = FileChannel.open(dir.resolve("channelLocked.txt"), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
        channel = FileChannel.open(dir.resolve("channel.txt"), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
        logeventsPath = dir.resolve("logevents.txt");
    }

    @Benchmark
    public void writeToOutputStream() throws IOException {
        outputStream.write(text.getBytes());
    }

    @Benchmark
    public void writeToOutputBuffered() throws IOException {
        bufferedOutputStream.write(text.getBytes());
        bufferedOutputStream.flush();
    }

    @Benchmark
    public void writeToChannel() throws IOException {
        ByteBuffer src = ByteBuffer.wrap(text.getBytes());
        try(FileLock ignored = channelLocked.tryLock()) {
            channelLocked.write(src);
        }
    }

    @Benchmark
    public void writeToChannelWithoutLock() throws IOException {
        ByteBuffer src = ByteBuffer.wrap(text.getBytes());
        channel.write(src);
    }

    @Benchmark
    public void writeToLogback() throws IOException {
        logbackOutputStream.write(text.getBytes());
        logbackOutputStream.flush();
    }

    @Benchmark
    public void writeToLogevents() {
        logeventsFiles.writeEvent(logeventsPath, text);
    }


}
