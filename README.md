# Introduction
Papertrail (<http://papertrailapp.com>) is a very cool cloud service I have been experimenting with lately to unify logging from my Java applications. In my circumstances, dealing with configuration and behavioral differences that pop up when desiring to use Log4j with Tomcat and Weblogic 11g simultaneously is a time drain.

I ran into one issue when configuring Log4j to use Papertrail: using UDP as the default for the syslog protocol. I created the Papertrail Appender for Log4j as an alternative to deal with network polices that prevented me from using the packaged SyslogAppender already included with Log4j. Another bonus is the use of SSL TLS for secure transmission of logging data.

It is a very simple appender, relies exclusively on Java SE classes, easy to configure, and intentionally does not have many bells-and-whistles. The most important aspect is the use of concurrency so that Log4j is not held back by a slower network connection, and this also allows the connection to be reused if multiple log messages are pending.

# Installation

## Step 1: Add Papertrail Certificate to JVM

Papertrail provides their certificate directly from the support documentation. Download the .crt file onto the same machine where the JVM you will be using to run your application is installed. You find the support article here:

<http://help.papertrailapp.com/kb/configuration/encrypting-remote-syslog-with-tls-ssl>

To keep things sensible, I have designed to appender to rely on the JVM for certificate management. Therefore, after downloading the Papertrail root certificate, import it into the cacerts file for your Java virtual machine. A command to do this on UNIX might look like the following:

```
sudo keytool -import -alias papertrail -file papertrail.crt -keystore ${JAVA_HOME}/jre/lib/security/cacerts
```

Modify as you need to make the import work for your environment.

## Step 2: Add Dependency to your Application

The best option is to use Maven.

```xml
<dependency>
  <groupId>edu.fhda</groupId>
  <artifactId>log4j-papertrail-appender</artifactId>
  <version>1.0</version>
</dependency>
```

Optionally, you can clone this repo to your disk, and run "mvn clean compile package" to generate a JAR file that you can include in your classpath

## Step 3: Configure Log4j

#### Log4j XML Example

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE log4j:configuration SYSTEM "log4j.dtd">

<log4j:configuration xmlns:log4j="http://jakarta.apache.org/log4j/">
    <appender name="papertrail" class="edu.fhda.log4j.net.PapertrailAppender">
        <param name="papertrailHost" value="YOUR_PAPERTRAIL_HOST" />
        <param name="papertrailPort" value="YOUR_PAPERTRAIL_PORT" />
        <layout class="org.apache.log4j.PatternLayout">
            <param name="ConversionPattern" value="%d{MMM dd HH:mm:ss} your-host your-app: %d{hh:mm aa} [%c] [%t] %m %n" />
        </layout>
    </appender>

    <root>
        <priority value ="debug" />
        <appender-ref ref="papertrail" />
    </root>
</log4j:configuration>
```

#### Grails Example

```java
appenders {
    appender new PapertrailAppender(
        "papertrail",
        "YOUR_PAPERTRAIL_HOST",
        YOUR_PAPERTRAIL_PORT,
        new PatternLayout("%d{MMM dd HH:mm:ss} your-host your-app: %d{hh:mm aa} [%c] [%t] %m %n"))
}
```

Done! After configuring your appender and verbosity, Log4j can now relay log messages for safe keeping at Papertrail.