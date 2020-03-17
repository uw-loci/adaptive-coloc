/*-
 * #%L
 * Adaptive colocalization algorithms.
 * %%
 * Copyright (C) 2018 - 2020 Board of Regents of the University of
 * Wisconsin-Madison.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package loci.adaptivecoloc;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.loops.LoopBuilder.FourConsumer;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

/**
 * Adapted from Shulei's original Java code for AdaptiveSmoothedKendallTau from
 * his RKColocal R package.
 * (https://github.com/lakerwsl/RKColocal/blob/master/RKColocal_0.0.1.0000.tar.gz)
 *
 * @author Shulei Wang
 * @author Curtis Rueden
 * @author Ellen TA Dobson
 */
public final class AdaptiveSmoothedKendallTau {

	private AdaptiveSmoothedKendallTau() {}

	public static <I extends RealType<I>, O extends RealType<O>> void execute(
		final RandomAccessibleInterval<I> image1,
		final RandomAccessibleInterval<I> image2, final I thres1, final I thres2,
		final RandomAccessibleInterval<O> result, final long seed,
		final boolean multiThreaded)
	{
		execute(image1, image2, thres1, thres2, new DoubleType(), result, seed,
			multiThreaded);
	}

	public static <I extends RealType<I>, T extends RealType<T>, O extends RealType<O>> void execute(
		final RandomAccessibleInterval<I> image1,
		final RandomAccessibleInterval<I> image2, final I thres1, final I thres2,
		final T intermediate, final RandomAccessibleInterval<O> result,
		final long seed, final boolean multiThreaded)
	{
		final Function<RandomAccessibleInterval<I>, RandomAccessibleInterval<T>> factory =
			img -> Util.getSuitableImgFactory(img, intermediate).create(img);
		execute(image1, image2, thres1, thres2, factory, result, seed,
			multiThreaded);
	}

	public static <I extends RealType<I>, T extends RealType<T>, O extends RealType<O>>
		void execute(final RandomAccessibleInterval<I> image1,
			final RandomAccessibleInterval<I> image2, final I thres1, final I thres2,
			Function<RandomAccessibleInterval<I>, RandomAccessibleInterval<T>> factory,
			final RandomAccessibleInterval<O> result, final long seed,
			final boolean multiThreaded)
	{
		final long nr = image1.dimension(1);
		final long nc = image1.dimension(0);
		final RandomAccessibleInterval<T> oldtau = factory.apply(image1);
		final RandomAccessibleInterval<T> newtau = factory.apply(image1);
		final RandomAccessibleInterval<T> oldsqrtN = factory.apply(image1);
		final RandomAccessibleInterval<T> newsqrtN = factory.apply(image1);
		final List<RandomAccessibleInterval<T>> stop = new ArrayList<>();
		for (int s = 0; s < 3; s++)
			stop.add(factory.apply(image1));
		final double Dn = Math.sqrt(Math.log(nr * nc)) * 2;
		final int TU = 15;
		final int TL = 8;
		final double Lambda = Dn;

		LoopBuilder.setImages(oldsqrtN).forEachPixel(t -> t.setOne());

		double size = 1;
		final double stepsize = 1.15; // empirically the best, but could have users
																	// set
		int intSize;
		boolean IsCheck = false;

		final Random rng = new Random(seed);

		for (int s = 0; s < TU; s++) {
			intSize = (int) Math.floor(size);
			singleiteration(image1, image2, thres1, thres2, stop, oldtau, oldsqrtN,
				newtau, newsqrtN, result, Lambda, Dn, intSize, IsCheck, rng);
			size *= stepsize;
			if (s == TL) {
				IsCheck = true;
				LoopBuilder<FourConsumer<T, T, T, T>> looper = //
					LoopBuilder.setImages(stop.get(1), stop.get(2), newtau, newsqrtN);
				if (multiThreaded) looper = looper.multiThreaded();
				looper.forEachPixel((ts1, ts2, tTau, tSqrtN) -> {
					ts1.set(tTau);
					ts2.set(tSqrtN);
				});
			}
		}
	}

