# Logevents - a simple SLF4J implementation

[![Apache 2.0 License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.logevents/logevents/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.logevents/logevents)
[![Build Status](https://travis-ci.org/jhannes/logevents.png)](https://travis-ci.org/jhannes/logevents)
[![Coverage Status](https://coveralls.io/repos/github/jhannes/logevents/badge.svg?branch=master)](https://coveralls.io/github/jhannes/logevents?branch=master)
[![Vulnerability scan](https://snyk.io/test/github/jhannes/logevents/badge.svg?targetFile=pom.xml)](https://snyk.io/test/github/jhannes/logevents?targetFile=pom.xml)

Setting up and configuring logging should be *easy*, whether you want to do it with
configuration files or in code. Log Events is a tiny logging framework built on top of
SLF4J - the logging lingua franka for Java.

Logevents supports setting up your logging with a `logevents-<profile>.properties` files,
with a custom `LogeventsConfigurator` loaded with Java's service loader framework or
programmatically.

Set up your logging configuration programatically:

```java
LogEventFactory logEventFactory = LogEventFactory.getInstance();

logEventFactory.setRootLevel(Level.ERROR);
logEventFactory.addRootObserver(new DateRollingLogEventObserver("target/logs/application.log"));

logEventFactory.setLevel("org.logevents", Level.INFO);
logEventFactory.addObserver("org.logevents", new DateRollingLogEventObserver("target/logs/info.log"));
```

Logevents tries to make concrete improvements compared to Logback:

* Simpler programmatic configuration by dropping the `LifeCycle` concept
* More navigable code base with less indirection
* Fewer architectural concepts: Log Events is build almost exclusively
  around an observer pattern.
* More concise documentation
* No dependencies


## Architecture


![Architecture Overview](http://www.plantuml.com/plantuml/proxy?src=https://raw.github.com/jhannes/logevents/master/doc/classes.puml?v=2)


## Logging with SLF4J

This section is about SLF4J more than just about Logevents. It can be useful even if you
are not using Logevents. The [SLF4J documentation](https://www.slf4j.org/manual.html)
is a bit exhaustive and not to-the-point.

SLF4J (Simple Logging Facade for Java) is the most used framework by libraries
and applications for logging. It contains only interfaces and an application is
required to include an implementation such as Logback, Logevents, slf4j-log4j or
slf4j-jul to capture the logs.


### Basic logging

In order to log to SLF4J, include the `slf4j-api` dependency with Maven (or gradle):

```xml
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>1.7.25</version>
</dependency>
```

**In addition, you must include a slf4j implementation.**

You can log from your own code by getting a `Logger`:

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DemoClass {

    private static Logger logger = LoggerFactory.getLogger(DemoClass.class);

    public static void main(String[] args) throws IOException {
        logger.warn("Something went wrong");
    }
}
```

### Including information

You can include data in your logging statement - if the event is never printed
(for example if `debug` level is not active), `toString()` is never called
on the message arguments:

```java
logger.debug("Response from {}: {}", url, responseCode);
```


You can include an exception. A logging implementation will generally print the
whole stack trace of the exception:

```java
try {
    ...
} catch (IOException e) {
    logger.error("Failed to communicate with {}", url, e);
}
```

If you want to avoid a costly operation when the event is suppressed, you can
use `isDebugEnabled`, `isWarnEnabled` etc. In this example, if the logging
level is warn or error, `parsePayload` is never called.

```java
if (logger.isInfoEnabled()) {
	logger.info("Message payload: {}", parsePayload());
}
```


### MDC (Mapped Diagnostic Context)

Mapped Diagnostic Context, or MDC, can associate information with the
current thread. This information can be used by the log implementation to
filter messages, redirect messages to different destinations or include in
the output.

It's important to clean up the MDC after use, or MDC values can show up for
another request.

Example of usage:

```java
public LoggingFilter implements javax.servlet.Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        try {
            HttpServletRequest req = (HttpServletRequest)request;
            MDC.put("client-ip", req.getRemoteAddr());
            MDC.put("RequestContext", Optional.ofNullable(req.getHeader("X-Request-Context"))
                    .orElseGet(() -> UUID.randomUUID().toString()));
            MDC.put("user", req.getRemoteUser());
            
            chain.doFilter(req, resp);
        } finally {
            MDC.clear();
        }
    }
}
```

You can configure the logging implementation (logevents or logback) to use the MDC, or you
can even use it from your own code:

```java
HttpURLConnection connection = (HttpURLConnection) serviceUrl.openConnection();
connection.setRequestProperty("X-Request-Context", MDC.get("RequestContext"));

```


## Configuring SLF4J and Logevents

### General SLF4J configuration

1. Include the org.logevents:logevents maven dependency
2. Setup log configuration
   a. If nothing is set up, WARN and higher are logged to console
   b. You can get the `LogEventFactory.getInstance()` directly and set up everything programmatically
   c. If you don't, Logevents will use the Java Service Loader framework to locate an instance of `org.logevents.LogEventConfigurator` interface
   d. The default `LogEventConfiguration` will attempt to determine the current profile and load `logevents-<profile>.properties` and `logfile.properties`


### Configuring SLF4J with Logevents

Include Logevents maven dependency:

```xml
<dependency>
    <groupId>org.logevents</groupId>
    <artifactId>logevents</artifactId>
    <version>0.1</version>
</dependency>
```

### Configuring Log Events programmatically

Use `LogEventFactory` to set up the configuration from your
main method before calling any logging code:


```java
LogEventFactory factory = LogEventFactory.getInstance();
factory.setRootLevel(Level.WARN);
factory.setRootObserver(CompositeLogEventObserver.combine(
        new ConsoleLogEventObserver(),
        new DateRollingLogEventObserver("logs/application.log")
        ));
factory.setLevel("org.myapp", Level.INFO);
factory.setObserver("org.myapp",
        new DateRollingLogEventObserver("log/info.log"));

factory.setObserver("org.logevents",
        new MyCustomSlackLogEventObserver());
```

### Configuring Log Events with Service Loader

If you want to ensure that your configuration is loaded before anything
else happens, you can use the Java Service Loader framework.

1. Create a class that implements `LogeventsConfigurator`
2. Create a file in `META-INF/services/org.logevents.Configurator`
   containing the qualified class name of your configurator.

For example:

```java
package com.example.myapp;

import org.logevents.Configurator;

public class MyAppConfigurator implements Configurator {
    @Override
    public void configure(LogEventFactory factory) {
        factory.setRootLevel(Level.WARN);
        factory.setRootObserver(new ConsoleLogEventObserver(), false);
    }
}
```

This is loaded with the following `META-INF/services/org.logevents.Configurator`:

```
com.example.myapp.MyAppConfigurator
```


### Configuring Log Events with a properties file

The default `LogEventConfigurator` will try to determine the current profile,
using the system properties `profiles`, `profile`, `spring.profiles.active` or
the environment variables `PROFILES`, `PROFILE` or `SPRING_PROFILES_ACTIVE`.
If running in JUnit, the profile `test` will be active by default (TODO).

`DefaultLogEventConfiguration` will try to load `logevents.properties` and
`logevents-<profile>.properties` for any properties set in one of the profile
environment variables or system properties.

The properties files is read from the classpath and from the current working directory,
which is also watched for changes in the files.

The properties-file is on the following format:

```
observer.<observerName>=<observerClassName or alias>
observer.<observerName>.<propertyKey>=<property value>

logger.<category>=[<LEVEL> ]<observerName>

root=[<LEVEL> ]<observerName>
```

For observers in `org.logevents.observers` package, the package name
can be omitted - so `TextLogEventObserver` and be used instead of
`org.logevents.TextLogEventObserver`.


### PatternLogEventFormatter and ExceptionFormatter

If you do programmatic configuration, the recommended approach is to implement
message formatting in code. See `ConsoleLogEventFormatter` as an example.

However, if you use properties files for configuration, you will probably
want to use `PatternLogEventFormatter` to format the log events. Here
is an example:

```
observer.file=FileLogEventObserver
observer.file.filename=logs/application-%date.log
observer.file.formatter=PatternLogEventFormatter
observer.file.formatter.pattern=%date{HH:mm:ss} %highlight([%5level]) [%thread] [%mdc{user:-<no user>}] %logger{20}: %message
observer.file.formatter.exceptionFormatter=CauseFirstExceptionFormatter
observer.file.formatter.exceptionFormatter.packageFilter=sun.nio, my.internal.package

root=INFO file
```

The following conversion words are supported:

* `%date{<format>, <timezone>}`: The time when the log event was generated. Examples:
  `%date{HH:mm:ss, UTC}`. The parameters are passed to `DateTimeFormatter.ofPattern()`
  and `ZoneId.of()` respectively
* `%logger{<length>}`: The name of the logger (the parameter to LoggerFactory.getLogger())
* `%message`: The formatted message of the log event, with "{}" replaced by log arguments
* `%thread`: The thread that created the log event
* `%class`: The class name of the caller of the log event
* `%method`: The class method of the caller of the log event
* `%file`: The file name of the caller of the log event
* `%line`: The line number of the caller of the log event
* `%mdc{<key>:-<default>}`: The value of the specified MDC variable.
   `%mdc` outputs all MDC variables.
* `%highlight(<sub pattern>)` will format the sub-pattern as normal and output
  the text in a color appropriate for the log level of the event
* `%red(<sub pattern>)`, `%green(<sub pattern>)`, `%blue(<sub pattern>)`, etc
  formats the message according to the sub pattern and outputs it in the given color

As opposed to log4j and logback, logevents don't consider exception formatting as part
of the normal formatting pattern. Instead, you must set `.pattern.exceptionFormatter`
to customize the output of exceptions. On the upside, this means that it's
quite easy to create and plug in your own classes for exception formatting.


### Intercepting log4j and JUL logging

SLF4J comes out of the box with a bridge from java.util.logging
(jul-to-slf4j) and log4j (log4j-to-slf4j). This is very useful if
you are using a third party library which uses one of these
logging implementations (for example Tomcat). For log4j, you
simply need to include a dependency in your `pom.xml`:

**For log4j:**

```xml
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>log4j-to-slf4j</artifactId>
    <version>1.7.25</version>
</dependency>
```

**For java.util.logging**

```xml
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>jul-to-slf4j</artifactId>
    <version>1.7.25</version>
</dependency>
```

java.util.logging is slightly more involved: In addition, you have
to call `SLF4JBridgeHandler.install()` from your own code. However,
with Logevents, this step isn't needed as Logevents will call this
for you at the earliest possibility (usually at the moment when you
first call `LoggerFactory.getLogger`). 
 



## Advanced usage patterns

### Batching

For some observers, it makes more sense to not send messages at once, but rather
wait until we know there aren't more messages coming. This is useful for example
when sending email (where you would often rather wait a minute and get a full list
of log messages in one email) or Slack (where you may want to have a cool down
period before sending more messages). Here is an example of how you can set up
a batching log event observer:

```java
LogEventFactory factory = LogEventFactory.getInstance();
factory.setRootLevel(Level.INFO);

// Get yours at https://www.slack.com/apps/manage/custom-integrations
URL slackUrl = new URL("https://hooks.slack.com/services/....");
SlackLogEventBatchProcessor slackLogEventBatchProcessor = new SlackLogEventBatchProcessor(slackUrl);
slackLogEventBatchProcessor.setUsername("Loge Vents");
slackLogEventBatchProcessor.setChannel("test");

BatchingLogEventObserver batchEventObserver = new BatchingLogEventObserver(slackLogEventBatchProcessor);
batchEventObserver.setCooldownTime(Duration.ofSeconds(5));
batchEventObserver.setMaximumWaitTime(Duration.ofMinutes(3));
batchEventObserver.setIdleThreshold(Duration.ofSeconds(3));
factory.setRootObserver(CompositeLogEventObserver.combine(
        new LevelThresholdConditionalObserver(Level.WARN, batchEventObserver),
        new ConsoleLogEventObserver()));
```

Or with properties files:

```
observer.slack=LevelThresholdConditionalObserver
observer.slack.threshold=WARN

observer.slack.delegate=BatchingLogEventObserver
observer.slack.delegate.cooldownTime=PT10S
observer.slack.delegate.maximumWaitTime=PT1M
observer.slack.delegate.idleThreshold=PT5S

observer.slack.delegate.batchProcessor=org.logevents.observers.batch.SlackLogEventBatchProcessor
observer.slack.delegate.batchProcessor.slackUrl=https://hooks.slack.com/services/xxxx/xxxxx

logger.org.logeventsdemo.Main=DEBUG slack

```

### Slack

Logevents comes with an example implementation of logging to Slack in
the form of `SlackLogEventBatchProcessor`. You can subclass this to customize
your Slack messages.


### Servlets

Logevents comes with two servlets that you can add to your servlet container.

`org.logevents.extend.servlets.LogEventsServlet.doGet` can list up
log events as JSON (to be formatted by your own JavaScript). It supports
four optional query parameters: `level` (default INFO), `category`, `offset`
and `count`.  (TODO)

`org.logevents.extend.servlets.LogEventConfigurationServlet`:
`doGet` lists up all active loggers and observers as JSON.
`doPost` allows to change the level and observer (TODO) for a single logger.


### JUnit

If no configuration is loaded, Logevents logs at WARN level to the console.
This is appropriate for most test scenarios. If you want to suppress
warn and error logging or if you want to capture log events to assert,
you can use the included `org.logevents.extend.junit.LogEventRule`.

For example:

```java
public class LogEventRuleTest {

    private Logger logger = LoggerFactory.getLogger("com.example.application.Service");

    @Rule
    public LogEventRule logEventRule = new LogEventRule("com.example", Level.DEBUG);

    @Test
    public void shouldCaptureLogEvent() {
        logger.debug("Hello world");
        logEventRule.assertSingleMessage("Hello world", Level.DEBUG);
        logger.error("Even though this is an error event, it is not displayed");
    }

}
```


### JMX (TODO)


## Questions and answers

### Why not logback

When designed, logback was created with a lot of flexibilty for mind for
possible future requirements. As it has become in wider use, much of this
flexibility has remained untapped, while the architecture is paying the cost in
increased indirection.

Based on the experience from using Logback, Logevents is trying to only support
the flexibility that used in the most common scenarios for Logback. This means
there are probably things Logback can do that Logevents will be unable to handle.
On the other hand, most of the common extension scenarios will probably require
less code to implement with Logevents.


### TODO

* MDC-based batching
* JMX

