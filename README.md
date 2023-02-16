# Logevents - a simple SLF4J implementation

[![Apache 2.0 License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.logevents/logevents/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.logevents/logevents)
[![Javadoc](https://img.shields.io/badge/javadoc-logevents-blue)](https://jhannes.github.io/logevents/apidocs/)
[![Build Status](https://github.com/jhannes/logevents/workflows/Java%20CI/badge.svg)](https://github.com/jhannes/logevents/actions/workflows/delivery.yml)
[![Coverage Status](https://coveralls.io/repos/github/jhannes/logevents/badge.svg?branch=main)](https://coveralls.io/github/jhannes/logevents?branch=main)
[![Vulnerability scan](https://snyk.io/test/github/jhannes/logevents/badge.svg?targetFile=pom.xml)](https://snyk.io/test/github/jhannes/logevents?targetFile=pom.xml)

Setting up and configuring logging should be *easy*, whether you want to do it with configuration files or in code. Log
Events is a small (265kb, *no dependencies*) logging framework built on top of SLF4J - the logging lingua franka for
Java.

For detailed instructions, see [the manual](MANUAL.md).

## Quick start

### Add dependencies (for Maven)

```xml
<dependencies>
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
        <version>2.0.3</version>
    </dependency>

    <dependency>
        <groupId>org.logevents</groupId>
        <artifactId>logevents</artifactId>
        <version>0.4.3</version>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

### Simple configuration

```properties
root=WARN file,console
logger.org.example=INFO
logger.org.example.myapp=DEBUG,TRACE@marker=HTTP_REQUEST&mdc:user=admin
logevents.status=CONFIG
```

By default, the [`file`](https://jhannes.github.io/logevents/apidocs/org/logevents/observers/FileLogEventObserver.html) observer will log to a file named `logs/your-app-name-%date.log` and [`console`](https://jhannes.github.io/logevents/apidocs/org/logevents/observers/ConsoleLogEventObserver.html) logs ANSI-colored logs to the console.

The default level for loggers with this configuration will be `WARN`, by `org.example` will log at `INFO` and `org.example.myapp` will log at `DEBUG`, or trace for HTTP_REQUEST when the `user` is `admin`. See [LogEventFilter](https://jhannes.github.io/logevents/apidocs/org/logevents/impl/LogEventFilter.html) for details. Use level `NONE` to turn off logging.

Logevents will output [configuration information](https://jhannes.github.io/logevents/apidocs/org/logevents/status/package-summary.html) to system err.

### Configuration in a [Twelve-Factor](https://12factor.net/) setting

Here is an example setup in a cloud environment. Logevents can be configured with environment variables and use stdout as the main channel for logging, outputting JSON logs for more powerful downstream parsing

```bash
# Output logevents configuration debug to stderr
LOGEVENTS_STATUS=CONFIG
# Skip sun.reflect packages in stack traces
LOGEVENTS_PACKAGEFILTER=sun.reflect

# By default only output to console at DEBUG
LOGEVENTS_ROOT=DEBUG console

# Console format should be JSON for easier log parsing
LOGEVENTS_OBSERVER_CONSOLE_FORMATTER=ConsoleJsonLogEventFormatter

# Install logging to Microsoft Teams for all ERROR messages
LOGEVENTS_ROOT_OBSERVER_TEAMS=ERROR
LOGEVENTS_OBSERVER_TEAMS=MicrosoftTeamsLogEventObserver
LOGEVENTS_OBSERVER_TEAMS_URL=https://example.webhook.office.com/webhookb2/...

# Turn down logging for selected packages
LOGEVENTS_LOGGER_ORG_ECLIPSE_JETTY=WARN
# Turn up logging to TRACE for com.example message for selected users are
LOGEVENTS_LOGGER_COM_EXAMPLE=DEBUG,TRACE@mdc:user=superuser|admin|tester
```


## Features:

* [Console logging](https://jhannes.github.io/logevents/apidocs/org/logevents/observers/ConsoleLogEventObserver.html)
  with good default colors (also on Windows). Use `observer.console.format=ConsoleJsonLogEventFormatter` to output
  single-line JSON logs, suitable for log parsing
* [File logging](https://jhannes.github.io/logevents/apidocs/org/logevents/observers/FileLogEventObserver.html) with
  reasonable defaults
* [JUnit support](https://jhannes.github.io/logevents/apidocs/org/logevents/extend/junit/ExpectedLogEventsRule.html) to
  easy assert on what is logged
* [Email logging](https://jhannes.github.io/logevents/apidocs/org/logevents/observers/SmtpLogEventObserver.html),
  including throttling to reduce spamming when get lots of log messages
* [Slack](https://jhannes.github.io/logevents/apidocs/org/logevents/observers/SlackLogEventObserver.html) to send log
  messages to you favorite chat channel
* [Microsoft Teams](https://jhannes.github.io/logevents/apidocs/org/logevents/observers/MicrosoftTeamsLogEventObserver.html)
* [Logging to database](https://jhannes.github.io/logevents/apidocs/org/logevents/observers/DatabaseLogEventObserver.html)
* [Display logs on a web dashboard](https://jhannes.github.io/logevents/apidocs/org/logevents/observers/WebLogEventObserver.html)
* [Elasticsearch](https://jhannes.github.io/logevents/apidocs/org/logevents/observers/ElasticsearchLogEventObserver.html)
  . Logging directly to Elastic search Index API avoids edge cases when writing and parsing log files
* [Humio](https://jhannes.github.io/logevents/apidocs/org/logevents/observers/HumioLogEventObserver.html)
    . Logging directly to Humio via their [Elastic search Bulk API](https://library.humio.com/stable/docs/ingesting-data/log-shippers/other-log-shippers/#elasticsearch-bulk-api).
* [Azure Application Insights](https://jhannes.github.io/logevents/apidocs/org/logevents/extend/azure/ApplicationInsightsLogEventObserver.html) (
  requires optional com.microsoft.azure:applicationinsights-core dependency)
* [JMX integration](https://jhannes.github.io/logevents/apidocs/org/logevents/jmx/LogEventsMBeanFactory.html) to view
  the configuration and tweak log levels
* [Filter loggers](https://jhannes.github.io/logevents/apidocs/org/logevents/impl/LogEventFilter.html) on
  markers and MDC values (e.g. `logger.org.example.app=INFO,DEBUG@mdc:user=superuser|admin`)
* [Filter observers](https://jhannes.github.io/logevents/apidocs/org/logevents/observers/AbstractFilteredLogEventObserver.html)
  on markers and MDC values
