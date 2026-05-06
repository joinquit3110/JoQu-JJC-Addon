package net.mcreator.jujutsucraft.addon.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

public final class DomainWallTraceData {
    private DomainWallTraceData() {
    }

    public static final class Cell {
        public final BlockPos placePos;
        public final BlockPos supportPos;
        public final int lateral;
        public final int radial;

        public Cell(BlockPos placePos, BlockPos supportPos, int lateral, int radial) {
            this.placePos = placePos;
            this.supportPos = supportPos;
            this.lateral = lateral;
            this.radial = radial;
        }
    }

    public static final class Result {
        public final List<Cell> cells;
        public final Map<Integer, BlockPos> lowestSupportByLateral;
        public final Map<Integer, BlockPos> highestSupportByLateral;
        public final int candidateCount;
        public final int seedCount;

        public Result(List<Cell> cells, Map<Integer, BlockPos> lowestSupportByLateral, Map<Integer, BlockPos> highestSupportByLateral, int candidateCount, int seedCount) {
            this.cells = cells;
            this.lowestSupportByLateral = lowestSupportByLateral;
            this.highestSupportByLateral = highestSupportByLateral;
            this.candidateCount = candidateCount;
            this.seedCount = seedCount;
        }

        public static Result empty() {
            return new Result(new ArrayList<>(), new HashMap<>(), new HashMap<>(), 0, 0);
        }
    }
}