	private static <I extends RealType<I>, T extends RealType<T>, O extends RealType<O>>
		void singleiteration(final RandomAccessibleInterval<I> image1,
			final RandomAccessibleInterval<I> image2, final I thres1, final I thres2,
			final List<RandomAccessibleInterval<T>> stop,
			final RandomAccessibleInterval<T> oldtau,
			final RandomAccessibleInterval<T> oldsqrtN,
			final RandomAccessibleInterval<T> newtau,
			final RandomAccessibleInterval<T> newsqrtN,
			final RandomAccessibleInterval<O> result, final double Lambda,
			final double Dn, final int Bsize, final boolean isCheck, final Random rng)
	{
		final double[][] kernel = kernelGenerate(Bsize);

		final long[] rowrange = new long[4];
		final long[] colrange = new long[4];
		final int totnum = (2 * Bsize + 1) * (2 * Bsize + 1);
		final double[] LocX = new double[totnum];
		final double[] LocY = new double[totnum];
		final double[] LocW = new double[totnum];
		final double[][] combinedData = new double[totnum][3];
		final int[] rankedindex = new int[totnum];
		final double[] rankedw = new double[totnum];
		final int[] index1 = new int[totnum];
		final int[] index2 = new int[totnum];
		final double[] w1 = new double[totnum];
		final double[] w2 = new double[totnum];
		final double[] cumw = new double[totnum];
		double tau;
		double taudiff;

		final Cursor<O> cursor = Views.iterable(result).localizingCursor();
		final RandomAccess<T> raOldTau = oldtau.randomAccess();
		final RandomAccess<T> raOldSqrtN = oldsqrtN.randomAccess();
		final RandomAccess<T> raNewTau = newtau.randomAccess();
		final RandomAccess<T> raNewSqrtN = newsqrtN.randomAccess();
		final RandomAccess<T> rastop0 = stop.get(0).randomAccess();
		final RandomAccess<T> rastop1 = stop.get(1).randomAccess();
		final RandomAccess<T> rastop2 = stop.get(2).randomAccess();

		final RandomAccess<I> gdImage1 = image1.randomAccess();
		final RandomAccess<I> gdImage2 = image2.randomAccess();
		final RandomAccess<T> gdTau = oldtau.randomAccess();
		final RandomAccess<T> gdSqrtN = oldsqrtN.randomAccess();

		final long nr = result.dimension(1);
		final long nc = result.dimension(0);
		while (cursor.hasNext()) {
			cursor.next();
			raOldTau.setPosition(cursor);
			raOldSqrtN.setPosition(cursor);
			raNewTau.setPosition(cursor);
			raNewSqrtN.setPosition(cursor);
			rastop0.setPosition(cursor);
			rastop1.setPosition(cursor);
			rastop2.setPosition(cursor);

			final long row = cursor.getLongPosition(1);
			updateRange(row, Bsize, nr, rowrange);
			if (isCheck) {
				if (rastop0.get().getRealDouble() != 0) {
					continue;
				}
			}
			final long col = cursor.getLongPosition(0);
			updateRange(col, Bsize, nc, colrange);
			getData(Dn, kernel, gdImage1, gdImage2, gdTau, gdSqrtN, LocX, LocY, LocW,
				rowrange, colrange, totnum);
			raNewSqrtN.get().setReal(Math.sqrt(NTau(thres1, thres2, LocW, LocX,
				LocY)));
			if (raNewSqrtN.get().getRealDouble() <= 0) {
				raNewTau.get().setZero();
				cursor.get().setZero();
			}
			else {
				tau = WtKendallTau.calculate(LocX, LocY, LocW, combinedData,
					rankedindex, rankedw, index1, index2, w1, w2, cumw, rng);
				raNewTau.get().setReal(tau);
				cursor.get().setReal(tau * raNewSqrtN.get().getRealDouble() * 1.5);
			}

			if (isCheck) {
				taudiff = Math.abs(rastop1.get().getRealDouble() - raNewTau.get()
					.getRealDouble()) * rastop2.get().getRealDouble();
				if (taudiff > Lambda) {
					rastop0.get().setOne();
					raNewTau.get().set(raOldTau.get());
					raNewSqrtN.get().set(raOldSqrtN.get());
				}
			}
		}

		// TODO: instead of copying pixels here, swap oldTau and newTau every time.
		// :-)
		LoopBuilder.setImages(oldtau, newtau, oldsqrtN, newsqrtN).forEachPixel((
			tOldTau, tNewTau, tOldSqrtN, tNewSqrtN) -> {
			tOldTau.set(tNewTau);
			tOldSqrtN.set(tNewSqrtN);
		});

	}

