package org.gradle;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkService extends Thread {
    private static final String STATUS_404 = "404 Not Found";
    private static final String STATUS_200 = "200 OK";
    private int bufferSize;
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private Selector selector = null;
    private ServerSocketChannel serverSocketChannel;
    private boolean runFlag = true;
    private boolean exit = false;

    /**
     * 소켓 생성
     *
     * @param bufferSize
     *
     * @throws IOException
     */
    public NetworkService(int port, int bufferSize) {
        try {
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            selector = Selector.open();
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            serverSocketChannel.socket().bind(new InetSocketAddress(port));
            this.bufferSize = bufferSize;
        } catch (IOException e) {
            close();
        }
    }

    /**
     * 소켓 동작
     */
    @Override
    public void run() {
        while (runFlag) {
            try {
                if (selector.selectNow() != 0) {
                    Set<SelectionKey> selectedKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iterator = selectedKeys.iterator();
                    while (iterator.hasNext()) {
                        SelectionKey selectionKey = iterator.next();
                        if (selectionKey.isAcceptable()) {
                            accept(selectionKey);
                        } else if (selectionKey.isReadable()) {
                            read(selectionKey);
                        } else {
                        }
                        iterator.remove();
                    }
                }
            } catch (ClosedSelectorException | IOException e) {
                runFlag = false;
            }
        }
    }

    /**
     * 클라이언트가 접속한후에 처리되는 메소드
     *
     * @param selectionKey
     * @throws IOException
     */
    private void accept(SelectionKey selectionKey) throws IOException {
        // 받아들인 채널로 서버소켓채널 생성
        ServerSocketChannel server = (ServerSocketChannel) selectionKey.channel();
        // 받아들인 서버소켓채널로 소켓채널 생성
        SocketChannel sc = server.accept();
        sc.configureBlocking(false);
        // 접속된후에는 읽기 모드로 변경
        sc.register(selector, SelectionKey.OP_READ);
    }

    /**
     * 접속한후에 read된 내용을 뷰이벤트로 전파
     *
     * @param selectionKey
     */
    private void read(SelectionKey selectionKey) {
        boolean isEos = false;
        byte[] rawData = (byte[]) selectionKey.attachment();
        if (rawData == null) {
            rawData = new byte[0];
            selectionKey.attach(rawData);
        }
        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
        ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
        try {
            // 전달 받은 내용을 버퍼에 입력한다.
            int read = socketChannel.read(buffer);
            isEos = (read < bufferSize);
            buffer.clear();
            byte[] bytearr = new byte[bufferSize];
            int totalSize = rawData.length + bytearr.length;
            byte[] totalArr = new byte[totalSize];
            buffer.get(bytearr);

            System.arraycopy(rawData, 0, totalArr, 0, rawData.length);
            System.arraycopy(bytearr, 0, totalArr, rawData.length, bytearr.length);
            selectionKey.attach(totalArr);
            if (isEos) {
                byte[] resultByteArray = (byte[]) selectionKey.attachment();
                String planStr = new String(resultByteArray, "UTF-8");

                logger.debug(planStr);
                Map<String, String> requestMap = analysis(resultByteArray);
                if (requestMap.get("filePath") != null) {
                    fileWriter(socketChannel, requestMap);
                    socketChannel.close();
                } else {
                    socketChannel.write(ByteBuffer.wrap("not yet".getBytes()));
                    socketChannel.close();
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            try {
                selectionKey.cancel();
                socketChannel.close();
            } catch (IOException e1) {
                logger.debug(e1.getMessage());
            }
            close();
        } finally {
            if (buffer != null) {
                buffer = null;
            }
        }
        if (this.exit) {
            close();
        }
    }

    /**
     * 리퀘스트 정보를 분석한다.
     * 
     * @param resultByteArray
     * @return
     * @throws IOException
     */
    private Map<String, String> analysis(byte[] resultByteArray) throws IOException {
        Map<String, String> reqReport = new HashMap<String, String>();
        HttpRequestParser parser = new HttpRequestParser();
        parser.parseRequest(new String(resultByteArray));
        if (parser.getRequestLine().indexOf("GET") > -1) {
            String getInfo = parser.getRequestLine().replaceAll("GET\\s\\/([^\\s]+)\\sHTTP/1.1", "$1");
            String[] split = getInfo.split("\\?");
            String doWhat = split[0];
            if ("close".equals(doWhat)) {
                this.exit = true;
                return null;
            } else {
                if (doWhat.indexOf(".") > -1) {
                    Path path = Paths.get(doWhat);
                    if (Files.exists(path)) {
                        String contentType = getContentType(doWhat);
                        reqReport.put("contentLength", "100");
                        reqReport.put("contentType", contentType +"; charset=UTF-8");
                        reqReport.put("status", STATUS_200);
                    } else {
                        reqReport.put("contentLength", "0");
                        reqReport.put("contentType", "text/html; charset=UTF-8");
                        reqReport.put("status", STATUS_404);
                    }
                    reqReport.put("filePath", doWhat);
                } else {
                    reqReport.put("action", doWhat);
                    reqReport.put("status", STATUS_200);
                }
            }
        }
        return reqReport;
    }

    private String getContentType(String fileName) {
        String contentType = "";
        if(fileName.endsWith("html")){
            contentType = "text/html";
        } else if(fileName.endsWith("png")){
            contentType = "image/png";
        } else {
            contentType = "application/octet-stream";
        }
        return contentType;
    }

    private void headWriter(SocketChannel socketChannel, Map<String, String> headerMap) throws IOException {
        String lineSeparator = System.getProperty("line.separator");
        streamWriter(socketChannel, "HTTP/1.1 " + headerMap.get("status") + lineSeparator);
        streamWriter(socketChannel, "Server : higore" + lineSeparator);
        streamWriter(socketChannel, "Content-Type : " + headerMap.get("contentType") + lineSeparator);
        streamWriter(socketChannel, "Content-Length : " + headerMap.get("contentLenth") + lineSeparator);
        streamWriter(socketChannel, lineSeparator);
    }

    /**
     * 클라이언트로 보낼 정보를 쓴다.
     * 
     * @param socketChannel
     * @param headerMap
     * @throws IOException
     */
    private void fileWriter(SocketChannel socketChannel, Map<String, String> headerMap) throws IOException {
        if (STATUS_200.equals(headerMap.get("status"))) {
            Path path = Paths.get(headerMap.get("filePath"));
            BufferedReader buffer = Files.newBufferedReader(path);
            String line = null;
            headWriter(socketChannel, headerMap);
            do {
                line = buffer.readLine();
                if (line != null) {
                    streamWriter(socketChannel, line);
                } else {
                    break;
                }
            } while (true);
        } else if(STATUS_404.equals(headerMap.get("status"))){
            
            
            headWriter(socketChannel, headerMap);
            socketChannel.write(ByteBuffer.wrap("없슈".getBytes()));
        }
    }

    private void streamWriter(SocketChannel socketChannel, String line) throws IOException {
        socketChannel.write(ByteBuffer.wrap(line.getBytes()));
    }

    /**
     * 서버 소켓을 닫는다.
     */
    public void close() {
        try {
            selector.close();
            serverSocketChannel.close();
            this.runFlag = false;
        } catch (IOException e) {
            logger.debug(e.getMessage());
        }
    }
}
