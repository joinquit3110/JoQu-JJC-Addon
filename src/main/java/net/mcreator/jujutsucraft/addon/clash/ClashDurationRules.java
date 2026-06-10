package net.mcreator.jujutsucraft.addon.clash;

import net.mcreator.jujutsucraft.addon.AddonGameRules;
import net.mcreator.jujutsucraft.addon.util.DomainForm;
import net.minecraft.world.level.LevelAccessor;

public final class ClashDurationRules {
    private ClashDurationRules() {
    }

    public static int durationTicks(DomainForm a, DomainForm b) {
        DomainForm left = a == null ? DomainForm.CLOSED : a;
        DomainForm right = b == null ? DomainForm.CLOSED : b;
        if (left == DomainForm.INCOMPLETE && right == DomainForm.INCOMPLETE) {
            return 240;
        }
        if (has(left, right, DomainForm.INCOMPLETE, DomainForm.CLOSED)) {
            return 280;
        }
        if (left == DomainForm.CLOSED && right == DomainForm.CLOSED) {
            return 320;
        }
        if (has(left, right, DomainForm.INCOMPLETE, DomainForm.OPEN)) {
            return 220;
        }
        if (has(left, right, DomainForm.CLOSED, DomainForm.OPEN)) {
            return 300;
        }
        if (left == DomainForm.OPEN && right == DomainForm.OPEN) {
            return 260;
        }
        return 300;
    }

    public static int durationTicks(LevelAccessor world, DomainForm a, DomainForm b) {
        int override = AddonGameRules.nonNegativeInt(world, AddonGameRules.DOMAIN_CLASH_DURATION_TICKS, 0);
        return override > 0 ? override : durationTicks(a, b);
    }

    private static boolean has(DomainForm a, DomainForm b, DomainForm x, DomainForm y) {
        return a == x && b == y || a == y && b == x;
    }
}

