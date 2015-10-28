package com.tomgibara.tries;

import java.util.Comparator;

import com.tomgibara.fundament.Producer;

public interface TrieSerialization<E> {

	boolean isSerializable(Object obj);
	
	byte[] buffer();

	void push(byte b);
	
	void replace(byte b);
	
	void pop();
	
	int length();
	
	void length(int newLength);
	
	void reset();

	TrieSerialization<E> resetCopy();

	boolean startsWith(byte[] prefix);
	
	void set(byte[] prefix);
	
	void set(E e);

	E get();
	
	default Comparator<E> comparator(ByteOrder byteOrder) {
		if (byteOrder == null) throw new IllegalArgumentException("null byteOrder");
		TrieSerialization<E> as = resetCopy();
		TrieSerialization<E> bs = resetCopy();
		return (a,b) -> {
			as.set(a);
			bs.set(b);
			int al = as.length();
			int bl = bs.length();
			int limit = Math.min(al, bl);
			byte[] ab = as.buffer();
			byte[] bb = bs.buffer();
			for (int i = 0; i < limit; i++) {
				int c = byteOrder.compare(ab[i], bb[i]);
				if (c != 0) return c;
			}
			return al - bl;
		};
	}

	default Producer<TrieSerialization<E>> producer() {
		return () -> resetCopy();
	}

	default Tries<E> tries() {
		return new Tries<E>(producer());
	}
}