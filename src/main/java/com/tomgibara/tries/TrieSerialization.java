package com.tomgibara.tries;

import java.util.Comparator;

import com.tomgibara.fundament.Producer;

public interface TrieSerialization<E> {

	boolean isSerializable(Object obj);
	
	// for efficiency purposes, implementations are not required to make defensive copy
	// the buffer is treated strictly read-only by the trie implementations
	// the length of the buffer may exceed value reported by length()
	byte[] buffer();

	// push a byte into the buffer, growing as necessary
	// increases length by 1
	void push(byte b);
	
	// reduces length by 1
	// illegal state exception if length is zero
	void pop();

	// push a byte into the buffer, growing as necessary
	// leaves length unchanged
	// illegal state exception if length is zero
	default void replace(byte b) {
		pop();
		push(b);
	}

	// the number of bytes stored in the buffer
	int length();

	// reset the length to zero
	// this should not shrink the buffer capacity
	default void reset() {
		for (int i = length(); i > 0; i --) pop();
	}

	// create a copy of the serialization, with its length reset to zero
	TrieSerialization<E> resetCopy();

	// true iff the buffer starts with the supplied prefix
	default boolean startsWith(byte[] prefix) {
		if (prefix.length > length()) return false;
		byte[] buffer = buffer();
		for (int i = 0; i < prefix.length; i++) {
			if (buffer[i] != prefix[i]) return false;
		}
		return true;
	}
	
	// sets the initial bytes of the buffer equal to the prefix
	// sets the length prefix.length
	//TODO some ambiguity here - currently impossible to be called with a prefix longer than the buffer
	// but this may change if we allow byte-based prefixing
	default void set(byte[] prefix) {
		reset();
		for (byte value : prefix) push(value);
	}
	
	// serializes the object into the buffer, growing the buffer as necessary
	// and recording the number of bytes written into length.
	// may throw an IllegalArgumentException for invalid e
	void set(E e);

	// deserializes the buffered bytes into an object
	E get();
	
	// returns a comparator consistent with the natural byte ordering indicated
	// some implementations may be able to return more efficient comparators for certain byte orders
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

	// a producer that can create more instances of this type of serialization
	// the default implementation uses resetCopy()
	default Producer<TrieSerialization<E>> producer() {
		return () -> resetCopy();
	}

	// create a tries using this serialization
	default Tries<E> tries() {
		return new Tries<E>(producer());
	}
}