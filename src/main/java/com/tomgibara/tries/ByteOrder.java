/*
 * Copyright 2015 Tom Gibara
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.tomgibara.tries;

import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.SortedSet;

import com.tomgibara.bits.BitStore;
import com.tomgibara.bits.Bits;

/**
 * <p>
 * Efficiently compares byte values using a fixed ordering.
 *
 * <p>
 * Note that this class is serializable so that applications seeking to persist
 * tries may do so together with the byte ordering applied. Serialization works
 * even if the byte order was constructed using a comparator that is not
 * serializable.
 *
 * @author Tom Gibara
 */

public final class ByteOrder implements Serializable {

	// constants

	private static final long serialVersionUID = 7031876057420610423L;

	private static final int CMP = 0;
	private static final int UNS = 1;
	private static final int SGN = 2;
	private static final int RUN = 3;
	private static final int RSN = 4;

	private static final int HASH_UNS = 0x037ce081;
	private static final int HASH_SGN = 0x3c831f7f;
	private static final int HASH_RUN = 0x7a53307f;
	private static final int HASH_RSN = 0x3a53307f;

	// private statics

	private static int unsCmp(byte a, byte b) {
		return Integer.compare(a & 0xff, b & 0xff);
	}

	private static int sgnCmp(byte a, byte b) {
		return Byte.compare(a, b);
	}

	private static int runCmp(byte a, byte b) {
		return Integer.compare(b & 0xff, a & 0xff);
	}

	private static int rsnCmp(byte a, byte b) {
		return Byte.compare(b, a);
	}

	private static boolean isSameOrder(ByteOrder a, ByteOrder b) {
		return Arrays.equals(a.lookup(), b.lookup());
	}

	// public statics

	/**
	 * Unsigned byte order. Comparisons between <code>a</code> and
	 * <code>b</code> are equivalent to:
	 * <code>Integer.compare(a &amp; 0xff, b &amp; 0xff)</code>.
	 */

	public static final ByteOrder UNSIGNED = new ByteOrder(UNS);

	/**
	 * Signed byte order. Comparisons between <code>a</code> and
	 * <code>b</code> are equivalent to:
	 * <code>Byte.compare(a, b)</code>.
	 */

	public static final ByteOrder SIGNED = new ByteOrder(SGN);

	/**
	 * Reverse unsigned byte order. Comparisons between <code>a</code> and
	 * <code>b</code> are equivalent to:
	 * <code>Integer.compare(b &amp; 0xff, a &amp; 0xff)</code>.
	 */

	public static final ByteOrder REVERSE_UNSIGNED = new ByteOrder(RUN);

	/**
	 * Reverse signed byte order. Comparisons between <code>a</code> and
	 * <code>b</code> are equivalent to:
	 * <code>Byte.compare(b, a)</code>.
	 */

	public static final ByteOrder REVERSE_SIGNED = new ByteOrder(RSN);

	/**
	 * Derives a byte order from the ordering imposed by a comparator.
	 * The supplied comparator must provide a consistent ordering.
	 *
	 * @param comparator a consistent byte comparator
	 * @return a byte order equivalent to the supplied comparator
	 * @throws IllegalArgumentException if the comparator ordering is
	 * inconsistent
	 */

	public static ByteOrder from(Comparator<Byte> comparator) {
		if (comparator == null) throw new IllegalArgumentException("null comparator");
		ByteOrder order = new ByteOrder(comparator);
		ByteOrder fixed = fixedOrder(order.hashCode);
		return fixed != null && isSameOrder(order, fixed) ? fixed : order;
	}

	private static int fixedHashCode(int fixedType) {
		// note hashes precomputed
		switch (fixedType) {
		case UNS: return HASH_UNS;
		case SGN: return HASH_SGN;
		case RUN: return HASH_RUN;
		case RSN: return HASH_RSN;
		default: throw new IllegalArgumentException();
		}
	}

	private static Comparator<Byte> fixedComparator(int fixedType) {
		switch (fixedType) {
		case UNS: return ByteOrder::unsCmp;
		case SGN: return ByteOrder::sgnCmp;
		case RUN: return ByteOrder::runCmp;
		case RSN: return ByteOrder::rsnCmp;
		default: throw new IllegalArgumentException();
		}
	}

	private static ByteOrder fixedOrder(int hashCode) {
		switch (hashCode) {
		case HASH_UNS: return UNSIGNED;
		case HASH_SGN: return SIGNED;
		case HASH_RUN: return REVERSE_UNSIGNED;
		case HASH_RSN: return REVERSE_SIGNED;
		default: return null;
		}
	}

	// fields

	private final int fixedType;
	private final Comparator<Byte> comparator;
	private final int hashCode;
	private int[] lookup = null;

	// constructors

	private ByteOrder(int fixedType) {
		this.fixedType = fixedType;
		hashCode = fixedHashCode(fixedType);
		comparator = fixedComparator(fixedType);
	}

	private ByteOrder(Comparator<Byte> comparator) {
		fixedType = CMP;
		this.comparator = comparator;
		// force computation of lookup to check consistency
		lookup();
		// cache hashCode
		hashCode = Math.abs( Arrays.hashCode(lookup()) );
	}

