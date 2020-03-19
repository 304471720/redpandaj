package im.redpanda.kademlia;


import im.redpanda.core.KademliaId;
import im.redpanda.core.NodeId;
import im.redpanda.crypt.Sha256Hash;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.interfaces.ECKey;

public class KadContent {

//    public static final int PUBKEY_LEN = 33;
//    public static final int SIGNATURE_LEN = 64;

    private KademliaId id; //we store the ID duplicated because of performance reasons (new lookup in the hashmap costs more than a bit of memory)
    private long timestamp; //created at (or updated)
    private byte[] pubkey;
    private byte[] content;
    private byte[] signature;


    public KadContent(KademliaId id, long timestamp, byte[] pubkey, byte[] content) {
        this.id = id;
        this.timestamp = timestamp;
        this.pubkey = pubkey;
        this.content = content;
    }

    public KadContent(KademliaId id, long timestamp, byte[] pubkey, byte[] content, byte[] signature) {
        this.id = id;
        this.timestamp = timestamp;
        this.pubkey = pubkey;
        this.content = content;
        this.signature = signature;
    }

    public KadContent(KademliaId id, byte[] pubkey, byte[] content) {
        this.id = id;
        this.timestamp = System.currentTimeMillis();
        this.pubkey = pubkey;
        this.content = content;
    }


    public byte[] getPubkey() {
        return pubkey;
    }

    public KademliaId getId() {
        return id;
    }

    public void setId(KademliaId id) {
        this.id = id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public byte[] getSignature() {
        if (signature == null) {
            throw new RuntimeException("this content was not signed, signature is null!");
        }
        return signature;
    }

    public Sha256Hash createHash() {

        ByteBuffer buffer = ByteBuffer.allocate(8 + content.length);
        buffer.putLong(timestamp);
        buffer.put(content);

        Sha256Hash hash = Sha256Hash.create(buffer.array());
        return hash;
    }


    public void signWith(NodeId nodeId) {
        Sha256Hash hash = createHash();

        signature = nodeId.sign(hash.getBytes());
    }

    public boolean verify() {
        Sha256Hash hash = createHash();

        NodeId pubNodId = NodeId.importPublic(pubkey);

        return pubNodId.verify(hash.getBytes(), getSignature());
    }
}
