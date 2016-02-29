package org.gradle;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Hashtable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for HTTP request parsing as defined by RFC 2612:
 *
 * Request = Request-Line ; Section 5.1 (( general-header ; Section 4.5 |
 * request-header ; Section 5.3 | entity-header ) CRLF) ; Section 7.1 CRLF [
 * message-body ] ; Section 4.3
 *
 * @author izelaya
 *
 */
public class HttpRequestParser {
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private String _requestLine;
    private Hashtable<String, String> _requestHeaders;
    private StringBuffer _messagetBody;

    public HttpRequestParser() {
        _requestHeaders = new Hashtable<String, String>();
        _messagetBody = new StringBuffer();
    }

    /**
     * Parse and HTTP request.
     *
     * @param request
     *            String holding http request.
     * @throws IOException
     *             If an I/O error occurs reading the input stream.
     */
    public void parseRequest(String request) throws IOException {
        BufferedReader reader = new BufferedReader(new StringReader(request));

        setRequestLine(reader.readLine()); // Request-Line ; Section 5.1

        String header = reader.readLine();
        while (header != null && header.length() > 0) {
            appendHeaderParameter(header);
            header = reader.readLine();
        }

        String bodyLine = reader.readLine();
        while (bodyLine != null) {
            appendMessageBody(bodyLine);
            bodyLine = reader.readLine();
        }

    }

    /**
     *
     * 5.1 Request-Line The Request-Line begins with a method token, followed by
     * the Request-URI and the protocol version, and ending with CRLF. The
     * elements are separated by SP characters. No CR or LF is allowed except in
     * the final CRLF sequence.
     *
     * @return String with Request-Line
     */
    public String getRequestLine() {
        return _requestLine;
    }

    private void setRequestLine(String requestLine)  {
        if (requestLine == null || requestLine.length() == 0) {
            logger.error("Invalid Request-Line: " + requestLine);
        }
        _requestLine = requestLine;
    }

    private void appendHeaderParameter(String header)  {
        int idx = header.indexOf(":");
        if (idx == -1) {
            logger.error("Invalid Header Parameter: " + header);
        }
        _requestHeaders.put(header.substring(0, idx), header.substring(idx + 1, header.length()));
    }

    /**
     * The message-body (if any) of an HTTP message is used to carry the
     * entity-body associated with the request or response. The message-body
     * differs from the entity-body only when a transfer-coding has been
     * applied, as indicated by the Transfer-Encoding header field (section
     * 14.41).
     * @return String with message-body
     */
    public String getMessageBody() {
        return _messagetBody.toString();
    }

    private void appendMessageBody(String bodyLine) {
        _messagetBody.append(bodyLine).append("\r\n");
    }
}