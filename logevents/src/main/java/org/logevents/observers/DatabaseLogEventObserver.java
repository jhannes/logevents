package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.observers.batch.LogEventBatch;
import org.logevents.observers.batch.LogEventBatchProcessor;
import org.logevents.query.LogEventFilter;
import org.logevents.query.LogEventQueryResult;
import org.logevents.query.LogEventSummary;
import org.logevents.status.LogEventStatus;
import org.logevents.util.Configuration;
import org.logevents.util.JsonParser;
import org.logevents.util.JsonUtil;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Writes log events asynchronously to relational database with JDBC. Will create the necessary tables
 * at startup if they don't exist. {@link DatabaseLogEventObserver} can use an existing database and
 * doesn't interfere with database migrations with Flyway or similar tools. Log events are written with
 * UUID primary keys and the same database can be used for several applications if you want.
 * <strong>This class should work with all relational database providers but has only been verified with
 * Postgres and H2.</strong>
 *
 * <p>{@link DatabaseLogEventObserver} is a nice compromise if you want to take a step towards centralized logging,
 * but don't want to set up Elastic search, Splunk or a similar solution quite yet.</p>
 *
 * <p>{@link DatabaseLogEventObserver} is designed to be used with {@link org.logevents.extend.servlets.LogEventsServlet}</p>
 *
 * <h3>Sample configuration</h3>
 * <pre>
 * observer.db=DatabaseLogEventObserver
 * observer.db.jdbcUrl=jdbc:postgres://localhost/logdb
 * observer.db.jdbcUsername=logevents
 * observer.db.jdbcPassword=sdgawWWF/)l31L
 * observer.db.logeventTable=log_events
 * observer.db.logeventMdcTable=log_events_mdc
 * </pre>
 */
// TODO: Save exception
// TODO: Configurable table names
public class DatabaseLogEventObserver extends BatchingLogEventObserver implements LogEventBatchProcessor, LogEventSource {

    private final String jdbcUrl;
    private final String jdbcUsername;
    private final String jdbcPassword;
    private final String nodeName;

