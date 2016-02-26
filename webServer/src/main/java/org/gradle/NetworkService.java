package org.gradle;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkService extends Thread {
    private Selector selector = null;
    private ServerSocketChannel serverSocketChannel;
    private boolean runFlag = true;

    /**
     * 소켓 생성
     *
     * @throws IOException
     * @throws InterruptedException
     */
    public NetworkService() throws IOException, InterruptedException {
        serverSocketChannel = ServerSocketChannel.open();
        selector = Selector.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        serverSocketChannel.socket().bind(new InetSocketAddress(1234));
    }

    /**
     * 소켓 동작
     */
    @Override
    public void run() {
        while (runFlag) {
            try {
                if (selector.select(1000) != 0) {
                    Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                    while (iterator.hasNext()) {
                        SelectionKey selectionKey = iterator.next();
                        if (selectionKey.isAcceptable()) {
                            accept(selectionKey);
                        } else if (selectionKey.isReadable()) {
                            read(selectionKey);
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
        sc.write(ByteBuffer.wrap("allow".getBytes()));
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
        ByteBuffer buffer = ByteBuffer.allocateDirect(100);
        try {
            // 전달 받은 내용을 버퍼에 입력한다.
            int read = socketChannel.read(buffer);
            isEos = (read == -1);
            if (isEos) {
                // 여기서 문자열 생성
                byte[] resultByteArray = (byte[]) selectionKey.attachment();

                String planStr = new String(resultByteArray);

                System.out.println(planStr);
                selectionKey.cancel();
                socketChannel.close();
                this.close();
            } else {
                buffer.clear();
                byte[] bytearr = new byte[buffer.remaining()];
                int totalSize = rawData.length + bytearr.length;
                byte[] totalArr = new byte[totalSize];
                buffer.get(bytearr);

                System.arraycopy(rawData, 0, totalArr, 0, rawData.length);
                System.arraycopy(bytearr, 0, totalArr, rawData.length, bytearr.length);
                selectionKey.attach(totalArr);
            }
        } catch (Exception e) {
            Logger logger = LoggerFactory.getLogger(this.getClass());
            logger.debug(e.getMessage());
            try {
                selectionKey.cancel();
                socketChannel.close();
            } catch (IOException e1) {
                logger.debug(e1.getMessage());
            }
        } finally {
            if (buffer != null) {
                buffer.clear();
                buffer = null;
            }
        }
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
            Logger logger = LoggerFactory.getLogger(this.getClass());
            logger.debug(e.getMessage());
        }
    }

}
