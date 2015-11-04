package com.tomgibara.tries;

import java.util.Arrays;
import java.util.Comparator;

import com.tomgibara.storage.Stores;

public final class ByteOrder {

	// constants
	
	private static final int CMP = 0;
	private static final int UNS = 1;
	private static final int SGN = 2;
	private static final int RUN = 3;
	private static final int RSN = 4;

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
	
	// public statics

	public static final ByteOrder UNSIGNED = new ByteOrder(UNS);

	public static final ByteOrder SIGNED = new ByteOrder(SGN);

	public static final ByteOrder REVERSE_UNSIGNED = new ByteOrder(RUN);

	public static final ByteOrder REVERSE_SIGNED = new ByteOrder(RSN);
	
	public static ByteOrder from(Comparator<Byte> comparator) {
		if (comparator == null) throw new IllegalArgumentException("null comparator");
		ByteOrder order = new ByteOrder(comparator);
		//TODO consider canonicalizing fixed orders
		return order;
	}
	
	// fields
	
	private final int fixedType;
	private final Comparator<Byte> comparator;
	private final int hashCode;
	private int[] lookup = null;

	// constructors
	
	private ByteOrder(int fixedType) {
		this.fixedType = fixedType;
		Comparator<Byte> c;
		int h;
		// note hashes precomputed
		switch (fixedType) {
		case UNS: c = ByteOrder::unsCmp; h = 0x037ce081; break;
		case SGN: c = ByteOrder::sgnCmp; h = 0x3c831f7f; break;
		case RUN: c = ByteOrder::runCmp; h = 0x7a53307f; break;
		case RSN: c = ByteOrder::rsnCmp; h = 0x3a53307f; break;
		default: throw new IllegalArgumentException();
		}
		comparator = c;
		hashCode = h;
	}

	private ByteOrder(Comparator<Byte> comparator) {
		fixedType = CMP;
		this.comparator = comparator;
		// force computation of lookup to check consistency
		lookup();
		// cache hashCode
		hashCode = Math.abs( Arrays.hashCode(lookup()) );
	}
	
	// methods
	
	public Comparator<Byte> asComparator() {
		return comparator;
	}
	
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
		// why do the work unnecessarily on startup
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
				byte[] bytes = new byte[256];
				for (int i = 0; i < 256; i++) {
					bytes[i] = (byte) (i - 128);
				}
				// sort them
				Stores.bytes(bytes).asList().sort(comparator);
				// create a reverse lookup
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
	
	@Override
	public int hashCode() {
		return hashCode;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (!(obj instanceof ByteOrder)) return false;
		ByteOrder that = (ByteOrder) obj;
		// check hash code first because it's quick
		if (this.hashCode != that.hashCode) return false;
		// don't rely on comparator equality - that cannot be relied upon
		// use lookup equality instead
		if (!Arrays.equals(this.lookup(), that.lookup())) return false;
		return true;
	}
}
