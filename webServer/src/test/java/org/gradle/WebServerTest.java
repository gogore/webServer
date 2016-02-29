package org.gradle;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebServerTest {

    @Test
    public void runWebServer() throws IOException, InterruptedException {
        
        Logger logger = LoggerFactory.getLogger(this.getClass());
        NetworkService networkService = new NetworkService(1234, 100);
        //networkService.run();
        System.out.println(1);
        logger.debug("asdf");
        networkService.close();
    }
}
