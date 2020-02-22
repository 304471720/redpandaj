package im.redpanda.core;

import im.redpanda.crypt.Base58;
import im.redpanda.crypt.Sha256Hash;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.security.*;

public class PeerInHandshake {

    public static final int IVbytelen = 12;
    public static final String ALGORITHM = "AES/CTR/NoPadding";


    String ip;
    int port = 0;
    int status = 0;
    KademliaId identity;
    NodeId nodeId;
    Peer peer;
    SocketChannel socketChannel;
    SelectionKey key;
    byte[] randomFromUs;
    byte[] randomFromThem;

    boolean weSendOurRandom = false;
    boolean awaitingEncryption = false;
    boolean encryptionActive = false;

    //Secret key and iv used for AES encryption
    SecretKey sharedSecretSend;
    SecretKey sharedSecretReceive;
    IvParameterSpec ivSend;
    IvParameterSpec ivReceive;
    Cipher cipherSend;
    Cipher cipherReceive;


    public PeerInHandshake(String ip, SocketChannel socketChannel) {
        this.ip = ip;
        this.socketChannel = socketChannel;
    }

    public PeerInHandshake(String ip, Peer peer, SocketChannel socketChannel) {
        this.ip = ip;
        this.peer = peer;
        this.socketChannel = socketChannel;
    }

    /**
     * 0 default value, before any handshake was parsed.
     * <p></p>
     * 1 first handshake was parsed, here we are waiting to obtain more information of the peer like the public key
     * to finish the complete handshake.
     * 2 do not connect, connected to ourselves or blacklisted
     * -1 handshake finished from our site, we do not expect more data before switching to encryption.
     * We are waiting for the switching byte to start the encryption.
     *
     * @param status
     */
    public void setStatus(int status) {
        this.status = status;
    }

    /**
     * For the status information see the setter method.
     *
     * @return
     */
    public int getStatus() {
        return status;
    }

    public PeerInHandshake(String ip) {
        this.ip = ip;
    }


    public void addConnection(boolean alreadyConnected) {
        try {
            socketChannel.configureBlocking(false);

            SelectionKey key = null;
            ConnectionHandler.selectorLock.lock();
            try {
                ConnectionHandler.selector.wakeup();
//                if (connectionPending) {
//                    peer.isConnecting = true;
//                    peer.setConnected(false);
//                key = socketChannel.register(ConnectionHandler.selector, SelectionKey.OP_CONNECT);
//                key = socketChannel.register(ConnectionHandler.selector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ);
//                } else {
//                    peer.isConnecting = false;
//                    peer.setConnected(true);
//                    key = socketChannel.register(ConnectionHandler.selector, SelectionKey.OP_READ);
//                }

                if (alreadyConnected) {
                    key = socketChannel.register(ConnectionHandler.selector, SelectionKey.OP_READ);
                } else {
                    key = socketChannel.register(ConnectionHandler.selector, SelectionKey.OP_CONNECT);
                }
            } finally {
                ConnectionHandler.selectorLock.unlock();
            }


            key.attach(this);
            this.key = key;

//            peer.setSelectionKey(key);
            ConnectionHandler.selector.wakeup();
            Log.putStd("added con");
        } catch (IOException ex) {
            ex.printStackTrace();
            peer.disconnect("could not init connection....");
            return;
        }
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public KademliaId getIdentity() {
        return identity;
    }

    public void setIdentity(KademliaId nonce) {
        this.identity = nonce;
    }

    public Peer getPeer() {
        return peer;
    }

    public void setPeer(Peer peer) {
        this.peer = peer;
    }

    public void setKey(SelectionKey key) {
        this.key = key;
    }

    public SelectionKey getKey() {
        return key;
    }

    public SocketChannel getSocketChannel() {
        return socketChannel;
    }

    public NodeId getNodeId() {
        return nodeId;
    }

    public void setNodeId(NodeId nodeId) {
        this.nodeId = nodeId;
    }

    public byte[] getRandomFromUs() {

        if (randomFromUs == null) {
            byte[] randomBytesForEncryption = new byte[PeerInHandshake.IVbytelen / 2];
            Server.secureRandom.nextBytes(randomBytesForEncryption);
            randomFromUs = randomBytesForEncryption;
        }

        return randomFromUs;
    }


    public byte[] getRandomFromThem() {
        return randomFromThem;
    }

    public void setRandomFromThem(byte[] randomFromThem) {
        this.randomFromThem = randomFromThem;
    }

    public void calculateSharedSecret() {

        try {
            KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH", "BC");
            keyAgreement.init(Server.nodeId.getKeyPair().getPrivate());
            keyAgreement.doPhase(nodeId.getKeyPair().getPublic(), true);

            SecretKey intermediateSharedSecret = keyAgreement.generateSecret("AES");

            byte[] encoded = intermediateSharedSecret.getEncoded();


            ByteBuffer bytesForPrivateAESkeySend = ByteBuffer.allocate(32 + PeerInHandshake.IVbytelen);
            ByteBuffer bytesForPrivateAESkeyReceive = ByteBuffer.allocate(32 + PeerInHandshake.IVbytelen);

            bytesForPrivateAESkeySend.put(encoded);
            bytesForPrivateAESkeyReceive.put(encoded);


            bytesForPrivateAESkeySend.put(randomFromUs);
            bytesForPrivateAESkeySend.put(randomFromThem);

            bytesForPrivateAESkeyReceive.put(randomFromThem);
            bytesForPrivateAESkeyReceive.put(randomFromUs);


            if (bytesForPrivateAESkeySend.remaining() != 0) {
                throw new RuntimeException("here is something wrong with the random bytes length!");
            }


            Sha256Hash sha256HashSend = Sha256Hash.create(bytesForPrivateAESkeySend.array());
            Sha256Hash sha256HashReceive = Sha256Hash.create(bytesForPrivateAESkeyReceive.array());

            sharedSecretSend = new SecretKeySpec(sha256HashSend.getBytes(), "AES");
            sharedSecretReceive = new SecretKeySpec(sha256HashReceive.getBytes(), "AES");

            System.out.println("asf " + Base58.encode(sharedSecretSend.getEncoded()) + " " + Base58.encode(sharedSecretReceive.getEncoded()));


            ByteBuffer bytesForIVsend = ByteBuffer.allocate(IVbytelen);
            ByteBuffer bytesForIVreceive = ByteBuffer.allocate(IVbytelen);

            bytesForIVsend.put(randomFromUs);
            bytesForIVsend.put(randomFromThem);
            bytesForIVreceive.put(randomFromThem);
            bytesForIVreceive.put(randomFromUs);

            //todo: iv are just the way around for send/receive, is this a security risk?
            ivSend = new IvParameterSpec(bytesForIVsend.array());
//            System.out.println("send iv: " + Base58.encode(bytesForIVsend.array()));
            ivReceive = new IvParameterSpec(bytesForIVreceive.array());
//            System.out.println("rec iv: " + Base58.encode(bytesForIVreceive.array()));

            //todo we have to change this here for the real crypto algo

//            ivSend = new IvParameterSpec(randomFromUs);
//            System.out.println("send iv: " + Base58.encode(randomFromUs));
//            ivReceive = new IvParameterSpec(randomFromThem);
//            System.out.println("rec iv: " + Base58.encode(randomFromThem));

        } catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchProviderException e) {
            e.printStackTrace();
        }

    }

