package org.gradle;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

public class WebServerTest {

    @Test
    public void runWebServer() throws IOException, InterruptedException {
        NetworkService networkService = new NetworkService(1234, 100);
        networkService.run();
    }
}
