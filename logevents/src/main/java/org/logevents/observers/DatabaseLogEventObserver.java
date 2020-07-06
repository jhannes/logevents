package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.config.Configuration;
import org.logevents.extend.servlets.JsonExceptionFormatter;
import org.logevents.extend.servlets.JsonMessageFormatter;
import org.logevents.formatting.MessageFormatter;
import org.logevents.observers.batch.JsonLogEventsBatchFormatter;
import org.logevents.observers.batch.LogEventBatch;
import org.logevents.query.LogEventFilter;
import org.logevents.query.LogEventQueryResult;
import org.logevents.query.LogEventSummary;
import org.logevents.status.LogEventStatus;
import org.logevents.util.ExceptionUtil;
import org.logevents.util.JsonParser;
import org.logevents.util.JsonUtil;
import org.slf4j.Marker;
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
import java.util.LinkedHashMap;
import java.util.List;
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
 * <p>{@link DatabaseLogEventObserver} is designed to be used with {@link org.logevents.extend.servlets.LogEventsServlet}
 * through {@link WebLogEventObserver}</p>
 *
 * <h3>Sample configuration</h3>
 * <pre>
 * observer.db=DatabaseLogEventObserver
 * observer.db.jdbcUrl=jdbc:postgres://localhost/logdb
 * observer.db.jdbcUsername=logevents
 * observer.db.jdbcPassword=sdgawWWF/)l31L
 * observer.db.logeventsTable=log_events
 * observer.db.logeventsMdcTable=log_events_mdc
 * observer.db.loginTimeout=20
 * observer.db.noFetchFirstSupport=false
 * </pre>
 */
public class DatabaseLogEventObserver extends BatchingLogEventObserver implements LogEventSource {

    private final String jdbcUrl;
    private final String jdbcUsername;
    private final String jdbcPassword;
    private final String nodeName;
    private final String applicationName;

    /** The table name in the database to use for logevents. Will be created at startup if it doesn't exist */
    private final String logEventsTable;

    /** The table name in the database to use for MDC. Will be created at startup if it doesn't exist */
    private final String logEventsMdcTable;

    /**
     * Unless set to <em>true</em>, {@link LogEventFilter#getLimit()} will be added as
     * <code>SELECT ... FETCH FIRST ... ROWS</code>. FETCH FIRST was introduced in
     * <a href="https://en.wikipedia.org/wiki/Select_%28SQL%29#Limiting_result_rows">SQL:2008</a>
     * and is supported by Postgresql 8.4, Oracle 12c, IBM DB2, HSQLDB, H2, and SQL Server 2012.
     * If you use an older database or a vendor that doesn't support it, set
     * <code>observer...noFetchFirstSupport=true</code>
     */
    private final boolean noFetchFirstSupport;

    /** Calls DriverManager::setLoginTimeout with this value (if present) at startup <strong>NB: This is a global value for the JVM</strong> */
    private final Optional<Integer> loginTimeout;

