package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.status.LogEventStatus;
import org.logevents.util.Configuration;
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
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// TODO: Extend BatchingLogEventObserver
public class DatabaseLogEventObserver extends FilteredLogEventObserver {

    private final String jdbcUrl;
    private final String jdbcUsername;
    private final String jdbcPassword;
    private String argumentSeparator;

    public DatabaseLogEventObserver(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public DatabaseLogEventObserver(Configuration configuration) {
        this.argumentSeparator = configuration.optionalString("argumentSeparator").orElse("§");

        this.jdbcUrl = configuration.getString("jdbcUrl");
        this.jdbcUsername = configuration.getString("jdbcUsername");
        this.jdbcPassword = configuration.optionalString("jdbcPassword").orElse("");

        try (Connection connection = getConnection()) {
            try (ResultSet tables = connection.getMetaData().getTables(null, null, "LOG_EVENT", null)) {
                if (!tables.next()) {
                    try (Statement statement = connection.createStatement()) {
                        statement.executeUpdate("create table log_event (" +
                                "logger varchar(100) not null, " +
                                "level varchar(10) not null, " +
                                "level_int integer not null, " +
                                "message text not null, " +
                                "instant bigint not null, " +
                                "thread_name varchar(100) not null," +
                                "arguments varchar(1000) not null," +
                                "marker varchar(100)" +
                                ")"
                        );
                    }
                }
            }
        } catch (SQLException e) {
            LogEventStatus.getInstance().addFatal(this, "Failed to initialize database", e);
        }
    }

    @Override
    protected void doLogEvent(LogEvent logEvent) {
        try (Connection connection = getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("insert into log_event (logger, level, level_int, message, instant, thread_name, arguments, marker) values (?, ?, ?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, logEvent.getLoggerName());
                statement.setString(2, logEvent.getLevel().toString());
                statement.setInt(3, logEvent.getLevel().toInt());
                statement.setString(4, logEvent.getMessage());
                statement.setLong(5, logEvent.getTimeStamp());
                statement.setString(6, logEvent.getThreadName());
                statement.setString(7,
                        Stream.of(logEvent.getArgumentArray()).map(Object::toString).collect(Collectors.joining(argumentSeparator)));
                statement.setString(8, toString(logEvent.getMarker()));
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            LogEventStatus.getInstance().addError(this, "Failed to write log record", e);
        }
    }

    private String toString(Object o) {
        return Optional.ofNullable(o).map(Object::toString).orElse(null);
    }

    public List<LogEvent> filter(Level threshold, Instant start, Instant end) {
        List<LogEvent> result = new ArrayList<>();
        try (Connection connection = getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("select * from log_event where instant between ? and ? and level_int >= ?")) {
                statement.setLong(1, start.toEpochMilli());
                statement.setLong(2, end.toEpochMilli());
                statement.setInt(3, threshold.toInt());
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        result.add(new LogEvent(
                                rs.getString("logger"),
                                Level.valueOf(rs.getString("level")),
                                rs.getString("thread_name"),
                                Instant.ofEpochMilli(rs.getLong("instant")),
                                getMarker(rs.getString("marker")),
                                rs.getString("message"),
                                rs.getString("arguments").split(argumentSeparator),
                                null,
                                null
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            LogEventStatus.getInstance().addError(this, "Failed to write log record", e);
        }
        return result;
    }

    private Marker getMarker(String marker) {
        return marker != null ? MarkerFactory.getMarker(marker) : null;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, jdbcUsername, jdbcPassword);
    }
}
