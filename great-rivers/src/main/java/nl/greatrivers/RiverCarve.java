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
		double top = field.ceiling(pos.blockX(), pos.blockZ());
		if (top == Double.POSITIVE_INFINITY) return base;
		double carve = (top - pos.blockY()) / 6.0;
		return Math.min(base, carve);
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
