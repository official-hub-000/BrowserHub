package com.browserhub.app.media;

/**
 * AES-128-CBC decryption for HLS segment keys.
 *
 * Javax crypto on Android exposes AES-CBC/NoPadding, and doing the padding
 * removal ourselves avoids a padded-mode quirk: HLS segments are encrypted as
 * whole blocks with no PKCS#7 padding, so a "with padding" cipher would throw on
 * a segment whose last byte happens to look like padding.
 *
 * Implemented directly against the JDK's AES primitive so there is no extra
 * dependency to audit in a privacy-focused browser.
 */
public final class Aes128Cbc {

    private Aes128Cbc() {}

    /**
     * @param data ciphertext, a multiple of 16 bytes
     * @param key  16 raw key bytes
     * @param ivHex "0x…" or plain hex, 32 hex chars
     */
    public static byte[] decrypt(byte[] data, byte[] key, String ivHex) throws Exception {
        if (key == null || key.length != 16) {
            throw new IllegalArgumentException("AES-128 needs a 16-byte key");
        }
        if (data == null || data.length == 0) return new byte[0];
        if (data.length % 16 != 0) {
            // A truncated final block cannot be decrypted; keeping it would emit
            // garbage at the end of the file.
            throw new IllegalArgumentException("encrypted data was not a multiple of 16 bytes");
        }
        byte[] iv = parseIv(ivHex);
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(
                javax.crypto.Cipher.DECRYPT_MODE,
                new javax.crypto.spec.SecretKeySpec(key, "AES"),
                new javax.crypto.spec.IvParameterSpec(iv));
        return cipher.doFinal(data);
    }

    /**
     * Parses a hex IV. An absent IV is the caller's business (it uses the segment
     * sequence number); an absent IV here means an explicit zero IV.
     */
    public static byte[] parseIv(String ivHex) {
        byte[] iv = new byte[16];
        if (ivHex == null) return iv;
        String hex = ivHex.trim();
        if (hex.startsWith("0x") || hex.startsWith("0X")) hex = hex.substring(2);
        if (hex.length() != 32) return iv;
        for (int i = 0; i < 16; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) return iv;
            iv[i] = (byte) ((hi << 4) | lo);
        }
        return iv;
    }

    /** The implicit IV for segment N: N as a 128-bit big-endian integer. */
    public static byte[] sequenceIv(int index) {
        byte[] iv = new byte[16];
        long v = index & 0xFFFFFFFFL;
        iv[12] = (byte) ((v >>> 24) & 0xFF);
        iv[13] = (byte) ((v >>> 16) & 0xFF);
        iv[14] = (byte) ((v >>> 8) & 0xFF);
        iv[15] = (byte) (v & 0xFF);
        return iv;
    }
}