    public boolean peerIsHigher() {
//        return isConnectionInitializedByMe;
        for (int i = 0; i < KademliaId.ID_LENGTH / 8; i++) {
            int compare = Byte.compare(Server.NONCE.getBytes()[i], nodeId.getKademliaId().getBytes()[i]);
            if (compare > 0) {
                return true;
            } else if (compare < 0) {
                return false;
            }
        }
        System.out.println("could not compare!!!");
        return false;
    }

    public boolean isWeSendOurRandom() {
        return weSendOurRandom;
    }

    public void setWeSendOurRandom(boolean weSendOurRandom) {
        this.weSendOurRandom = weSendOurRandom;
    }

    public boolean isAwaitingEncryption() {
        return awaitingEncryption;
    }

    public void setAwaitingEncryption(boolean awaitingEncryption) {
        this.awaitingEncryption = awaitingEncryption;
    }

    public boolean hasPublicKey() {
        if (getPeer().getNodeId() == null) {
            return false;
        }
        return getPeer().getNodeId().getKeyPair() != null;
    }

    public boolean isEncryptionActive() {
        return encryptionActive;
    }

    public void activateEncryption() {
        encryptionActive = true;
        try {

            /**
             * todo we have to use a encryption authentication algorithm for the stream
             * but currently bouncycastle AES/GCM only support block cipher!
             * maybe we should go for the chacha20-poly, but how to start a new round?
             */

            //let set up the send Cipher
            cipherSend = Cipher.getInstance(ALGORITHM);
            cipherSend.init(Cipher.ENCRYPT_MODE, sharedSecretSend, ivSend);


            //let set up the receive Cipher
            cipherReceive = Cipher.getInstance(ALGORITHM);
            cipherReceive.init(Cipher.DECRYPT_MODE, sharedSecretReceive, ivReceive);


        } catch (NoSuchAlgorithmException
                | NoSuchPaddingException | InvalidKeyException
                | InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }
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

    public Cipher getCipherSend() {
        return cipherSend;
    }

    public Cipher getCipherReceive() {
        return cipherReceive;
    }
}
