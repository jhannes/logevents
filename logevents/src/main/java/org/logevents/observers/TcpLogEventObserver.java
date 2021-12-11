package org.logevents.observers;

import org.logevents.config.Configuration;
import org.logevents.formatters.JsonLogEventFormatter;
import org.logevents.LogEventFormatter;
import org.logevents.observers.batch.LogEventBatch;
import org.logevents.status.LogEventStatus;
import org.slf4j.event.Level;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.Map;

/**
 * Writes LogEvents to a TCP address. To avoid exceptions and network slowness interrupting main
 * process flow, this observer batches the message sending with the same mechanism as
 * {@link AbstractBatchingLogEventObserver}. This class has been optimized for writing to
 * an ELK installation
 *
 * <h2>Sample configuration (used with Logstash)</h2>
 *
 * <pre>
 * observer.tcp=TcpLogEventObserver
 * observer.tcp.address=example.com:8888
 * observer.tcp.formatter=JsonLogEventFormatter
 * observer.tcp.formatter.excludedMdcKeys=test
 * </pre>
 */
public class TcpLogEventObserver extends AbstractBatchingLogEventObserver {

    private final LogEventFormatter formatter;
    private final InetSocketAddress address;
    private int timeout = 0;

    public TcpLogEventObserver(String address, LogEventFormatter formatter) {
        int colonPos = address.indexOf(':');
        String host = address.substring(0, colonPos);
        int port = Integer.parseInt(address.substring(colonPos + 1));
        this.address = new InetSocketAddress(host, port);
        this.formatter = formatter;
    }

    public TcpLogEventObserver(Configuration configuration) {
        this(
                configuration.getString("address"),
                configuration.createFormatter("formatter", JsonLogEventFormatter.class)
        );
        this.configureBatching(configuration);
        this.configureFilter(configuration, Level.TRACE);
        this.timeout = configuration.optionalDuration("timeout").map(Duration::toMillis).orElse(0L).intValue();
        configuration.checkForUnknownFields();
    }

    public TcpLogEventObserver(Map<String, String> properties, String prefix){
        this(new Configuration(properties, prefix));
    }

    @Override
    protected void processBatch(LogEventBatch batch) {
        if (batch.isEmpty()) {
            return;
        }
        try ( Socket clientSocket = new Socket()) {
            clientSocket.connect(address, timeout);
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            batch.forEach(event -> out.println(formatter.apply(event)));
            out.flush();
            LogEventStatus.getInstance().addTrace(this, "Sent message to " + address);
        } catch (IOException e) {
            LogEventStatus.getInstance().addError(this, "While sending to " + address, e);
        }
    }

    @Override
    public String toString() {
        return "TcpLogEventObserver{address=" + address + ",formatter=" + formatter + '}';
    }
}
