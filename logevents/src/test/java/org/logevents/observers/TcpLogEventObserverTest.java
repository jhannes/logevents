package org.logevents.observers;

import org.junit.Rule;
import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.extend.junit.LogEventSampler;
import org.logevents.extend.junit.LogEventStatusRule;
import org.logevents.formatting.LogEventFormatter;
import org.logevents.formatting.MessageFormatter;
import org.logevents.observers.batch.LogEventBatch;
import org.logevents.observers.file.FileRotationWorker;
import org.logevents.status.LogEventStatus;
import org.logevents.status.StatusEvent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TcpLogEventObserverTest {

    @Rule
    public LogEventStatusRule statusRule = new LogEventStatusRule();

    @Test
    public void shouldShowConfiguration() {
        Map<String, String> properties = new HashMap<>();
        properties.put("observer.tcp.address", "logserver.example.com:8080");
        properties.put("observer.tcp.timeout", Duration.ofMillis(100).toString());
        TcpLogEventObserver observer = new TcpLogEventObserver(properties, "observer.tcp");

        assertEquals("TcpLogEventObserver{address=logserver.example.com:8080,formatter=JsonLogEventFormatter}", observer.toString());
    }

    @Test
    public void shouldLogMessageOverTcp() throws IOException, InterruptedException {
        ServerSocket serverSocket = new ServerSocket(0);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Thread thread = new Thread(() -> {
            try {
                Socket clientSocket = serverSocket.accept();
                FileRotationWorker.transfer(clientSocket.getInputStream(), buffer);
            } catch (IOException e) {
                e.printStackTrace();
            }

        });
        thread.start();

        LogEventFormatter formatter = event -> event.getMessage(new MessageFormatter());
        TcpLogEventObserver observer = new TcpLogEventObserver("localhost:" + serverSocket.getLocalPort(), formatter);
        LogEvent event = new LogEventSampler().build();
        observer.processBatch(new LogEventBatch().add(event));
        thread.join(100);
        assertEquals(event.getMessage(new MessageFormatter()) + System.lineSeparator(), buffer.toString());

        assertEquals(
                "Sent message to localhost/127.0.0.1:" + serverSocket.getLocalPort(),
                LogEventStatus.getInstance().lastMessage().getMessage()
        );
    }

    @Test
    public void shouldReportError() throws IOException {
        ServerSocket serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        serverSocket.close();

        Map<String, String> properties = new HashMap<>();
        properties.put("observer.tcp.address", "localhost:" + port);
        properties.put("observer.tcp.timeout", Duration.ofMillis(100).toString());
        TcpLogEventObserver observer = new TcpLogEventObserver(properties, "observer.tcp");

        statusRule.setStatusLevel(StatusEvent.StatusLevel.FATAL);
        LogEvent event = new LogEventSampler().build();
        observer.processBatch(new LogEventBatch().add(event));

        assertEquals("While sending to localhost/127.0.0.1:" + port, LogEventStatus.getInstance().lastMessage().getMessage());
        assertTrue(LogEventStatus.getInstance().lastMessage().getThrowable() instanceof IOException);
    }
}
