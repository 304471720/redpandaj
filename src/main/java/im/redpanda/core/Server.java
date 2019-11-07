package im.redpanda.core;

import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Server {

    public static final int VERSION = 21;
    public static final int MY_PORT = 59558;
    static String MAGIC = "k3gV";
    public static KademliaId NONCE;
    public static boolean SHUTDOWN = false;
    public static ArrayList<Peer> peerList = null;
    public static ReentrantReadWriteLock peerListLock = new ReentrantReadWriteLock();
    public static int outBytes = 0;
    public static int inBytes = 0;
    public static ConnectionHandler connectionHandler;
    public static OutboundHandler outboundHandler;
    public static ArrayList<Peer>[] buckets = new ArrayList[KademliaId.ID_LENGTH];
    public static ArrayList<Peer>[] bucketsReplacement = new ArrayList[KademliaId.ID_LENGTH];


    public static void start() {


        peerList = Saver.loadPeers();

        NONCE = new KademliaId();


        connectionHandler = new ConnectionHandler();
        connectionHandler.start();


        outboundHandler = new OutboundHandler();
        outboundHandler.init();

    }


    public static void triggerOutboundthread() {
        if (outboundHandler != null) {
            outboundHandler.tryInterrupt();
        }
    }


    public static void addPeerToBucket(Peer p) {
        peerListLock.writeLock().lock();
        try {
            int distanceToUs = p.getNodeId().getDistanceToUs();
            buckets[distanceToUs - 1].add(p);
        } finally {
            peerListLock.writeLock().unlock();
        }
    }

    public static void removePeerFromBucket(Peer p) {
        peerListLock.writeLock().lock();
        try {
            int distanceToUs = p.getNodeId().getDistanceToUs();
            buckets[distanceToUs - 1].remove(p);
        } finally {
            peerListLock.writeLock().unlock();
        }
    }


    public static Peer findPeer(Peer peer) {

        peerListLock.readLock().lock();
        try {
            for (Peer p : peerList) {

                if (p.equalsIpAndPort(peer)) {
                    return p;
                }

            }

            peerListLock.writeLock().lock();
            peerList.add(peer);
            peerListLock.writeLock().unlock();

        } finally {
            peerListLock.readLock().unlock();
        }
        return peer;
    }

    public static void removePeer(Peer peer) {
        peerListLock.writeLock().lock();
        peerList.remove(peer);
        peer.removeNodeId();
        peerListLock.writeLock().unlock();
    }
}
