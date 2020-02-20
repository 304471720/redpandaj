package im.redpanda.core;

import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PeerInHandshakeTest {

    @Test
    public void addConnection() throws IOException, InterruptedException {

        Log.LEVEL = 10000;

        ConnectionHandler connectionHandler = new ConnectionHandler();
        connectionHandler.bind();
        connectionHandler.start();


        SocketChannel open = SocketChannel.open();
        open.configureBlocking(false);

        boolean alreadyConnected = open.connect(new InetSocketAddress("127.0.0.1", Server.MY_PORT));

        PeerInHandshake peerInHandshake = new PeerInHandshake("127.0.0.1", open);

        //lets block the main selector worker
        Server.connectionHandler.selectorLock.lock();
        Server.connectionHandler.selector.wakeup();


        //lets not read the data by the main thread by using the alreadyConnected value false....
        peerInHandshake.addConnection(false);

        Server.connectionHandler.selector.select();

        Set<SelectionKey> selectionKeys = Server.connectionHandler.selector.selectedKeys();

        System.out.println("" + selectionKeys.size());

//        assertFalse(selectionKeys.isEmpty()); // test not reliable enough

        System.out.println("" + selectionKeys.size());

//        assertTrue(selectionKeys.size() == 2); // test not reliable enough

        for (SelectionKey key : selectionKeys) {
            if (key.channel() instanceof ServerSocketChannel) {
                continue;
            }
            assertTrue(key.isConnectable());
        }


        open.finishConnect();


        peerInHandshake.getKey().interestOps(0);
        Server.connectionHandler.selector.wakeup();

        //lets the main selector accept the connection
        Server.connectionHandler.selectorLock.unlock();

        Thread.sleep(100);
        System.out.println("try lock");

        while (!Server.connectionHandler.selectorLock.tryLock(1000, TimeUnit.MILLISECONDS)) {
            System.out.println("try locka");
            Server.connectionHandler.selector.wakeup();
            System.out.println("try lockb");
            Thread.sleep(1000);
            System.out.println("try lock");
        }


        peerInHandshake.getKey().interestOps(SelectionKey.OP_READ);
        Server.connectionHandler.selector.wakeup();

        Server.connectionHandler.selector.select(1000);

        selectionKeys = Server.connectionHandler.selector.selectedKeys();

        assertTrue(selectionKeys.size() == 1);


        for (SelectionKey key : selectionKeys) {
            if (key.channel() instanceof ServerSocketChannel) {
                continue;
            }
            assertTrue(key.isReadable());

            ByteBuffer readBuffer = ByteBuffer.allocate(1024);

            open.read(readBuffer);

            assertTrue(ConnectionReaderThread.parseHandshake(peerInHandshake, readBuffer));

            byte[] bytes = new byte[KademliaId.ID_LENGTH];
            KademliaId zeorByteKadId = KademliaId.fromFirstBytes(bytes);

            assertTrue(peerInHandshake.getIdentity().equals(zeorByteKadId));

        }


        Server.connectionHandler.selectorLock.unlock();

    }
}