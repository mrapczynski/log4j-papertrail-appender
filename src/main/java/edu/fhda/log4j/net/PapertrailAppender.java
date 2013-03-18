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

    private final String papertrailHost;
    private final int papertrailPort;

    private final LinkedBlockingQueue<String> logMessageQueue = new LinkedBlockingQueue<String>();
    private final ExecutorService workerService = Executors.newFixedThreadPool(1);

    private SSLSocketFactory sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();

    /**
     * Create a new PapertrailAppender instance with a configurable name, host, port, and layout.
     * @param name The name to use for the appender
     * @param papertrailHostUser The remote hostname for connecting remotely to Papertrail
     * @param papertrailPortUser The remote port to use for connecting remotely to Papertrail
     * @param layout A Layout instance for formatting log messages
     */
    public PapertrailAppender(String name, String papertrailHostUser, int papertrailPortUser, Layout layout) {
        // Configure private fields for Papertrail remote configuration
        this.setName(name);
        this.setLayout(layout);
        this.papertrailHost = papertrailHostUser;
        this.papertrailPort = papertrailPortUser;

        // Build the worker thread for processing log messages
        workerService.submit(new Runnable() {
            @Override
            public void run() {
            try {
                // Begin an infinite loop to process log messages
                while(true) {
                    // Block and wait for log messages to be queued
                    String nextWaitingMessage = logMessageQueue.take();

                    // Create a secure socket to Papertrail
                    SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(papertrailHost, papertrailPort);
                    sslSocket.startHandshake();

                    // Write log message
                    // (a limit of 8KB is placed on the message test as specified by Papertrail)
                    BufferedWriter socketOut = new BufferedWriter(new OutputStreamWriter(sslSocket.getOutputStream()));
                    socketOut.write(nextWaitingMessage.substring(0, (nextWaitingMessage.length() < MAX_MESSAGE_SIZE) ? nextWaitingMessage.length() : MAX_MESSAGE_SIZE));

                    // Nested loop to check for additional waiting messages (connection reuse)
                    while(logMessageQueue.peek() != null) {
                        // Get the next available log message
                        nextWaitingMessage = logMessageQueue.take();

                        // Write log message (same 8KB limit as above)
                        socketOut.write(nextWaitingMessage.substring(0, (nextWaitingMessage.length() < MAX_MESSAGE_SIZE) ? nextWaitingMessage.length() : MAX_MESSAGE_SIZE));
                    }

                    // Clean up stream and socket
                    socketOut.flush();
                    socketOut.close();
                    sslSocket.close();
                }
            }
            catch(Exception papertrailThreadError) {
                // Logging should be fast and error-free. Getting an exception thrown is really bad.
                // Make a loud noise so the error is visible and diagnosable.
                papertrailThreadError.printStackTrace(System.err);
            }
            }
        });
    }

    @Override
    protected void append(LoggingEvent loggingEvent) {
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

    public int getPapertrailPort() {
        return papertrailPort;
    }

}
