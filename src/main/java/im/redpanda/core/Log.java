/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package im.redpanda.core;

import java.util.Arrays;
import java.util.List;

/**
 * @author robin
 */
public class Log {

    public static int LEVEL = 10;

    static {
//        System.out.println("is testing: " + isJUnitTest());
        if (isJUnitTest()) {
            LEVEL = 3000;
//            LEVEL = 0;
        }
    }

    public static void put(String msg, int level) {
        if (level > LEVEL) {
            return;
        }
        System.out.println("Log: " + msg);
    }

    public static void putStd(String msg) {
        if (20 > LEVEL) {
            return;
        }
        System.out.println("Log: " + msg);
    }

    public static void putCritical(Throwable e) {
        if (-200 > LEVEL) {
            return;
        }
        e.printStackTrace();
    }

    public static boolean isJUnitTest() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        List<StackTraceElement> list = Arrays.asList(stackTrace);
        for (StackTraceElement element : list) {
            if (element.getClassName().startsWith("org.junit.")) {
                return true;
            }
        }
        return false;
    }
}
