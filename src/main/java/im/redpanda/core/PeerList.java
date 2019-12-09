package im.redpanda.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * This "static" class stores all peers in two Hashmap for fast get operations using the {@link KademliaId} and Ip+Port.
 * For the connections we establish, we need a sorted List with regard to specific parameters.
 * This class maintains an ArrayList with the same peers as in the Hashmap.
 * In addition, a peer can be optionally be stored in the DHT routing table, called the Buckets.
 * Note that not all nodes will be in the routing table (Buckets).
 * <p>
 * <b>Warning</b>: Do not change any of the required data ({@link KademliaId}, Ip, Port) of a {@link Peer} if it is present in the Peerlist.
 */
public class PeerList {

    /**
     * We store each Peer in a hashmap for fast get operations via KademliaId
     */
    private static HashMap<KademliaId, Peer> peerlist;

    /**
     * We store each Peer in a hashmap for fast get operations via Ip and Port
     */
    private static HashMap<Integer, Peer> peerlistIpPort;

    /**
     * Blacklist of ips via HashMap
     */
    private static HashMap<Integer, Peer> blacklistIp;

    /**
     * We store each Peer in a ArrayList to obtain a sorted list of Peers where the good peers are on top
     */
    private static ArrayList<Peer> peerArrayList;

    /**
     * ReadWriteLock for peerlist peerArrayList and Buckets
     */
    private static ReentrantReadWriteLock readWriteLock;

    /**
     * Buckets for the Kademlia routing
     */
    private static ArrayList<Peer>[] buckets;
    private static ArrayList<Peer>[] bucketsReplacement;

    static {
        peerlist = new HashMap<>();
        peerlistIpPort = new HashMap<>();
        blacklistIp = new HashMap<>();
        peerArrayList = new ArrayList<>();
        readWriteLock = new ReentrantReadWriteLock();
        buckets = new ArrayList[KademliaId.ID_LENGTH];
        bucketsReplacement = new ArrayList[KademliaId.ID_LENGTH];
    }

    /**
     * Adds a Peer to the Peerlist by handling all Hashmaps and the Arraylist.
     * Acquires locks.
     *
     * @param peer The peer to add to the PeerList.
     * @return old peer, null if no old peer or old peer null.
     */
    public static Peer add(Peer peer) {
        if (peer.getKademliaId() == null) {
            throw new RuntimeException("Its not allowed to insert a peer in the PeerList without a KademliaId!");
        }

        Peer oldPeer = null;
        try {
            readWriteLock.writeLock().lock();
            oldPeer = peerlist.put(peer.getKademliaId(), peer);
            peerlistIpPort.put(getIpPortHash(peer), peer);
            peerArrayList.add(peer);
        } finally {
            readWriteLock.writeLock().unlock();
        }
        return oldPeer;
    }

    /**
     * Hash method for the peerlistIpPort list.
     *
     * @param peer
     * @return hash value
     */
    private static Integer getIpPortHash(Peer peer) {
        //ToDo: we need later a better method
        return peer.getIp().hashCode() + peer.getPort();
    }

    /**
     * Removes a {@link Peer} from the PeerList.
     * Removes the Peer from both Hashmaps and the ArrayList
     *
     * @param peer
     */
    public static void remove(Peer peer) {
        remove(peer.getKademliaId());
    }

    /**
     * Removes a {@link Peer} by providing a {@link KademliaId} from the PeerList.
     * Removes the Peer from both Hashmaps and the ArrayList
     *
     * @param id
     */
    public static void remove(KademliaId id) {
        try {
            readWriteLock.writeLock().lock();
            Peer remove = peerlist.remove(id);
            peerArrayList.remove(remove);
            peerlistIpPort.remove(getIpPortHash(remove));
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    public static Peer get(KademliaId id) {
        try {
            readWriteLock.readLock().lock();
            return peerlist.get(id);
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    public static ReentrantReadWriteLock getReadWriteLock() {
        return readWriteLock;
    }

    public static ArrayList<Peer> getPeerArrayList() {
        return peerArrayList;
    }


    public static int size() {
        readWriteLock.readLock().lock();
        try {
            return peerArrayList.size();
        } finally {
            readWriteLock.readLock().unlock();
        }
    }


}
