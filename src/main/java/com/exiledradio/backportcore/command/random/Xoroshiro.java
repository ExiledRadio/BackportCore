package com.exiledradio.backportcore.command.random;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 1.18's {@code XoroshiroRandomSource} (Xoroshiro128++) and the {@code RandomSupport} seeding 26.3's random
 * sequences use, bit for bit. (Caves &amp; Cliffs Backport's terrain has its own copy.)
 */
public final class Xoroshiro {

    static final long GOLDEN_RATIO_64 = -7046029254386353131L;
    static final long SILVER_RATIO_64 = 7640891576956012809L;

    long lo;
    long hi;

    public Xoroshiro(long lo, long hi) {
        this.lo = lo;
        this.hi = hi;
        if ((lo | hi) == 0L) {
            this.lo = GOLDEN_RATIO_64;
            this.hi = SILVER_RATIO_64;
        }
    }

    static long mixStafford13(long v) {
        v = (v ^ v >>> 30) * -4658895280553007687L;
        v = (v ^ v >>> 27) * -7723592293110705685L;
        return v ^ v >>> 31;
    }

    /**
     * 26.3's {@code RandomSequence.createSequence}: {@code upgradeSeedTo128bitUnmixed(seed)}, xor the MD5 of the
     * sequence's id when it is included ({@code seedFromHashOf}), then {@code mixed()}.
     */
    public static Xoroshiro forSequence(long seed, String idOrNull) {
        long lo = seed ^ SILVER_RATIO_64;
        long hi = lo + GOLDEN_RATIO_64;
        if (idOrNull != null) {
            byte[] b;
            try {
                b = MessageDigest.getInstance("MD5").digest(idOrNull.getBytes(StandardCharsets.UTF_8));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
            lo ^= bytes(b, 0);
            hi ^= bytes(b, 8);
        }
        return new Xoroshiro(mixStafford13(lo), mixStafford13(hi));
    }

    private static long bytes(byte[] b, int from) {
        long v = 0L;
        for (int i = 0; i < 8; i++) {
            v = v << 8 | (b[from + i] & 0xFFL);
        }
        return v;
    }

    public long nextLong() {
        long i = lo;
        long j = hi;
        long k = Long.rotateLeft(i + j, 17) + i;
        j ^= i;
        lo = Long.rotateLeft(i, 49) ^ j ^ j << 21;
        hi = Long.rotateLeft(j, 28);
        return k;
    }

    public int nextInt() {
        return (int) nextLong();
    }

    public int nextInt(int bound) {
        long i = nextInt() & 0xFFFFFFFFL;
        long j = i * (long) bound;
        long k = j & 4294967295L;
        if (k < (long) bound) {
            for (int l = Integer.remainderUnsigned(~bound + 1, bound); k < (long) l; k = j & 4294967295L) {
                i = nextInt() & 0xFFFFFFFFL;
                j = i * (long) bound;
            }
        }
        return (int) (j >> 32);
    }
}