    public DatabaseLogEventObserver(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public DatabaseLogEventObserver(Configuration configuration) {
        super(null);
        this.jdbcUrl = configuration.getString("jdbcUrl");
        this.jdbcUsername = configuration.getString("jdbcUsername");
        this.jdbcPassword = configuration.optionalString("jdbcPassword").orElse("");
        this.nodeName = configuration.getNodeName();

        try (Connection connection = getConnection()) {
            try (ResultSet tables = connection.getMetaData().getTables(null, null, "LOG_EVENTS", null)) {
                if (!tables.next()) {
                    try (Statement statement = connection.createStatement()) {
                        statement.executeUpdate("create table log_events (" +
                                "event_id varchar(100) primary key, " +
                                "logger varchar(100) not null, " +
                                "level varchar(10) not null, " +
                                "level_int integer not null, " +
                                "message text not null, " +
                                "instant bigint not null, " +
                                "thread_name varchar(100) not null," +
                                "arguments varchar(1000) not null," +
                                "node_name varchar(100) not null," +
                                "marker varchar(100)" +
                                ")"
                        );
                        statement.executeUpdate("create index log_events_logger_idx on log_events(logger)");
                        statement.executeUpdate("create index log_events_level_idx on log_events(level)");
                        statement.executeUpdate("create index log_events_instant_idx on log_events(instant)");
                        statement.executeUpdate("create index log_events_thread_name_idx on log_events(thread_name)");
                        statement.executeUpdate("create index log_events_marker_idx on log_events(marker)");
                        statement.executeUpdate("create index log_events_node_name_idx on log_events(node_name)");
                    }
                }
            }
            try (ResultSet tables = connection.getMetaData().getTables(null, null, "LOG_EVENTS_MDC", null)) {
                if (!tables.next()) {
                    try (Statement statement = connection.createStatement()) {
                        statement.executeUpdate("create table log_events_mdc (" +
                                "event_id varchar(100) not null references log_events(event_id), " +
                                "key varchar(100) not null, " +
                                "value varchar(20) not null" +
                                ")"
                        );
                        statement.executeUpdate("create index log_events_mdc_idx on log_events_mdc(key, value)");
                    }
                }
            }
        } catch (SQLException e) {
            LogEventStatus.getInstance().addFatal(this, "Failed to initialize database", e);
        }
    }

    private String toString(Object o) {
        return Optional.ofNullable(o).map(Object::toString).orElse(null);
    }

    /**
     * Retrieve log events from the database that matches the argument filter
     */
    public Collection<LogEvent> filter(LogEventFilter filter) {
        Collection<LogEvent> result = new ArrayList<>();
        Map<String, LogEvent> idToResult = new HashMap<>();
        try (Connection connection = getConnection()) {
            try (PreparedStatement statement = prepareQuery(connection, filter)) {
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        String id = rs.getString("event_id");
                        LogEvent e = idToResult.get(id);
                        if (e == null) {
                            e = new LogEvent(
                                    rs.getString("logger"),
                                    Level.valueOf(rs.getString("level")),
                                    rs.getString("thread_name"),
                                    Instant.ofEpochMilli(rs.getLong("instant")),
                                    getMarker(rs.getString("marker")),
                                    rs.getString("message"),
                                    JsonParser.parseArray(rs.getString("arguments")).toArray(),
                                    null,
                                    new HashMap<>()
                            );
                            idToResult.put(id, e);
                            result.add(e);
                        }
                        if (rs.getString("key") != null) {
                            e.getMdcProperties().put(rs.getString("key"), rs.getString("value"));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LogEventStatus.getInstance().addError(this, "Failed to write log record", e);
        }
        return result;
    }

    public PreparedStatement prepareQuery(Connection connection, LogEventFilter filter) throws SQLException {
        String sql = "select * from log_events e left outer join log_events_mdc m on e.event_id = m.event_id where instant between ? and ? and level_int >= ? order by instant";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setLong(1, filter.getStartTime().toEpochMilli());
        statement.setLong(2, filter.getEndTime().toEpochMilli());
        statement.setInt(3, filter.getThreshold().toInt());
        return statement;
    }

    private Marker getMarker(String marker) {
        return marker != null ? MarkerFactory.getMarker(marker) : null;
    }

    /**
     * Open a new connection to the database
     */
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, jdbcUsername, jdbcPassword);
    }

    @Override
    public void processBatch(LogEventBatch batch) {
        saveLogEvents(batch);
    }

    private void saveLogEvents(LogEventBatch batch) {
        try (Connection connection = getConnection()) {
            try (
                    PreparedStatement eventStmt = connection.prepareStatement("insert into log_events (event_id, logger, level, level_int, message, instant, thread_name, arguments, marker, node_name) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                    PreparedStatement mdcStmt = connection.prepareStatement("insert into log_events_mdc (event_id, key, value) values (?, ?, ?)")
                    ) {
                for (LogEvent logEvent : batch) {
                    String id = UUID.randomUUID().toString();
                    eventStmt.setString(1, id);
                    eventStmt.setString(2, logEvent.getLoggerName());
                    eventStmt.setString(3, logEvent.getLevel().toString());
                    eventStmt.setInt(4, logEvent.getLevel().toInt());
                    eventStmt.setString(5, logEvent.getMessage());
                    eventStmt.setLong(6, logEvent.getTimeStamp());
                    eventStmt.setString(7, logEvent.getThreadName());
                    eventStmt.setString(8, JsonUtil.toIndentedJson(
                            Stream.of(logEvent.getArgumentArray()).map(Object::toString).collect(Collectors.toList())
                    ));
                    eventStmt.setString(9, toString(logEvent.getMarker()));
                    eventStmt.setString(10, nodeName);
                    eventStmt.addBatch();

                    for (Map.Entry<String, String> entry : logEvent.getMdcProperties().entrySet()) {
                        mdcStmt.setString(1, id);
                        mdcStmt.setString(2, entry.getKey());
                        mdcStmt.setString(3,entry.getValue());
                        mdcStmt.addBatch();
                    }

                }
                eventStmt.executeBatch();
                mdcStmt.executeBatch();
            }
        } catch (SQLException e) {
            LogEventStatus.getInstance().addError(this, "Failed to write log record", e);
        }
    }

    @Override
    public LogEventQueryResult query(LogEventFilter filter) {
        try {
            return new LogEventQueryResult(filter(filter), getSummary(filter));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieves a summary of log events within the specified interval above the threshold log level
     */
    public LogEventSummary getSummary(LogEventFilter filter) throws SQLException {
        LogEventSummary summary = new LogEventSummary();

        try (Connection connection = getConnection()) {
            summary.setMarkers(listDistinct(connection, filter, "marker"));
            summary.setThreads(listDistinct(connection, filter, "thread_name"));
            summary.setLoggers(listDistinct(connection, filter, "logger"));
            summary.setNodes(listDistinct(connection, filter, "node_name"));
            summary.setMdcMap(getMdcMap(connection, filter));
        }
        return summary;
    }

    private Set<String> listDistinct(Connection connection, LogEventFilter filter, String columnName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select distinct " + columnName + " from log_events where instant between ? and ? and level_int >= ?")) {
            statement.setLong(1, filter.getStartTime().toEpochMilli());
            statement.setLong(2, filter.getEndTime().toEpochMilli());
            statement.setLong(3, filter.getThreshold().toInt());
            try (ResultSet rs = statement.executeQuery()) {
                Set<String> result = new TreeSet<>();
                while (rs.next()) {
                    String value = rs.getString(1);
                    if (!rs.wasNull()) {
                        result.add(value);
                    }
                }
                return result;
            }
        }
    }

    private Map<String, Set<String>> getMdcMap(Connection connection, LogEventFilter filter) throws SQLException {
        Map<String, Set<String>> mdcMap = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("select distinct key, value from log_events_mdc m inner join log_events e on m.event_id = e.event_id where instant between ? and ? and level_int >= ?")) {
            statement.setLong(1, filter.getStartTime().toEpochMilli());
            statement.setLong(2, filter.getEndTime().toEpochMilli());
            statement.setLong(3, filter.getThreshold().toInt());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("key");
                    mdcMap.computeIfAbsent(key, k -> new HashSet<>()).add(rs.getString("value"));
                }
            }
        }
        return mdcMap;
    }
}
