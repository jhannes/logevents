# Logevents - a simple SLF4J implementation

**Status: Concept implementation. I'm not sure if this is something worth doing or not!**

Setting up and configuring logging should be *easy*, whether you want to do it with
configuration files or in code. Log Events is a tiny logging framework built on top of
SLF4J - the logging lingua franka for Java.

Set up your logging configuration programatically:

```java
LogEventConfigurator configurator = new LogEventConfigurator();
configurator.setLevel(Level.WARN);
configurator.setObserver(configurator.combine(
        configurator.consoleObserver(),
        configurator.dateRollingAppender("logs/application.log")
        ));
configurator.setLevel("org.myapp", Level.INFO);
configurator.setObserver("org.myapp",
        configurator.dateRollingAppender("log/info.log"), true);
```

Log Events tries to make concrete improvements compared to Logback:

* Simpler programmatic configuration by dropping the `LifeCycle` concept
* More navigable code base with less indirection
* Fewer architectural concepts: Log Events is build almost exclusively
  around an observer pattern.
* More concise documentation
* No dependencies


## Architecture


![Architecture Overview](http://www.plantuml.com/plantuml/proxy?src=https://raw.github.com/jhannes/logevents/master/doc/classes.puml)


## Logging with SLF4J

The SLF4J documentation is a bit extensive and not to-the-point. This section is
about SLF4J and not Logevents. It can be useful even if you are not using Logevents.

SLF4J (Simple Logging Facade for Java) is the most used framework by libraries
and applications for logging. It contains only interfaces and an application is
required to include an implementation such as Logback, Logevents, slf4j-Log4j or
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


### MDC (Diagnostic Context)

Message (?) Diagnostic Context, or MDC, can associate information with the
current thread. This information can be used by the log implementation to
filter messages, redirect messages to different destinations or include in
the output.

It's important to clean up the MDC after use.

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
   b. You can get the `LogEventConfiguration.instance()` directory and set up everything programmatically
   c. If you don't, Logevents will use the Java Service Loader framework to locate an instance of `org.logevents.LogEventConfigurator` interface
   d. The default `LogEventConfiguration` will try to determine the current profile and load `logevents-<profile>.properties` and `logfile.properties`


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

Use `LogEventConfigurator` to set up the configuration from your
main method before calling any logging code:


```java
LogEventConfigurator configurator = new LogEventConfigurator();
configurator.setLevel(Level.WARN);
configurator.setObserver(configurator.combine(
        configurator.consoleObserver(),
        configurator.dateRollingAppender("logs/application.log")
        ));
configurator.setLevel("org.myapp", Level.INFO);
configurator.setObserver("org.myapp",
        configurator.dateRollingAppender("log/info.log"), true);

configurator.setObserver("org.logevents",
        new MyCustomSlackLogEventObserver(),
        true);
```

### Configuring Log Events with Service Loader (TODO)

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
        factory.setLevel(factory.getRootLogger(), Level.WARN);
        factory.setObserver(factory.getRootLogger(), consoleObserver(), false);
    }
}
```

This is loaded with the following `META-INF/services/org.logevents.Configurator`:

```
com.example.myapp.MyAppConfigurator
```


### Configuring Log Events with a properties file (TODO)

The default `LogEventConfiguration` will try to determine the current profile, using the system properties `profiles`, `profile`, `spring.profiles.active` or the environment variables `PROFILES`, `PROFILE` or `SPRING_PROFILES_ACTIVE`. If running in JUnit, the profile `test` will be active by default.

Use `LogEventsConfigurator.load(filename)` to read the configuration from a
properties file. By using ... you can convert a YAML file to `Properties` and
use that instead.



(TODO: `configurator.load(YamlProperties.load("logging.yml")`)

The properties-file is on the following format:

```
observer.<observerName>=<observerClassName or alias>
observer.<observerName>.<propertyKey>=<property value>

logger.<category>=[<LEVEL> ]<observerName>
logger.<category>.includeParentObserver=(true|false)

root=[<LEVEL> ]<observerName>
```

For observers in `org.logevents.observers` package, the package name
can be omitted - so `TextLogEventObserver` and be used instead of
`org.logevents.TextLogEventObserver`.


### Intercepting log4j and JUL logging

 

## Advanced usage patterns

### Batching

For some observers, it makes more sense to not send messages at once, but rather
wait until we know there aren't more messages coming. This is useful for example
when sending email (where you would often rather wait a minute and get a full list
of log messages in one email) or Slack (where you may want to have a cool down
period before sending more messages). Here is an example of how you can set up
a batching log event observer:

```java
SlackLogEventBatchProcessor slackLogEventBatchProcessor = new SlackLogEventBatchProcessor();

BatchingLogEventObserver batchEventObserver = configurator.batchEventObserver(slackLogEventBatchProcessor);
batchEventObserver.setCooldownTime(Duration.ofSeconds(5));
batchEventObserver.setMaximumWaitTime(Duration.ofMinutes(3));
batchEventObserver.setIdleThreshold(Duration.ofSeconds(3));

configurator.setObserver(configurator.combine(
        LogEventConfigurator.levelThresholdObserver(Level.WARN, batchEventObserver),
        LogEventConfigurator.consoleObserver(new AnsiLogEventFormatter())));
```

Or with properties files:

```
observer.slack=BatchingLogEventObserver
observer.slack.cooldownTime=PT30S
observer.slack.maximumWaitTimm=PT5M
observer.slack.idleThreshold=PT10S
observer.slack.batchProcessor=org.logevents.observers.batch.SlackLogEventBatchProcessor
observer.slack.batchProcessor.channel=Servers
observer.slack.batchProcessor.slackUrl=...
```


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


### JMX


## Questions and answers

### Why not logback


### TODO

* Implement proper ANSI logging for console
* Move LogEventConfiguration and LogEventFactory around to my satisfaction
* Implement emergency logging if something fails in logevents
