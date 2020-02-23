package im.redpanda.core;

import java.io.*;
import java.util.HashMap;
import java.util.Random;
import java.util.Set;

/**
 * @author Robin Braun
 */
public class LocalSettings implements Serializable {


    public NodeId myIdentity;
    public String myIp;
    public byte[] updateSignature;
    public byte[] updateAndroidSignature;

    public LocalSettings() {
        myIdentity = new NodeId();
        myIp = "";
    }

    public void setUpdateSignature(byte[] updateSignature) {
        this.updateSignature = updateSignature;
    }

    public byte[] getUpdateSignature() {
        return updateSignature;
    }

    public byte[] getUpdateAndroidSignature() {
        return updateAndroidSignature;
    }

    public void setUpdateAndroidSignature(byte[] updateAndroidSignature) {
        this.updateAndroidSignature = updateAndroidSignature;
    }

    public void save(int port) {

        FileOutputStream fileOutputStream = null;
        ObjectOutputStream objectOutputStream = null;

        try {

            File mkdirs = new File(Settings.SAVE_DIR);
            mkdirs.mkdir();

            File file = new File(Settings.SAVE_DIR + "/localSettings" + port + ".dat");

            file.createNewFile();
            fileOutputStream = new FileOutputStream(file);
            objectOutputStream = new ObjectOutputStream(fileOutputStream);
            objectOutputStream.writeObject(this);
            objectOutputStream.close();
            fileOutputStream.close();

        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            try {
                if (objectOutputStream != null) {
                    objectOutputStream.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            } finally {
                if (fileOutputStream != null) {
                    try {
                        fileOutputStream.close();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }

    }


    public static LocalSettings load(int port) {
        try {
            File file = new File(Settings.SAVE_DIR + "/localSettings" + port + ".dat");

            FileInputStream fileInputStream = new FileInputStream(file);
            ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
            Object readObject = objectInputStream.readObject();
            objectInputStream.close();
            fileInputStream.close();

            return (LocalSettings) readObject;


        } catch (ClassNotFoundException ex) {
        } catch (IOException ex) {
        }

        System.out.println("could not load localSettings.dat, generating new LocalSettings");

        LocalSettings localSettings = new LocalSettings();
        localSettings.save(port);
        return localSettings;
    }
}
