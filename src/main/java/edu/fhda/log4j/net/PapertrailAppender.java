package edu.fhda.log4j.net;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Layout;
import org.apache.log4j.spi.LoggingEvent;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The PapertrailAppender sends logger events directly to Papertrail (papertrailapp.com) using TCP over TLS. Designed
 * as an alternative to the packaged Log4j SyslogAppender for applications deployed on a private network where
 * outgoing UDP connections (such as for syslog) are blocked by the network policy.
 * blocked.
 *
 * @author Matt Rapczynski, Foothill-De Anza College District
 * @verson 1.0
 */
public class PapertrailAppender extends AppenderSkeleton {

    /**
     * Constant to be used in trimming log messages to hard limit of 8 kilobytes as specified in the Papertrail
     * support documentation.
     */
    public final int MAX_MESSAGE_SIZE = 8192;

    private String papertrailHost;
    private int papertrailPort;
    private boolean appenderInitialized = false;

    private final LinkedBlockingQueue<String> logMessageQueue = new LinkedBlockingQueue<String>();
    private final ExecutorService workerService = Executors.newFixedThreadPool(1);

    private SSLSocketFactory sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();

    /**
     * Create a new unconfigured PapertrailAppender instance.
     */
    public PapertrailAppender() {
    }

    /**
     * Create a new PapertrailAppender instance with a configurable name, host, port, and layout.
     * @param name The name to use for the appender
     * @param hostname The remote hostname for connecting to Papertrail
     * @param port The remote port to use for connecting to Papertrail
     * @param layout A Layout instance for formatting log messages
     */
    public PapertrailAppender(String name, String hostname, int port, Layout layout) {
        // Configure private fields for Papertrail remote configuration
        this.setName(name);
        this.setLayout(layout);
        this.papertrailHost = hostname;
        this.papertrailPort = port;
    }

    /**
     * Perform the actual action of appending a log event.
     * @param loggingEvent The LoggingEvent object to be appended.
     */
    @Override
    protected void append(LoggingEvent loggingEvent) {
        // Does a one-time initialization need to be performed?
        if(!(appenderInitialized)) {
            // Build the worker thread for relaying log messages to Papertrail
            workerService.submit(new Runnable() {
                @Override
                public void run() {
                try {
                    // Begin an infinite loop to process log messages
                    while(true) {
                        // Block and wait for log message(s) to be queued
                        String nextWaitingMessage = logMessageQueue.take();

                        // Create a secure socket to Papertrail
                        SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(papertrailHost, papertrailPort);
                        sslSocket.startHandshake();

                        // Write log message
                        BufferedWriter socketOut = new BufferedWriter(new OutputStreamWriter(sslSocket.getOutputStream()));
                        socketOut.write(truncateMessage(nextWaitingMessage));

                        // Nested loop to check for additional waiting messages (connection reuse)
                        while(logMessageQueue.peek() != null) {
                            // Get the next available log message
                            nextWaitingMessage = logMessageQueue.take();

                            // Write log message
                            socketOut.write(truncateMessage(nextWaitingMessage));
                        }

                        // Clean up stream and socket
                        socketOut.flush();
                        socketOut.close();
                        sslSocket.close();
                    }
                }
                catch(Exception workerError) {
                    // Logging should be fast and error-free. Getting an exception thrown is really bad.
                    // Make a loud noise so the error is visible and diagnosable.
                    workerError.printStackTrace(System.out);
                }
                }
            });

            appenderInitialized = true;
        }

        // Use the provided layout and queue up a log message to be written
        this.logMessageQueue.add(this.getLayout().format(loggingEvent));
    }

    @Override
    public void close() {
    }

    @Override
    public boolean requiresLayout() {
        return true;
    }

    public String getPapertrailHost() {
        return papertrailHost;
    }

    public void setPapertrailHost(String papertrailHost) {
        this.papertrailHost = papertrailHost;
    }

    public int getPapertrailPort() {
        return papertrailPort;
    }

    public void setPapertrailPort(int papertrailPort) {
        this.papertrailPort = papertrailPort;
    }

    /**
     * Utility function to truncate formatted log messages that meet the maximum message size as specified
     * in the Papertrail support documentation. See also static constant MAX_MESSAGE_SIZE.
     * @param messageText The log message formatted by a Log4j Layout
     * @return The log message, but truncated if longer than MAX_MESSAGE_SIZE
     */
    private String truncateMessage(String messageText) {
        return messageText.substring(0, (messageText.length() < MAX_MESSAGE_SIZE) ? messageText.length() : MAX_MESSAGE_SIZE);
    }

}
