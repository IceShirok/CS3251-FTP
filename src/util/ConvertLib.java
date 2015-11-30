package util;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Library of functions used in the MagicFTP/DTP code base.
 * Used mainly for converting between data types.
 * 
 * @author Susanna Dong
 *
 */
public class ConvertLib {

    public final static Charset ENCODING = StandardCharsets.UTF_8;

    /**
     * Converts a string to a 32-long MD5 hashed string.
     * http://rosettacode.org/wiki/MD5#Java
     * 
     * @param msg the message to be hashed.
     * @return a 32-long String that is MD5 hashed.
     * @throws NoSuchAlgorithmException
     * @throws UnsupportedEncodingException
     */
    public static String hashString(String msg)
            throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(msg.getBytes(ENCODING));
        return new String(md.digest(), ENCODING);
    }

    public static String convertBytesToString(byte[] bytes, int length)
            throws UnsupportedEncodingException {
        return new String(bytes, 0, length, ENCODING);
    }

    public static byte[] convertStringToBytes(String message)
            throws UnsupportedEncodingException {
        return message.getBytes(ENCODING);
    }

    public static byte[] convertShortToBytes(short s) {
        return new byte[] { (byte) (s >> 8), (byte) (s) };
    }

    public static short convertBytesToShort(byte a, byte b) {
        return (short) ((a << 8 & 0xFF00) | (b & 0x00FF));
    }

    public static byte[] convertIntToBytes(int i) {
        return new byte[] { (byte) (i >> 24), (byte) (i >> 16),
                (byte) (i >> 8), (byte) (i) };
    }

    public static int convertBytesToInt(byte a, byte b, byte c, byte d) {
        return (int) (a << 24 & 0xFF000000 | b << 16 & 0x00FF0000 | c << 8
                & 0x0000FF00 | d & 0x000000FF);
    }

    public static boolean isValidShort(int fakeShort) {
        return (fakeShort > 0 && fakeShort < 32000);
    }
}
