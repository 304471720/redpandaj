/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package im.redpanda.core;

import im.redpanda.crypt.Utils;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;
import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * @author rflohr
 */
public class Peer implements Comparable<Peer>, Serializable {

    public String ip;
    public int port;
    public int connectAble = 0;
    public int retries = 0;
    public long lastBufferModified;
    long lastRetryAfter5 = 0;
    public long lastActionOnConnection = 0;
    int cnt = 0;
    public long connectedSince = 0;
    private NodeId nodeId;
    private KademliaId kademliaId;
    private ArrayList<String> filterAdresses;
    private SocketChannel socketChannel;
    //    public ArrayList<ByteBuffer> readBuffers = new ArrayList<ByteBuffer>();
//    public ArrayList<ByteBuffer> writeBuffers = new ArrayList<ByteBuffer>();
    public ByteBuffer readBuffer;
    public ByteBuffer writeBuffer;
    SelectionKey selectionKey;
    public boolean firstCommandsProceeded;
    private boolean connected = false;
    public boolean isConnecting;
    public long lastPinged = 0;
    public double ping = 0;
    public int requestedMsgs = 0;
    byte[] toEncodeForAuthFromMe;
    byte[] toEncodeForAuthFromHim;
    boolean requestedNewAuth;
    public boolean authed = false;
    public ByteBuffer writeBufferCrypted;
    public ByteBuffer readBufferCrypted;
    public int trustRetries = 0;
    public final ReentrantLock writeBufferLock = new ReentrantLock();
    public Thread connectinThread;
    public int parsedCryptedBytes = 0;
    public long syncMessagesSince = 0;
    public ArrayList<Integer> removedSendMessages = new ArrayList<Integer>();
    public int maxSimultaneousRequests = 1;


    public long sendBytes = 0;
    public long receivedBytes = 0;

    public boolean isConnectionInitializedByMe = false;

    private boolean isIntegrated = false;

    //new variables since redpanda2.0
    Cipher cipherSend;
    Cipher cipherReceive;

