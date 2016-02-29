package org.gradle;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sun.nio.cs.UnicodeEncoder;

public class NetworkService extends Thread {
    private int bufferSize;
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private Selector selector = null;
    private ServerSocketChannel serverSocketChannel;
    private boolean runFlag = true;

    /**
     * 소켓 생성
     *
     * @param bufferSize
     *
     * @throws IOException
     * @throws InterruptedException
     */
    public NetworkService(int port, int bufferSize) throws IOException, InterruptedException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        serverSocketChannel.socket().bind(new InetSocketAddress(port));
        this.bufferSize = bufferSize;
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
                write(socketChannel, analysis(resultByteArray));
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
    }

    private void write(SocketChannel socketChannel, Map<String, Object> analysis) throws IOException {
        if (analysis == null) {
            logger.debug("아직 정의되지 않음");
        } else {
            Path path = (Path) analysis.get("contents");
            BufferedReader buffer = Files.newBufferedReader(path);
            String line = null;
            do {
                line = buffer.readLine();
                if (line != null) {
                    socketChannel.write(ByteBuffer.wrap(line.getBytes()));
                } else {
                    break;
                }
            } while (true);
        }
        socketChannel.close();
    }

    private Map<String, Object> analysis(byte[] resultByteArray) throws IOException {
        Map<String, Object> resultMap = new HashMap<String, Object>();
        HttpRequestParser parser = new HttpRequestParser();
        parser.parseRequest(new String(resultByteArray));
        if (parser.getRequestLine().indexOf("GET") > -1) {
            String getInfo = parser.getRequestLine().replaceAll("GET\\s\\/([^\\s]+)\\sHTTP/1.1", "$1");
            String[] split = getInfo.split("\\?");
            if ("close".equals(split[0])) {
                close();
            } else {
                Path path = Paths.get(split[0] + ".html");
                if (Files.exists(path)) {
                    resultMap.put("contents", path);
                } else {
                    return null;
                }
            }
        }
        return resultMap;
    }

    /**
     * 소켓을 닫는다.
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
