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

import org.apache.commons.math3.distribution.NormalDistribution;

public final class QNorm {

	private QNorm() {}

	public static double compute(final double p) {
		if (p < 0 || p > 1) {
			return Double.NaN;
		}
		if (p == 0 || p == 1) {
			return Double.POSITIVE_INFINITY;
		}

		return compute(p, 0, 1, true, false);
	}

	public static double compute(double p, final double mean, final double sd,
		final boolean lowerTail, final boolean logP)
	{
		final NormalDistribution dist = new NormalDistribution(mean, sd);
		if (logP) p = Math.exp(p);
		final double q = dist.inverseCumulativeProbability(p);
		return lowerTail ? q : -q;
	}
}
