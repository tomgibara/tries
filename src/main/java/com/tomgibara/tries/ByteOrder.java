package com.tomgibara.tries;

import java.util.Comparator;
import java.util.Objects;

public final class ByteOrder {

	private static final int UNS = 0;
	private static final int SGN = 1;
	private static final int RUN = 2;
	private static final int RSN = 3;
	private static final int CMP = 4;
	
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
		comparator = null;
	}

	private ByteOrder(Comparator<Byte> comparator) {
		fixedType = CMP;
		this.comparator = comparator;
	}
	
	public int compare(byte a, byte b) {
		switch (fixedType) {
		case UNS: return Integer.compare(a & 0xff, b & 0xff);
		case SGN: return Byte.compare(a, b);
		case RUN: return Integer.compare(b & 0xff, a & 0xff);
		case RSN: return Byte.compare(b, a);
		case CMP: return comparator.compare(a, b);
		default : throw new IllegalStateException("Unexpected type: " + fixedType);
		}
	}
	
	@Override
	public String toString() {
		switch (fixedType) {
		case UNS: return "UNSIGNED";
		case SGN: return "SIGNED";
		case RUN: return "REVERSE_UNSIGNED";
		case RSN: return "REVERSE_SIGNED";
		case CMP: return comparator.toString();
		default : throw new IllegalStateException("Unexpected type: " + fixedType);
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