    public Peer(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public Peer(String ip, int port, NodeId id) {
        this.ip = ip;
        this.port = port;
        this.nodeId = id;
    }

    /**
     * Set the nodeId of this Peer, does not check the consitency with the KademliaId.
     *
     * @param nodeId
     */
    public void setNodeId(NodeId nodeId) {
        this.nodeId = nodeId;
    }

    public NodeId getNodeId() {
        return nodeId;
    }

    //    public void setNodeId(KademliaId nodeId) {
//
//        if (nodeId == null) {
//            return;
//        }
//
//        Test.peerListLock.lock();
//        try {
//            System.out.println("############################################ new node id: " + nodeId + " old: " + this.nodeId);
//
//            if (this.nodeId == null) {
//                //only add
//                //we have to set the new nodeId in advance!
//                this.nodeId = nodeId;
//                Test.addPeerToBucket(this);
//                return;
//            }
//
//            if (this.nodeId.equals(nodeId)) {
//                //maybe a new instance!
//                this.nodeId = nodeId;
//                return;
//            }
//
//            //if we are here the old id is not null and we have a new id/id changed
//            //first remove the old id from bucket
//            Test.removePeerFromBucket(this);
//            System.out.println("removed peer from buckets: new node id: " + nodeId + " old: " + this.nodeId);
//
//
//            //we have to set the new nodeId in advance!
//            this.nodeId = nodeId;
//            Test.addPeerToBucket(this);
//        } finally {
//            Test.peerListLock.unlock();
//        }
//    }
//
//    public void removeNodeId() {
//
//        if (this.nodeId == null) {
//            return;
//        }
//
//        Test.peerListLock.lock();
//        try {
//            Test.removePeerFromBucket(this);
//        } finally {
//            Test.peerListLock.unlock();
//        }
//    }

    public KademliaId getKademliaId() {
        return kademliaId;
    }

    public boolean equalsIpAndPort(Object obj) {

        if (obj instanceof Peer) {

            Peer n2 = (Peer) obj;

            return (ip.equals(n2.ip) && port == n2.port);

        } else {
            return false;
        }

    }

    public boolean equalsNonce(Object obj) {

        if (obj instanceof Peer) {

            Peer n2 = (Peer) obj;

            if (kademliaId == null || n2.kademliaId == null) {
                return false;
            }

            //return (ip.equals(n2.ip) && port == n2.port && nonce == n2.nonce);
            return kademliaId.equals(n2.kademliaId);

        } else {
            return false;
        }

    }


    public boolean equalsInstance(Object obj) {
        return super.equals(obj);
    }

//    @Override
//    public boolean equals(Object obj) {
//        throw new RuntimeException("hjgadzagdzwad");
////        return equalsNonce(obj);
//    }

    public long getLastAnswered() {
        return System.currentTimeMillis() - lastActionOnConnection;
    }


//    public PeerSaveable toSaveable() {
//        return new PeerSaveable(ip, port, lastAllMsgsQuerried, nodeId, retries);
//    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    @Override
    public int compareTo(Peer o) {

        return o.getPriority() - getPriority();

//        int ret = (int) (retries - o.retries);
//
//        if (ret != 0) {
//            return ret;
//        }
//
//
//        int a = (int) (o.lastActionOnConnection - lastActionOnConnection);
//
//        if (a != 0) {
//            return a;
//        }
//
//        return (int) (o.lastAllMsgsQuerried - lastAllMsgsQuerried);
    }

    public int getPriority() {

        int a = 0;

        if (connected) {
            a += 2000;
        }

        if (kademliaId == null) {
            a -= 1000;
        }

        if (ip.contains(":")) {
            a += 50;
        }


        a += -retries * 200;

        return a;
    }

    public boolean iSameInstance(Peer p) {
        return super.equals(p);
    }

    public SocketChannel getSocketChannel() {
        return socketChannel;
    }

    public void setSocketChannel(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
    }


    public void disconnect(String reason) {

        try {
            writeBufferLock.tryLock(2, TimeUnit.SECONDS);

            Log.put("DISCONNECT: " + reason, 100);

            setConnected(false);


            if (isConnecting && connectinThread != null) {
                connectinThread.interrupt();
            }

            isConnecting = false;
            authed = false;

            if (selectionKey != null) {
                selectionKey.cancel();
            }
            if (socketChannel != null) {
//            ByteBuffer a = ByteBuffer.allocate(1);
//            a.put((byte) 254);
//            a.flip();
//            try {
//                int write = socketChannel.write(a);
//                //System.out.println("QUIT bytes: " + write);
//            } catch (IOException ex) {
//            } catch (NotYetConnectedException e) {
//            }

                if (socketChannel.isOpen()) {
                    try {
                        socketChannel.configureBlocking(false);//ToDo: hack
                        socketChannel.close();
                    } catch (IOException ex) {
                    }
                }
            }

            readBuffer = null;
            readBufferCrypted = null;
            writeBuffer = null;
            writeBufferCrypted = null;

            if (writeBufferLock.isHeldByCurrentThread()) {
                writeBufferLock.unlock();
            }


        } catch (InterruptedException ex) {
            Logger.getLogger(Peer.class.getName()).log(Level.SEVERE, null, ex);
        }

        Server.triggerOutboundthread();

    }


    public void ping() {

        if (System.currentTimeMillis() - lastPinged < 5000) {
            return;
        }

        if (getSelectionKey() == null || writeBuffer == null) {
            setConnected(false);
            return;
        }
        if (!getSelectionKey().isValid()) {
            System.out.println("selectionkey invalid11!");
            //disconnect();
            setConnected(false);
            return;
        }

        lastPinged = System.currentTimeMillis();

        if (writeBufferLock.tryLock()) {
            if (writeBuffer.capacity() > 0) {
                writeBuffer.put(Command.PING);
                Log.put("pinged...", 100);
            } else {
                Log.put("didnt ping, buffer has content...", 100);
            }
            writeBufferLock.unlock();
        } else {
            Log.put("Could not lock for ping!", 50);
        }

        setWriteBufferFilled();

    }

    public SelectionKey getSelectionKey() {
        return selectionKey;
    }

    public void setSelectionKey(SelectionKey selectionKey) {
        this.selectionKey = selectionKey;
    }

    public boolean setWriteBufferFilled() {

        if (!isConnected()) {
            return false;
        }

        boolean remainingBytes;

        if (writeBuffer == null) {
            return false;
        }


        if (getSelectionKey().isValid()) {
            Server.connectionHandler.selectorLock.lock();
            try {
                getSelectionKey().selector().wakeup();
                getSelectionKey().interestOps(getSelectionKey().interestOps() | SelectionKey.OP_WRITE);
            } catch (CancelledKeyException e) {
                System.out.println("cancelled key exception");
            } finally {
                Server.connectionHandler.selectorLock.unlock();
            }
        } else {
            System.out.println("key is not valid");
            disconnect("key is not valid");
        }

        return false;
    }

    public int encrypteOutputdata() {

        writeBufferLock.lock();
        try {

            if (writeBuffer == null) {
                return 0;
            }

            writeBuffer.flip();
            int remaining = writeBuffer.remaining();


            if (remaining == 0) {
                writeBuffer.compact();
                return 0;
            }

            byte[] bytesToEncrypt = new byte[remaining];
            writeBuffer.get(bytesToEncrypt);


            byte[] encrypt = encrypt(bytesToEncrypt);

            writeBufferCrypted.put(encrypt);


            writeBuffer.compact();

//            System.out.println("encrypted " + remaining + " bytes...");

            return remaining;
        } finally {
            writeBufferLock.unlock();
        }


    }

    public int decryptInputdata() {

        writeBufferLock.lock();
        try {

            if (readBuffer == null) {
                return 0;
            }

            readBufferCrypted.flip();
            int remaining = readBufferCrypted.remaining();


            if (remaining == 0) {
                readBufferCrypted.compact();
                return 0;
            }

            byte[] bytesToDecrypt = new byte[remaining];
            readBufferCrypted.get(bytesToDecrypt);


            byte[] decrypt = decrypt(bytesToDecrypt);

            readBuffer.put(decrypt);


            readBufferCrypted.compact();

//            System.out.println("decrypted  " + remaining + " bytes...");

            return remaining;
        } finally {
            writeBufferLock.unlock();
        }


    }

    int writeBytesToPeer(ByteBuffer writeBuffer) throws IOException {
        writeBufferCrypted.flip();
        int writtenBytes = getSocketChannel().write(writeBufferCrypted);
        writeBufferCrypted.compact();

        System.out.println("written bytes to node: " + writtenBytes);

        return writtenBytes;
    }


    public boolean peerIsHigher() {
        for (int i = 0; i < KademliaId.ID_LENGTH / 8; i++) {
            int compare = Byte.toUnsignedInt(kademliaId.getBytes()[i]) - Byte.toUnsignedInt(Server.NONCE.getBytes()[i]);
            if (compare > 0) {
                return true;
            } else if (compare < 0) {
                return false;
            }
        }
        System.out.println("could not compare!!!");
        return false;
    }


    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public boolean isAuthed() {
        return authed;
    }

    public boolean isCryptedConnection() {
        return readBufferCrypted != null;
    }

    public ReentrantLock getWriteBufferLock() {
        return writeBufferLock;
    }

    public ByteBuffer getWriteBuffer() {
        return writeBuffer;
    }

    public boolean isIntegrated() {

        if (isIntegrated) {
            return true;
        }

        if (connectedSince != 0 && System.currentTimeMillis() - connectedSince > 1000L * 10L) {
            isIntegrated = true;
        }

        return false;
    }

    /**
     * Sets the KademliaId, the updates for Buckets and HashMap should be directly handled by the PeerList.
     *
     * @param kademliaId
     */
    public void setKademliaId(KademliaId kademliaId) {
        this.kademliaId = kademliaId;


//        if (kademliaId == null) {
//            return;
//        }
//
//        Server.peerListLock.writeLock().lock();
//        try {
//            System.out.println("############################################ new node id: " + kademliaId + " old: " + this.kademliaId);
//
//            if (this.kademliaId == null) {
//                //only add
//                //we have to set the new nodeId in advance!
//                this.kademliaId = kademliaId;
////                Server.addPeerToBucket(this);
//                return;
//            }
//
//            if (this.kademliaId.equals(kademliaId)) {
//                //maybe a new instance!
//                this.kademliaId = kademliaId;
//                return;
//            }
//
//            //if we are here the old id is not null and we have a new id/id changed
//            //first remove the old id from bucket
//            Server.removePeerFromBucket(this);
//            System.out.println("removed peer from buckets: new node id: " + kademliaId + " old: " + this.kademliaId);
//
//
//            //we have to set the new nodeId in advance!
//            this.kademliaId = kademliaId;
//            Server.addPeerToBucket(this);
//        } finally {
//            Server.peerListLock.writeLock().unlock();
//        }
    }

    public PeerSaveable toSaveable() {
        return new PeerSaveable(ip, port, kademliaId, retries);
    }

    public void removeNodeId() {

        if (this.kademliaId == null) {
            return;
        }

        Server.peerListLock.writeLock().lock();
        try {
            Server.removePeerFromBucket(this);
        } finally {
            Server.peerListLock.writeLock().unlock();
        }
    }

    public void setLastActionOnConnection(long lastActionOnConnection) {
        this.lastActionOnConnection = lastActionOnConnection;
    }

    public Cipher getCipherSend() {
        return cipherSend;
    }

    public void setCipherSend(Cipher cipherSend) {
        this.cipherSend = cipherSend;
    }

    public Cipher getCipherReceive() {
        return cipherReceive;
    }

    public void setCipherReceive(Cipher cipherReceive) {
        this.cipherReceive = cipherReceive;
    }

    public byte[] encrypt(byte[] toEncrypt) {

        try {

            byte[] outputEncryptedBytes;

            outputEncryptedBytes = new byte[cipherSend.getOutputSize(toEncrypt.length)];
            int encryptLength = cipherSend.update(toEncrypt, 0,
                    toEncrypt.length, outputEncryptedBytes, 0);
            encryptLength += cipherSend.doFinal(outputEncryptedBytes, encryptLength);


            return outputEncryptedBytes;
        } catch (ShortBufferException
                | IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
            return null;
        }
    }

    public byte[] decrypt(byte[] bytesToDecrypt) {
        try {
            byte[] outPlain;

//            System.out.println("len to decrypt: " + bytesToDecrypt.length);

            outPlain = new byte[cipherReceive.getOutputSize(bytesToDecrypt.length)];
            int decryptLength = cipherReceive.update(bytesToDecrypt, 0,
                    bytesToDecrypt.length, outPlain, 0);
            decryptLength += cipherReceive.doFinal(outPlain, decryptLength);

            return outPlain;
        } catch (IllegalBlockSizeException | BadPaddingException
                | ShortBufferException e) {
            e.printStackTrace();
            return null;
        }
    }


    public void setupConnection(PeerInHandshake peerInHandshake) {


        //disconnect old connection if present
        disconnect("new connection for this peer");


        /**
         * setup the buffers
         */
        writeBufferLock.lock();
        try {
            readBuffer = ByteBuffer.allocate(10 * 1024);
            readBufferCrypted = ByteBuffer.allocate(10 * 1024);
            writeBuffer = ByteBuffer.allocate(10 * 1024);
            writeBufferCrypted = ByteBuffer.allocate(10 * 1024);
        } catch (Throwable e) {
            Log.putStd("Speicher konnte nicht reserviert werden. Disconnect peer...");
            disconnect("Speicher konnte nicht reserviert werden.");
        } finally {
            writeBufferLock.unlock();
        }


        //setup the peer with all data from the peerInHandshake
        setLastActionOnConnection(System.currentTimeMillis());
        setConnected(true);

        setSocketChannel(peerInHandshake.getSocketChannel());
        setSelectionKey(peerInHandshake.getKey());

        setCipherSend(peerInHandshake.getCipherSend());
        setCipherReceive(peerInHandshake.getCipherReceive());


        //update the selection key to the actual peer
        peerInHandshake.getKey().attach(this);

        /**
         * If this is a new connection not initialzed by us this peer might not be in our PeerList, lets addd it by KademliaId
         */
        PeerList.add(this);

//        lets request some peers form that peer
        writeBufferLock.lock();
        try {
            writeBuffer.put(Command.REQUEST_PEERLIST);
            setWriteBufferFilled();
        } finally {
            writeBufferLock.unlock();
        }

    }

    @Override
    public String toString() {
        return "Peer{" +
                "ip='" + ip + '\'' +
                ", port=" + port +
                '}';
    }
}
