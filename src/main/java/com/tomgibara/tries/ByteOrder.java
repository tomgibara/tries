package com.tomgibara.tries;

import java.util.Comparator;
import java.util.Objects;

public final class ByteOrder {

	private static final int CMP = 0;
	private static final int UNS = 1;
	private static final int SGN = 2;
	private static final int RUN = 3;
	private static final int RSN = 4;
	
	
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

	public static final ByteOrder UNSIGNED = new ByteOrder(UNS);

	public static final ByteOrder SIGNED = new ByteOrder(SGN);

	public static final ByteOrder REVERSE_UNSIGNED = new ByteOrder(RUN);

	public static final ByteOrder REVERSE_SIGNED = new ByteOrder(RSN);
	
	public static ByteOrder from(Comparator<Byte> comparator) {
		if (comparator == null) throw new IllegalArgumentException("null comparator");
		return new ByteOrder(comparator);
	}
	
	private final int fixedType;
	private final Comparator<Byte> comparator;

	private ByteOrder(int fixedType) {
		this.fixedType = fixedType;
		Comparator<Byte> c;
		switch (fixedType) {
		case UNS: c = ByteOrder::unsCmp; break;
		case SGN: c = ByteOrder::sgnCmp; break;
		case RUN: c = ByteOrder::runCmp; break;
		case RSN: c = ByteOrder::rsnCmp; break;
		default: throw new IllegalArgumentException();
		}
		comparator = c;
	}

	private ByteOrder(Comparator<Byte> comparator) {
		fixedType = CMP;
		this.comparator = comparator;
	}
	
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
		return fixedType ^ Objects.hashCode(comparator);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (!(obj instanceof ByteOrder)) return false;
		ByteOrder that = (ByteOrder) obj;
		// cannot be fixed since we don't have referential equality
		return this.comparator.equals(that.comparator);
	}
}
