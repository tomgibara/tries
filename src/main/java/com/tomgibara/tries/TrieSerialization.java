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

import java.util.Comparator;

import com.tomgibara.fundament.Bijection;
import com.tomgibara.fundament.Producer;

/**
 * Implementations of this interface are responsible for converting objects
 * to-and-from bytes.
 * 
 * @author Tom Gibara
 *
 * @param <E>
 *            the type of object marshalled
 */

public interface TrieSerialization<E> {

	/**
	 * Whether the specified object can be serialized by this object.
	 * 
	 * @param obj an object
	 * @return true if and only if the supplied object can be serialized
	 */
	
	boolean isSerializable(Object obj);
	
	/**
	 * The byte array containing the serialized bytes. For efficiency purposes,
	 * implementations are not required to make defensive copies of the buffer;
	 * the buffer is treated strictly read-only by the trie implementations. The
	 * length of the buffer may exceed the value reported by {@link #length()}.
	 * 
	 * @return the underlying bytes
	 */

	byte[] buffer();

	/**
	 * Pushes a byte onto the end of the buffer. The buffer's length, as
	 * reported by {@link #length()} will increase by one. The buffer's capacity
	 * will grow as necessary.
	 * 
	 * @param b
	 *            the byte value to append to the buffer
	 */

	void push(byte b);

	/**
	 * Pops a byte off the end of the buffer. The buffer's length as reported by
	 * {@link #length()} will decrease by one.
	 * 
	 * @throws IllegalArgumentException
	 *             if called when the length is zero
	 */

	void pop();

	/**
	 * Replaces the last byte value in the buffer. The length of the buffer
	 * remains unchanged.
	 * 
	 * @param b
	 *            the byte value to be set
	 * @throws IllegalArgumentException
	 *             if the length is zero
	 */

	default void replace(byte b) {
		pop();
		push(b);
	}

	/**
	 * The length of the array returned by {@link #buffer()}.
	 * 
	 * @return the capacity in bytes
	 */
	
	default int capacity() {
		return buffer().length;
	}
	
	/**
	 * The number of byte values stored by this serialization. The reported
	 * length will never exceed the capacity.
	 * 
	 * @return the length in bytes
	 */

	int length();

	// reduces length, never grows
	default void trim(int newLength) {
		for (int i = length(); i > newLength; i --) pop();

	}
	
	/**
	 * Sets the length to zero, leaving the buffer capacity unchanged.
	 */

	default void reset() {
		trim(0);
	}

	/**
	 * Creates a new instance of the serialization with the same capacity but
	 * zero length.
	 * 
	 * @return a new serialization instance.
	 */

	default TrieSerialization<E> resetCopy() {
		return resetCopy(capacity());
	}

	/**
	 * Creates a new instance of the serialization with the specified capacity
	 * and zero length.
	 * 
	 * @return a new serialization instance.
	 */

	TrieSerialization<E> resetCopy(int capacity);

	/**
	 * Whether the buffer starts with the supplied prefix.
	 * 
	 * @param prefix
	 *            an array of byte values
	 * @return true iff and only iff the buffer starts with with supplied prefix
	 */
	
	default boolean startsWith(byte[] prefix) {
		if (prefix.length > length()) return false;
		byte[] buffer = buffer();
		for (int i = 0; i < prefix.length; i++) {
			if (buffer[i] != prefix[i]) return false;
		}
		return true;
	}
	
	//TODO currently impossible to be called with a prefix longer than the buffer
	// but this may change if we allow byte-based prefixing
	/**
	 * Sets the initial bytes of the buffer. The length of of the buffer is set
	 * to the length of the prefix. Implementations may assume that the length
	 * of the prefix will not exceed the capacity of the buffer.
	 * 
	 * @param prefix
	 *            an array of byte values
	 */

	default void set(byte[] prefix) {
		reset();
		for (byte value : prefix) push(value);
	}
	
	/**
	 * Serializes an object into the buffer, growing the buffer as necessary and
	 * recording the number of bytes written as the length.
	 * 
	 * @param e
	 *            the object to serialize
	 * @throws IllegalArgumentException
	 *             if the supplied object cannot be serialized by this object
	 */

	void set(E e);

	/**
	 * Deserializes the buffered bytes into an object. Any bytes beyond the
	 * first {@link #length()} bytes are ignored.
	 * 
	 * @return the deserialized object
	 */

	E get();
	
	/**
	 * Returns a comparator consistent with the byte ordering indicated. Some
	 * implementations may be able to return more efficient comparators for
	 * certain byte orders. The comparator is assumed to use the buffer
	 * underlying this serialization, and as such concurrency must be managed
	 * appropriately.
	 * 
	 * @param byteOrder
	 *            a byte order
	 * @return a comparator consistent with the supplied byte order
	 */
	
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

	/**
	 * A producer that can create more instanceof of this type of serialization.
	 * The default implementation uses {@link #resetCopy()}.
	 * 
	 * @return a producer of these serializations.
	 */

	default Producer<TrieSerialization<E>> producer() {
		return () -> resetCopy();
	}

	/**
	 * Creates new {@link #tries()} based on this serialization.
	 * 
	 * @return a new {@link #tries()} instance
	 */

	default Tries<E> tries() {
		return new Tries<E>(producer());
	}

	/**
	 * Adapts this serialization using a bijective mapping over the values it
	 * serializes. The returned serialization shares the same state (buffer,
	 * length and capacity) as this buffer but applies the specified adapter to
	 * its values post serialization, and disapplies the adapter
	 * pre-serialization.
	 * 
	 * @param adapter
	 *            an adapting bijection
	 * @return a view of this serialization under the adapter
	 */

	default <F> TrieSerialization<F> adapt(Bijection<E, F> adapter) {
		return new AdaptedSerialization<E, F>(this, adapter);
	}
}