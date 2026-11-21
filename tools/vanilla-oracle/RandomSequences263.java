// The oracle for CommandTests' random sequences: 26.3's RandomSequence, run from the 26.3 client jar with 1.21.11's
// libraries on the class path (Java 25). Output: src/selftest/resources/assets/backportcore_selftest/oracle/random_sequences_263.txt
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomSequence;
import java.util.Optional;

public class RandomSequences263 {
    public static void main(String[] a) {
        long[] seeds = {0L, 12345L, -7046029254386353131L};
        for (long seed : seeds) {
            for (String id : new String[] {"minecraft:test", "backportcore:dice"}) {
                for (int salt : new int[] {0, 7}) {
                    for (int incId = 0; incId < 2; incId++) {
                        long s = seed ^ (long) salt;
                        RandomSource r = new RandomSequence(s, incId == 1 ? Optional.of(Identifier.parse(id)) : Optional.<Identifier>empty()).random();
                        StringBuilder sb = new StringBuilder();
                        sb.append(seed).append(' ').append(id).append(' ').append(salt).append(' ').append(incId == 1).append(':');
                        for (int i = 0; i < 6; i++) sb.append(' ').append(r.nextInt(100) + 1);
                        sb.append(' ').append(r.nextInt(2147483646));
                        System.out.println(sb);
                    }
                }
            }
        }
    }
}