	private ByteOrder(int[] lookup) {
		fixedType = CMP;
		this.lookup = lookup;
		// validates the lookup
		comparator = new LookupComparator(lookup);
		hashCode = Math.abs( Arrays.hashCode(lookup) );
	}
	// methods

	public Comparator<Byte> asComparator() {
		return comparator;
	}

	/**
	 * Compares two bytes. As per Java conventions, the method returns a
	 * negative integer if <code>a &lt; b</code>, zero if <code>a == b</code>
	 * and a positive integer if <code>a &gt; b</code>
	 *
	 * @param a
	 *            any byte
	 * @param b
	 *            another byte
	 * @return <code>a</code> compared to <code>b</code>
	 */

	public int compare(byte a, byte b) {
		switch (fixedType) {
		case UNS: return unsCmp(a, b);
		case SGN: return sgnCmp(a, b);
		case RUN: return runCmp(a, b);
		case RSN: return rsnCmp(a, b);
		default: return comparator.compare(a, b);
		}
	}

	// private methods

	private int[] lookup() {
		// lookup is lazy for fixed types
		// use of custom comparators is rare
		// why do the work unnecessarily on startup?
		if (lookup == null) {
			int[] lookup = new int[256];
			// optimize fixed types
			switch (fixedType) {
			case UNS:
				for (int i = 0; i < 256; i++) {
					lookup[i] = i;
				}
				break;
			case SGN:
				for (int i = 0; i < 256; i++) {
					lookup[(i - 128) & 0xff] = i;
				}
				break;
			case RUN:
				for (int i = 0; i < 256; i++) {
					lookup[255 - i] = i;
				}
				break;
			case RSN:
				for (int i = 0; i < 256; i++) {
					lookup[(127 - i) & 0xff] = i;
				}
				break;
			default:
				// create bytes -128..127
				ByteList list = new ByteList();
				// sort them
				list.sort(comparator);
				// create a reverse lookup
				byte[] bytes = list.bytes;
				int index = 0;
				byte p = bytes[0];
				lookup[p & 0xff] = index;
				for (int i = 1; i < 256; i++) {
					byte b = bytes[i];
					int c = compare(p, b);
					if (c > 0) throw new IllegalStateException("inconsistent ordering");
					if (c < 0) index ++;
					lookup[b & 0xff] = index;
					p = b;
				}
			}
			this.lookup = lookup;
		}
		return lookup;
	}

	// object methods

	@Override
	public String toString() {
		switch (fixedType) {
		case UNS: return "UNSIGNED";
		case SGN: return "SIGNED";
		case RUN: return "REVERSE_UNSIGNED";
		case RSN: return "REVERSE_SIGNED";
		default : return comparator.toString();
		}
	}

	/**
	 * A hash code consistent with equality on this class.
	 */

	@Override
	public int hashCode() {
		return hashCode;
	}

	/**
	 * Two {@link ByteOrder} instances are equal if they impose an identical
	 * ordering on bytes.
	 */

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (!(obj instanceof ByteOrder)) return false;
		ByteOrder that = (ByteOrder) obj;
		// check hash code first because it's quick
		if (this.hashCode != that.hashCode) return false;
		// don't rely on comparator equality - that cannot be relied upon
		// use lookup equality instead
		if (!isSameOrder(this, that)) return false;
		return true;
	}

	// serialization

	private Object writeReplace() {
		return fixedType == CMP ? new LookupSerial(lookup) : new FixedSerial(fixedType);
	}

	private static class FixedSerial implements Serializable {

		private static final long serialVersionUID = -4929447042921599241L;

		private final int fixedType;

		FixedSerial(int fixedType) {
			this.fixedType = fixedType;
		}

		private Object readResolve() throws StreamCorruptedException {
			switch (fixedType) {
			case UNS: return UNSIGNED;
			case SGN: return SIGNED;
			case RUN: return REVERSE_UNSIGNED;
			case RSN: return REVERSE_SIGNED;
			default:  throw new StreamCorruptedException("invalid fixedType value: " + fixedType);
			}
		}

	}

	private static class LookupSerial implements Serializable {

		private static final long serialVersionUID = 652788669095291423L;

		private final int[] lookup;

		LookupSerial(int[] lookup) {
			this.lookup = lookup;
		}

		private Object readResolve() {
			return new ByteOrder(lookup);
		}

	}

	private static class LookupComparator implements Comparator<Byte> {

		private final int[] lookup;

		LookupComparator(int[] lookup) {
			if (lookup.length != 256) throw new IllegalArgumentException();
			BitStore bits = Bits.store(256);
			SortedSet<Integer> set = bits.ones().asSet();
			for (int index : lookup) set.add(index);
			if (!bits.rangeTo(set.size()).ones().isAll()) throw new IllegalArgumentException();

			this.lookup = lookup;
		}

		@Override
		public int compare(Byte a, Byte b) {
			return lookup[a & 0xff] - lookup[b & 0xff];
		}
	}

	private static final class ByteList extends AbstractList<Byte> {

		final byte[] bytes = new byte[256];

		ByteList() {
			for (int i = 0; i < 256; i++) {
				bytes[i] = (byte) (i - 128);
			}
		}

		@Override public int size() { return 256; }
		@Override public Byte get(int index) { return bytes[index]; }
		@Override public Byte set(int index, Byte value) {
			byte previous = bytes[index];
			bytes[index] = value;
			return previous;
		}
	}

}
