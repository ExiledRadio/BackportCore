"""Expected values for the data-structure self-tests, from 26.3's formulas on a Java LCG written out here."""
M = (1 << 48) - 1


def s64(v):
    v &= (1 << 64) - 1
    return v - (1 << 64) if v >> 63 else v


def s32(v):
    v &= (1 << 32) - 1
    return v - (1 << 32) if v >> 31 else v


class JRandom:
    def __init__(self, seed):
        self.seed = (seed ^ 0x5DEECE66D) & M

    def next(self, bits):
        self.seed = (self.seed * 0x5DEECE66D + 0xB) & M
        return s32(self.seed >> (48 - bits))

    def next_int(self, bound=None):
        if bound is None:
            return self.next(32)
        if (bound & -bound) == bound:
            return s32((bound * (self.next(31) & 0x7FFFFFFF)) >> 31)
        while True:
            bits = self.next(31) & 0x7FFFFFFF
            val = bits % bound
            if bits - val + (bound - 1) < (1 << 31):
                return val

    def next_long(self):
        return s64((self.next(32) << 32) + self.next(32))

    def next_float(self):
        return (self.next(24) & 0xFFFFFF) / float(1 << 24)

    def next_double(self):
        return (((self.next(26) & ((1 << 26) - 1)) << 27) + (self.next(27) & ((1 << 27) - 1))) * (1.0 / (1 << 53))


def with_salt(seed, x, z, salt):
    return JRandom(s64(x * 341873128712 + z * 132897987541 + seed + salt))


def large_feature(seed, x, z):
    r = JRandom(seed)
    a = r.next_long()
    b = r.next_long()
    return JRandom(s64(s64(x * a) ^ s64(z * b) ^ seed))


def candidate(seed, rx, rz, spacing, sep, salt, tri=False):
    r = with_salt(seed, rx, rz, salt)
    lim = spacing - sep
    if tri:
        x = (r.next_int(lim) + r.next_int(lim)) // 2
        z = (r.next_int(lim) + r.next_int(lim)) // 2
    else:
        x = r.next_int(lim)
        z = r.next_int(lim)
    return rx * spacing + x, rz * spacing + z


seed = 12345
print("linear", [candidate(seed, rx, rz, 10, 3, 20261123) for rx, rz in ((0, 0), (-1, 2), (5, -3))])
print("tri", [candidate(seed, rx, rz, 18, 5, 4242, True) for rx, rz in ((0, 0), (2, -1))])
x, z, salt = 7, -3, 4242
print("default float", repr(with_salt(seed, salt, x, z).next_float()))
print("legacy2 float", repr(with_salt(seed, x, z, 10387320).next_float()))
print("legacy3 double", repr(large_feature(seed, x, z).next_double()))
for (cx, cz) in ((7, -3), (130, 64)):
    r = JRandom(s64(s32((cx >> 4) ^ s32((cz >> 4) << 4)) ^ seed))
    r.next_int()
    print("legacy1", cx, cz, r.next_int(5))

r = JRandom(99)
out = []
# uniform 10..20
out.append(r.next_int(20 - 10 + 1) + 10)
# biased_to_bottom 5..40 inner 1
lim = r.next_int(40 - 5 - 1 + 1)
out.append(r.next_int(lim + 1) + 5)


def mth_next_int(r, lo, hi):
    return lo if lo >= hi else r.next_int(hi - lo + 1) + lo


# very_biased_to_bottom 0..30 inner 2
up = mth_next_int(r, 0 + 2, 30)
bup = mth_next_int(r, 0, up - 1)
out.append(mth_next_int(r, 0, bup - 1 + 2))
# trapezoid 0..40 plateau 10
rng = 40
ps = (rng - 10) // 2
pe = rng - ps
out.append(0 + (r.next_int(pe + 1)) + (r.next_int(ps + 1)))
# weighted_list: uniform(0..5) w1, constant 100 w3 (three samples)
for _ in range(3):
    sel = r.next_int(4)
    out.append(r.next_int(6) if sel < 1 else 100)
print("heights", out)