    public DatabaseLogEventObserver(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public DatabaseLogEventObserver(Configuration configuration) {
        this.jdbcUrl = configuration.getString("jdbcUrl");
        this.jdbcUsername = configuration.getString("jdbcUsername");
        this.jdbcPassword = configuration.optionalString("jdbcPassword").orElse("");
        this.logEventsTable = configuration.optionalString("logEventsTable").orElse("LOG_EVENTS").toUpperCase();
        this.logEventsMdcTable = configuration.optionalString("logEventsMdcTable").orElse(logEventsTable + "_MDC").toUpperCase();
        this.noFetchFirstSupport = configuration.getBoolean("noFetchFirstSupport");
        this.nodeName = configuration.getNodeName();
        this.applicationName = configuration.getApplicationName();
        this.messageFormatter = configuration.createInstanceWithDefault("messageFormatter", MessageFormatter.class);
        this.jsonMessageFormatter = configuration.createInstanceWithDefault("jsonMessageFormatter", JsonMessageFormatter.class);
        this.exceptionFormatter = configuration.createInstanceWithDefault("exceptionFormatter", JsonExceptionFormatter.class);
        this.loginTimeout = configuration.optionalInt("loginTimeout");
        configuration.checkForUnknownFields();

        LogEventStatus.getInstance().addDebug(this, "Connecting to " + jdbcUrl + " as " + jdbcUsername);

        loginTimeout.ifPresent(DriverManager::setLoginTimeout);
        try (Connection connection = getConnection()) {
            LogEventStatus.getInstance().addDebug(this, "Setting up to " + jdbcUrl + " as " + jdbcUsername);
            if (!tableExists(connection, logEventsTable)) {
                LogEventStatus.getInstance().addConfig(this, "Creating table " + logEventsTable);
                createLogEventsTable(connection);
            } else {
                LogEventStatus.getInstance().addDebug(this, "Table " + logEventsMdcTable + " already exists");
            }
            if (!tableExists(connection, logEventsMdcTable)) {
                LogEventStatus.getInstance().addConfig(this, "Creating table " + logEventsMdcTable);
                createMdcTable(connection);
            } else {
                LogEventStatus.getInstance().addDebug(this, "Table " + logEventsMdcTable + " already exists");
            }
        } catch (SQLException e) {
            LogEventStatus.getInstance().addFatal(this, "Failed to initialize database " + jdbcUrl, e);
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(null, null, null, new String[]{"TABLE"})) {
            while (tables.next()) {
                String rowTableName = tables.getString("TABLE_NAME");
                if (rowTableName.equalsIgnoreCase(tableName)) return true;
            }
            return false;
        }
    }

    public void createMdcTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("create table " + logEventsMdcTable + " (" +
                    "event_id varchar(100) not null references " + logEventsTable + "(event_id), " +
                    "name varchar(100) not null, " +
                    "value varchar(1000)" +
                    ")"
            );
            statement.executeUpdate("create index " + logEventsMdcTable + "_idx on " + logEventsMdcTable + "(name, value)");
        }
    }

    public void createLogEventsTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("create table " + logEventsTable + "(" +
                    "event_id varchar(100) primary key, " +
                    "logger varchar(100) not null, " +
                    "level varchar(10) not null, " +
                    "level_int integer not null, " +
                    "message text not null, " +
                    "formatted_message text not null, " +
                    "message_json text not null, " +
                    "instant bigint not null, " +
                    "thread varchar(100) not null," +
                    "arguments text not null," +
                    "marker varchar(100)," +
                    "throwable text," +
                    "stack_trace text," +
                    "node_name varchar(100) not null," +
                    "application_name varchar(100) not null" +
                    ")"
            );
            statement.executeUpdate("create index " + logEventsTable + "_logger_idx on " + logEventsTable + "(logger)");
            statement.executeUpdate("create index " + logEventsTable + "_level_idx on " + logEventsTable + "(level)");
            statement.executeUpdate("create index " + logEventsTable + "_instant_idx on " + logEventsTable + "(instant)");
            statement.executeUpdate("create index " + logEventsTable + "_thread_idx on " + logEventsTable + "(thread)");
            statement.executeUpdate("create index " + logEventsTable + "_marker_idx on " + logEventsTable + "(marker)");
            statement.executeUpdate("create index " + logEventsTable + "_node_name_idx on " + logEventsTable + "(node_name)");
        }
    }

    private String toString(Object o) {
        return Optional.ofNullable(o).map(Object::toString).orElse(null);
    }

    /**
     * Retrieve log events from the database that matches the argument filter
     */
    private Collection<Map<String, Object>> list(LogEventFilter filter) {
        Map<String, Map<String, Object>> idToJson = new LinkedHashMap<>();
        Map<String, List<Map<String, String>>> idToJsonMdc = new LinkedHashMap<>();

        try (Connection connection = getConnection()) {
            List<String> filters = new ArrayList<>();
            List<Object> parameters = new ArrayList<>();
            buildFilter(filter, filters, parameters);

            String sql = "select * from " + logEventsTable + " e left outer join " + logEventsMdcTable + "  m on e.event_id = m.event_id " +
                    " where " + String.join(" AND ", filters) + " order by instant";
            if (!noFetchFirstSupport) {
                sql += " FETCH FIRST " + filter.getLimit() + " ROWS ONLY";
            }

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int parameterIndex = 1;
                for (Object parameter : parameters) {
                    statement.setObject(parameterIndex++, parameter);
                }
                long startTime = System.currentTimeMillis();
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next() && idToJson.size() < filter.getLimit()) {
                        String id = rs.getString("event_id");
                        if (!idToJson.containsKey(id)) {
                            Map<String, Object> jsonEvent = new HashMap<>();
                            jsonEvent.put("thread", rs.getString("thread"));
                            jsonEvent.put("time", Instant.ofEpochMilli(rs.getLong("instant")).toString());
                            jsonEvent.put("logger", rs.getString("logger"));
                            jsonEvent.put("level", rs.getString("level"));
                            jsonEvent.put("levelIcon", null);
                            jsonEvent.put("formattedMessage", rs.getString("formatted_message"));
                            jsonEvent.put("messageTemplate", rs.getString("message"));
                            jsonEvent.put("message", JsonParser.parseArray(rs.getString("message_json")));
                            jsonEvent.put("marker", rs.getString("marker"));
                            jsonEvent.put("arguments", JsonParser.parseArray(rs.getString("arguments")));
                            jsonEvent.put("throwable", rs.getString("throwable"));
                            jsonEvent.put("stackTrace", JsonParser.parseArray(rs.getString("stack_trace")));

                            jsonEvent.put("abbreviatedLogger", LogEvent.getAbbreviatedClassName(jsonEvent.get("logger").toString(), 0));
                            jsonEvent.put("levelIcon", JsonLogEventsBatchFormatter.emojiiForLevel(Level.valueOf(jsonEvent.get("level").toString())));
                            jsonEvent.put("node", rs.getString("node_name"));
                            jsonEvent.put("application", rs.getString("application_name"));

                            idToJson.put(id, jsonEvent);

                            List<Map<String, String>> jsonMdc = new ArrayList<>();
                            jsonEvent.put("mdc", jsonMdc);
                            idToJsonMdc.put(id, jsonMdc);
                        }
                        if (rs.getString("name") != null) {
                            Map<String, String> jsonMdc = new HashMap<>();
                            jsonMdc.put("name", rs.getString("name"));
                            jsonMdc.put("value", rs.getString("value"));
                            idToJsonMdc.get(id).add(jsonMdc);
                        }
                    }
                }
                long executionTime = System.currentTimeMillis() - startTime;
                LogEventStatus.getInstance().addTrace(this, "Retrieved " + idToJson.size() + " events in " + (executionTime/1000.0) + "s");
            }
        } catch (SQLException e) {
            LogEventStatus.getInstance().addError(this, "Failed to write log record", e);
        }
        return idToJson.values();
    }

    private void buildFilter(LogEventFilter filter, List<String> filters, List<Object> parameters) {
        filters.add("instant between ? and ?");
        parameters.add(filter.getStartTime().toEpochMilli());
        parameters.add(filter.getEndTime().toEpochMilli());

        filters.add("level_int >= ?");
        parameters.add(filter.getThreshold().toInt());
        filter.getMarkers().ifPresent(markers -> {
            filters.add(filter.isIncludeMarkers() ? "marker in (" + questionMarks(markers.size()) + ")" : "(marker is null or marker not in (" + questionMarks(markers.size()) + "))");
            markers.stream().map(Marker::getName).forEach(parameters::add);
        });
        filter.getLoggers().ifPresent(loggers -> {
            filters.add(filter.isIncludeLoggers() ? "logger in (" + questionMarks(loggers.size()) + ")" : "logger not in (" + questionMarks(loggers.size()) + ")");
            parameters.addAll(loggers);
        });
        filter.getThreadNames().ifPresent(threads -> {
            filters.add("thread in (" + questionMarks(threads.size()) + ")");
            parameters.addAll(threads);
        });
        filter.getNodes().ifPresent(nodes -> {
            filters.add("node_name in (" + questionMarks(nodes.size()) + ")");
            parameters.addAll(nodes);
        });
        filter.getApplications().ifPresent(applications -> {
            filters.add("application_name in (" + questionMarks(applications.size()) + ")");
            parameters.addAll(applications);
        });
        filter.getMdcFilter().ifPresent(mdcFilter -> {
            List<String> mdcFilters = new ArrayList<>();
            mdcFilter.forEach((name, value) -> {
                mdcFilters.add("e.event_id in (select event_id from " + logEventsMdcTable + " where name = ? and value in (" + questionMarks(value.size()) + "))");
                parameters.add(name);
                parameters.addAll(value);
            });
            filters.add("(" + String.join(" AND ", mdcFilters) + ")");
        });
    }

    public String questionMarks(int size) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < size; i++) result.add("?");
        return String.join(",", result);
    }

    /**
     * Open a new connection to the database
     */
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, jdbcUsername, jdbcPassword);
    }

    @Override
    public void processBatch(LogEventBatch batch) {
        if (!batch.isEmpty()) {
            saveLogEvents(batch);
        }
    }

    private void saveLogEvents(LogEventBatch batch) {
        try (Connection connection = getConnection()) {
            try (
                    PreparedStatement eventStmt = connection.prepareStatement("insert into " + logEventsTable + " (event_id, logger, level, level_int, message, formatted_message, message_json, instant, thread, arguments, marker, throwable, stack_trace, node_name, application_name) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                    PreparedStatement mdcStmt = connection.prepareStatement("insert into " + logEventsMdcTable + " (event_id, name, value) values (?, ?, ?)")
            ) {
                for (LogEvent logEvent : batch) {
                    String id = UUID.randomUUID().toString();
                    eventStmt.setString(1, id);
                    eventStmt.setString(2, truncate(logEvent.getLoggerName(), 100));
                    eventStmt.setString(3, logEvent.getLevel().toString());
                    eventStmt.setInt(4, logEvent.getLevel().toInt());
                    eventStmt.setString(5, logEvent.getMessage());
                    eventStmt.setString(6, messageFormatter.format(logEvent.getMessage(), logEvent.getArgumentArray()));
                    eventStmt.setString(7, JsonUtil.toIndentedJson(jsonMessageFormatter.format(logEvent.getMessage(), logEvent.getArgumentArray())));
                    eventStmt.setLong(8, logEvent.getTimeStamp());
                    eventStmt.setString(9, truncate(logEvent.getThreadName(), 100));
                    eventStmt.setString(10, JsonUtil.toIndentedJson(
                            Stream.of(logEvent.getArgumentArray()).map(o -> o != null ? o.toString() : null).collect(Collectors.toList())
                    ));
                    eventStmt.setString(11, truncate(toString(logEvent.getMarker()), 100));
                    eventStmt.setString(12, logEvent.getThrowable() != null ? logEvent.getThrowable().toString() : null);
                    eventStmt.setString(13, logEvent.getThrowable() != null
                            ? JsonUtil.toIndentedJson(exceptionFormatter.createStackTrace(logEvent.getThrowable()))
                            : null);
                    eventStmt.setString(14, truncate(nodeName, 100));
                    eventStmt.setString(15, truncate(applicationName, 100));
                    eventStmt.addBatch();

                    for (Map.Entry<String, String> entry : logEvent.getMdcProperties().entrySet()) {
                        mdcStmt.setString(1, id);
                        mdcStmt.setString(2, truncate(entry.getKey(), 100));
                        mdcStmt.setString(3, truncate(entry.getValue(), 1000));
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

    private String truncate(String s, int maxLength) {
        if (s == null || s.length() < maxLength) return s;
        return s.substring(0, maxLength);
    }

    private final MessageFormatter messageFormatter;
    private final JsonMessageFormatter jsonMessageFormatter;
    private final JsonExceptionFormatter exceptionFormatter;

    @Override
    public LogEventQueryResult query(LogEventFilter filter) {
        try {
            return new LogEventQueryResult(getSummary(filter), list(filter));
        } catch (SQLException e) {
            throw ExceptionUtil.softenException(e);
        }
    }

    /**
     * Retrieves a summary of log events within the specified interval above the threshold log level
     */
    public LogEventSummary getSummary(LogEventFilter filter) throws SQLException {
        LogEventSummary summary = new LogEventSummary();

        try (Connection connection = getConnection()) {
            summary.setRowCount(countRows(connection, filter));
            summary.setFilteredCount(countFilteredRows(connection, filter));
            summary.setMarkers(listDistinct(connection, filter, "marker"));
            summary.setThreads(listDistinct(connection, filter, "thread"));
            summary.setLoggers(listDistinct(connection, filter, "logger"));
            summary.setNodes(listDistinct(connection, filter, "node_name"));
            summary.setApplications(listDistinct(connection, filter, "application_name"));
            summary.setMdcMap(getMdcMap(connection, filter));
        }
        return summary;
    }

    private int countFilteredRows(Connection connection, LogEventFilter filter) throws SQLException {
        List<String> filters = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        buildFilter(filter, filters, parameters);
        try (PreparedStatement statement = connection.prepareStatement("select count(*) from " + logEventsTable + " e where " + String.join(" AND ", filters))) {
            int parameterIndex = 1;
            for (Object parameter : parameters) {
                statement.setObject(parameterIndex++, parameter);
            }
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                } else {
                    throw new IllegalStateException("Should never happen");
                }
            }
        }
    }


    private int countRows(Connection connection, LogEventFilter filter) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select count(*) from " + logEventsTable + " where instant between ? and ? and level_int >= ?")) {
            statement.setLong(1, filter.getStartTime().toEpochMilli());
            statement.setLong(2, filter.getEndTime().toEpochMilli());
            statement.setLong(3, filter.getThreshold().toInt());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                } else {
                    throw new IllegalStateException("Should never happen");
                }
            }
        }
    }

    private Set<String> listDistinct(Connection connection, LogEventFilter filter, String columnName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select distinct " + columnName + " from " + logEventsTable + " where instant between ? and ? and level_int >= ?")) {
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
        try (PreparedStatement statement = connection.prepareStatement("select distinct name, value from " + logEventsMdcTable + " m inner join "  + logEventsTable + " e on m.event_id = e.event_id where instant between ? and ? and level_int >= ?")) {
            statement.setLong(1, filter.getStartTime().toEpochMilli());
            statement.setLong(2, filter.getEndTime().toEpochMilli());
            statement.setLong(3, filter.getThreshold().toInt());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    mdcMap.computeIfAbsent(name, k -> new HashSet<>()).add(rs.getString("value"));
                }
            }
        }
        return mdcMap;
    }
}
