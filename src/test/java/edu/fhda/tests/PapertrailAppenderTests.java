package edu.fhda.tests;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;
import org.junit.Test;

public class PapertrailAppenderTests {

    static Logger log = Logger.getLogger(PapertrailAppenderTests.class);

    @Test
    public void testAppenderIsConfigurable() {
        try {
            // Configure Log4j with the test XML configuration
            DOMConfigurator.configure(getClass().getClassLoader().getResource("edu/fhda/tests/log4j.xml"));
            log.info("Log4j configured successfully");
        }
        catch(Exception unitTestError) {
            unitTestError.printStackTrace(System.out);
        }
    }

}
