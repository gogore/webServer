package org.gradle;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebServerTest {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Test
    public void runWebServer() throws IOException, InterruptedException {
        NetworkService networkService = new NetworkService(1234, 100);
        networkService.run();
    }

    @Test
    public void headAnalysis() throws IOException {
        HttpRequestParser parser = new HttpRequestParser();
        StringBuilder sb = new StringBuilder();
        append(sb, "GET /test?kk=aa HTTP/1.1");
        append(sb, "");
        append(sb, "Host: localhost:1234");
        append(sb, "Connection: keep-alive");
        append(sb, "Cache-Control: max-age=0");
        append(sb, "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        append(sb, "Upgrade-Insecure-Requests: 1");
        append(sb, "User-Agent: Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/48.0.2564.116 Safari/537.36");
        append(sb, "Accept-Encoding: gzip, deflate, sdch");
        append(sb, "Accept-Language: ko-KR,ko;q=0.8,en-US;q=0.6,en;q=0.4");
        parser.parseRequest(sb.toString());
        if (parser.getRequestLine().indexOf("GET") > -1) {
            String getInfo = parser.getRequestLine().replaceAll("GET\\s\\/([^\\s]+)\\sHTTP/1.1", "$1");
            String[] split = getInfo.split("\\?");
            System.out.println(split[0]);
        }
    }

    private void append(StringBuilder sb, String str) {
        sb.append(str);
        sb.append(System.getProperty("line.separator"));
    }
}
