package nl.greatrivers;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.dynamic.CodecHolder;
import net.minecraft.world.gen.densityfunction.DensityFunction;

/**
 * Wraps the overworld terrain density: above the river ceiling there is no terrain (river bed, banks, valley).
 * Everything below stays exactly as vanilla generates it, so caves, ores and deepslate are untouched.
 */
public final class RiverCarve implements DensityFunction {
	private final DensityFunction input;
	private final RiverField field;

	public RiverCarve(DensityFunction input, RiverField field) {
		this.input = input;
		this.field = field;
	}

	@Override
	public double sample(NoisePos pos) {
		double base = input.sample(pos);
		int x = pos.blockX(), z = pos.blockZ(), y = pos.blockY();
		double top = field.ceiling(x, z);
		double v = top == Double.POSITIVE_INFINITY ? base : Math.min(base, (top - y) / 6.0);
		double p = field.polderWeight(x, z);
		if (p > 0.001 && y >= 44) {
			// Dutch landscape: level polders, dikes along the rivers, canals and ditches
			double t = field.dutchHeight(x, z);
			v = v * (1 - p) + ((t - y) / 6.0) * p;
		}
		double w = field.flatWeight(x, z);
		if (w > 0) {
			// harbour quay: exactly level at the quay height, sloping back into the land around it
			double flat = (Harbours.QUAY_Y + 0.5 - y) / 6.0;
			if (y < Harbours.QUAY_Y - 14) flat = base;   // deep below the quay: caves and ores as normal
			v = v * (1 - w) + flat * w;
		}
		return v;
	}

	@Override
	public void fill(double[] densities, EachApplier applier) {
		applier.fill(densities, this);
	}

	@Override
	public DensityFunction apply(DensityFunctionVisitor visitor) {
		return visitor.apply(new RiverCarve(input.apply(visitor), field));
	}

	@Override
	public double minValue() {
		return Math.min(input.minValue(), -700.0);
	}

	@Override
	public double maxValue() {
		return input.maxValue();
	}

	@Override
	public CodecHolder<? extends DensityFunction> getCodecHolder() {
		return CodecHolder.of(MapCodec.unit(this));
	}
}
