package com.exiledradio.backportcore.mechanics;

import net.minecraft.entity.Entity;

/**
 * A backport's baby that 26.1's age lock (the golden dandelion, Game Drops Backport) can hold: one that is not an
 * {@link net.minecraft.entity.EntityAgeable} (Wild Update Backport's tadpole, which 26.3 locks as {@code Tadpole}
 * does), or an {@code EntityAgeable} whose babies start at another age than 1.12.2's -24000 (Trails &amp; Tales
 * Backport's snifflet, 26.3's {@code Sniffer.getBabyStartAge} -48000).
 *
 * <p>The mod that has the lock (today Game Drops Backport) does everything else: a golden dandelion used on a baby
 * toggles the lock and puts its age back to {@link #startAge()}; while locked, the age stays where it is (set one
 * lower before each {@code onLivingUpdate}, which adds one; an owner whose age counts down holds it the same way,
 * since the lock sets the age it read). The lock is the boolean {@link #LOCKED} in the mob's Forge data
 * ({@link Entity#getEntityData()}), saved with the mob and sent to the clients that see it, so an owner only reads
 * it: {@link #isLocked(Entity)}. An owner that feeds its babies to grow them refuses the food while locked, as 26.3
 * does, and keeps the flag where it keeps the age (a tadpole's bucket).
 *
 * <p>An {@code EntityAgeable} that implements this has its age read and set through these methods, so they must
 * agree with {@code getGrowingAge} and {@code setGrowingAge}. Since Core 1.4.4.
 */
public interface AgeLockable {

    /** The lock: a boolean in the mob's Forge data ({@code ForgeData.AgeLocked}), 26.3's {@code AgeLocked}. */
    String LOCKED = "AgeLocked";

    /** @return true while it is a baby the lock can hold (a tadpole always is) */
    boolean isBaby();

    /** @return its age, in its own terms (a tadpole counts up from 0 to 24000, an {@code EntityAgeable} up to 0) */
    int getAge();

    /** Sets its age, in the same terms; reaching adulthood is the owner's (a tadpole becomes a frog). */
    void setAge(int age);

    /** @return the age a baby starts at, and is put back to when the lock is used ({@code getBabyStartAge}) */
    int startAge();

    /** @return true while the mob's age is locked (either side: the lock is sent to the clients that see it) */
    static boolean isLocked(Entity entity) {
        return entity.getEntityData().getBoolean(LOCKED);
    }
}