	private static <I extends RealType<I>, T extends RealType<T>> void getData(
		final double Dn, final double[][] w, final RandomAccess<I> i1RA,
		final RandomAccess<I> i2RA, final RandomAccess<T> tau,
		final RandomAccess<T> sqrtN, final double[] sx, final double[] sy,
		final double[] sw, final long[] rowrange, final long[] colrange,
		final int totnum)
	{
		// TODO: Decide if this cast is OK.
		int kernelk = (int) (rowrange[0] - rowrange[2] + rowrange[3]);
		int kernell;
		int index = 0;
		double taudiffabs;

		sqrtN.setPosition(colrange[2], 0);
		sqrtN.setPosition(rowrange[2], 1);
		final double sqrtNValue = sqrtN.get().getRealDouble();

		for (long k = rowrange[0]; k <= rowrange[1]; k++) {
			i1RA.setPosition(k, 1);
			i2RA.setPosition(k, 1);
			sqrtN.setPosition(k, 1);
			// TODO: Double check cast.
			kernell = (int) (colrange[0] - colrange[2] + colrange[3]);
			for (long l = colrange[0]; l <= colrange[1]; l++) {
				i1RA.setPosition(l, 0);
				i2RA.setPosition(l, 0);
				sqrtN.setPosition(l, 0);
				sx[index] = i1RA.get().getRealDouble();
				sy[index] = i2RA.get().getRealDouble();
				sw[index] = w[kernelk][kernell];

				tau.setPosition(l, 0);
				tau.setPosition(k, 1);
				final double tau1 = tau.get().getRealDouble();

				tau.setPosition(colrange[2], 0);
				tau.setPosition(rowrange[2], 1);
				final double tau2 = tau.get().getRealDouble();

				taudiffabs = Math.abs(tau1 - tau2) * sqrtNValue;
				taudiffabs = taudiffabs / Dn;
				if (taudiffabs < 1) sw[index] = sw[index] * (1 - taudiffabs) * (1 -
					taudiffabs);
				else sw[index] = sw[index] * 0;
				kernell++;
				index++;
			}
			kernelk++;
		}
		while (index < totnum) {
			sx[index] = 0;
			sy[index] = 0;
			sw[index] = 0;
			index++;
		}
	}

	private static void updateRange(final long location, final int radius,
		final long boundary, final long[] range)
	{
		range[0] = location - radius;
		if (range[0] < 0) range[0] = 0;
		range[1] = location + radius;
		if (range[1] >= boundary) range[1] = boundary - 1;
		range[2] = location;
		range[3] = radius;
	}

	private static <I extends RealType<I>> double NTau(final I thres1,
		final I thres2, final double[] w, final double[] x, final double[] y)
	{
		double sumW = 0;
		double sumsqrtW = 0;
		double tempW;

		for (int index = 0; index < w.length; index++) {
			if (x[index] < thres1.getRealDouble() || y[index] < thres2
				.getRealDouble()) w[index] = 0;
			tempW = w[index];
			sumW += tempW;
			tempW = tempW * w[index];
			sumsqrtW += tempW;
		}
		double NW;
		final double Denomi = sumW * sumW;
		if (Denomi <= 0) {
			NW = 0;
		}
		else {
			NW = Denomi / sumsqrtW;
		}
		return NW;
	}

	private static double[][] kernelGenerate(final int size) {
		final int L = size * 2 + 1;
		final double[][] kernel = new double[L][L];
		final int center = size;
		double temp;
		final double Rsize = size * Math.sqrt(2.5);

		for (int i = 0; i <= size; i++) {
			for (int j = 0; j <= size; j++) {
				temp = Math.sqrt(i * i + j * j) / Rsize;
				if (temp >= 1) temp = 0;
				else temp = 1 - temp;
				kernel[center + i][center + j] = temp;
				kernel[center - i][center + j] = temp;
				kernel[center + i][center - j] = temp;
				kernel[center - i][center - j] = temp;
			}
		}
		return kernel;
	}
}
