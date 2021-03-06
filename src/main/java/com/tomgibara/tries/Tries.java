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

import static java.lang.Math.max;
import static java.lang.Math.round;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.Arrays;
import java.util.Comparator;
import java.util.StringJoiner;

import com.tomgibara.fundament.Bijection;
import com.tomgibara.fundament.Producer;
import com.tomgibara.streams.ReadStream;
import com.tomgibara.streams.StreamBytes;
import com.tomgibara.streams.StreamDeserializer;
import com.tomgibara.streams.StreamSerializer;
import com.tomgibara.streams.Streams;
import com.tomgibara.streams.WriteStream;
import com.tomgibara.tries.nodes.TrieNodeSource;
import com.tomgibara.tries.nodes.TrieNodes;

/**
 * <p>
 * A class for creating {@link Trie} instances and a convenient entry point for
 * this library. Instances of this class may be created from any
 * {@link TrieSerialization}. Tries that use common serializations may be
 * created directly from this class.
 *
 * <p>
 * Instances of this class are immutable, and are safe for multithreaded use.
 * Methods to configure and customize the creation of tries are available. These
 * return new immutable instances of this class via chainable methods.
 *
 * <p>
 * The class also defines a number of 'standard' {@link TrieNodeSource}
 * implementations that are optimized for different scenarios. Other than their
 * convenient availability as part of this package, they are not distinguished
 * from alternatively defined sources that may better suite some applications.
 *
 * @author Tom Gibara
 *
 * @param <E>
 *            the type of elements to be stored
 * @see Trie
 * @see TrieSerialization#tries()
 */

public class Tries<E> {

	// statics

	private static final int DEFAULT_CAPACITY = 16;

	// used for byte tries
	static TrieSerialization<byte[]> newByteSerialization(int capacity) {
		return new ByteSerialization(capacity);
	}

	// serializers

	/**
	 * Uses a serializer/deserializer pair to define a {@link TrieSerialization}
	 * from which tries are defined.
	 *
	 * @param type
	 *            the type of object marshalled by the serializers.
	 * @param serializer
	 *            writes objects of the specified type to a byte stream
	 * @param deserializer
	 *            reads objects of the specified type from a byte stream
	 * @param <E>
	 *            the type of values stored in the tries
	 * @return tries based on the supplied serialization
	 */

	public static <E> Tries<E> serial(Class<E> type, StreamSerializer<E> serializer, StreamDeserializer<E> deserializer) {
		if (type == null) throw new IllegalArgumentException("null type");
		if (serializer == null) throw new IllegalArgumentException("null serializer");
		if (deserializer == null) throw new IllegalArgumentException("null deserializer");
		return new Tries<>(() -> new StreamSerialization<E>(type, serializer, deserializer));
	}

	/**
	 * Creates tries to contain strings under a specified encoding. Since the
	 * tries are byte based, a specific encoding is needed to provide an
	 * unambiguous encoding.
	 *
	 * @param charset
	 *            the character encoding used to convert the string to/from
	 *            bytes
	 * @return tries containing strings
	 */

	public static Tries<String> serialStrings(Charset charset) {
		if (charset == null) throw new IllegalArgumentException("null charset");
		return new Tries<>(() -> new StringSerialization(charset));
	}

	/**
	 * Creates tries that can store byte arrays.
	 *
	 * @return tries containing byte arrays
	 */

	public static Tries<byte[]> serialBytes() {
		return new Tries<>(() -> new ByteSerialization());
	}

	/**
	 * Creates tries that can store longs.
	 *
	 * @return tries containing longs
	 */

	public static Tries<Long> serialLongs() {
		return new Tries<>(() -> new LongSerialization());
	}

	// node sources

	/**
	 * Models trie nodes using a Java object for each node; this is fast, at
	 * the expense of a larger memory overhead than other implementations.
	 *
	 * @return a source that provides generally good performance.
	 */

	public static TrieNodeSource sourceForSpeed() {
		return BasicTrieNodes.SOURCE;
	}

	/**
	 * Models trie nodes using integers to reduce the memory overhead associated
	 * with Java objects. To further reduce memory overhead there is additional
	 * logic to byte-pack non-branching child sequences. This implementation
	 * typically operates approximately half as fast as {@link #sourceForSpeed()}
	 *
	 * @return a source that stores reduces memory overhead
	 */

	public static TrieNodeSource sourceForCompactness() {
		return PackedTrieNodes.SOURCE;
	}

	/**
	 * Models trie nodes similarly to {@link #sourceForCompactness()} with further
	 * logic that linearizes siblings during compaction to enable binary
	 * searches over successor nodes. This typically doubles the speed of
	 * lookups over the {@link #sourceForCompactness()} implementation at the expense
	 * of doubling the time for removals.
	 *
	 * @return a source that facilitates faster lookups over a compact memory
	 *         representation
	 */

	public static TrieNodeSource sourceForCompactLookups() {
		return CompactTrieNodes.SOURCE;
	}

	// inner classes

	private static abstract class BaseSerialization<E> implements TrieSerialization<E> {

		byte[] buffer;
		int length;

		BaseSerialization(byte[] buffer, int length) {
			this.buffer = buffer;
			this.length = length;
		}

		BaseSerialization() {
			this(new byte[16], 0);
		}

		BaseSerialization(int capacity) {
			this(new byte[capacity], 0);
		}

		BaseSerialization(BaseSerialization<E> that) {
			this(that.buffer.clone(), that.length);
		}

		@Override
		public byte[] buffer() {
			return buffer;
		}

		@Override
		public void push(byte b) {
			checkBuffer();
			buffer[length++] = b;
		}

		@Override
		public void replace(byte b) {
			checkLength();
			buffer[length - 1] = b;
		}

		@Override
		public void pop() {
			checkLength();
			length--;
		}

		@Override
		public int length() {
			return length;
		}

		@Override
		public void trim(int newLength) {
			this.length = newLength;
		}

		@Override
		public void reset() {
			length = 0;
		}

		@Override
		public boolean startsWith(byte[] prefix) {
			if (prefix.length > length) return false;
			for (int i = 0; i < prefix.length; i++) {
				if (buffer[i] != prefix[i]) return false;
			}
			return true;
		}

		@Override
		public void set(byte[] prefix) {
			length = prefix.length;
			System.arraycopy(prefix, 0, buffer, 0, length);
		}

		private void checkLength() {
			if (length == 0) throw new IllegalStateException();
		}

		private void checkBuffer() {
			if (length == buffer.length) {
				buffer = Arrays.copyOf(buffer, 2 * length);
			}
		}

		@Override
		public String toString() {
			StringJoiner joiner = new StringJoiner(",", "[", "]");
			for (int i = 0; i < length; i++) {
				joiner.add(String.valueOf(buffer[i]));
			}
			return joiner.toString();
		}
	}

	private static class StreamSerialization<E> extends BaseSerialization<E> {

		private final Class<E> type;
		private final StreamSerializer<E> serializer;
		private final StreamDeserializer<E> deserializer;

		private StreamSerialization(Class<E> type, StreamSerializer<E> serializer, StreamDeserializer<E> deserializer) {
			this.type = type;
			this.serializer = serializer;
			this.deserializer = deserializer;
		}

		private StreamSerialization(StreamSerialization<E> that, int capacity) {
			super(capacity);
			this.type = that.type;
			this.serializer = that.serializer;
			this.deserializer = that.deserializer;
		}

		private StreamSerialization(StreamSerialization<E> that) {
			super(that);
			this.type = that.type;
			this.serializer = that.serializer;
			this.deserializer = that.deserializer;
		}

		@Override
		public boolean isSerializable(Object obj) {
			return type.isInstance(obj);
		}

		@Override
		public E get() {
			return Streams.bytes(buffer, length).readStream().readWith(deserializer).produce();
		}

		@Override
		public void set(E e) {
			StreamBytes bytes = Streams.bytes(buffer);
			try (WriteStream s = bytes.writeStream()) {
				s.writeWith(serializer).consume(e);
				length = bytes.length();
				buffer = bytes.directBytes();
			}
		}

		@Override
		public TrieSerialization<E> copy() {
			return new StreamSerialization<>(this);
		}

		@Override
		public StreamSerialization<E> resetCopy(int capacity) {
			return new StreamSerialization<>(this, capacity);
		}
	}

	private static class StringSerialization extends BaseSerialization<String> {

		private final CharsetEncoder encoder;

		StringSerialization(Charset charset) {
			encoder = charset.newEncoder();
		}

		private StringSerialization(StringSerialization that, int capacity) {
			super(capacity);
			this.encoder = that.encoder;
		}

		private StringSerialization(StringSerialization that) {
			super(that);
			this.encoder = that.encoder;
		}

		@Override
		public boolean isSerializable(Object obj) {
			return obj instanceof String;
		}

		@Override
		public void set(String e) {
			CharBuffer in = CharBuffer.wrap(e);
			ByteBuffer out = ByteBuffer.wrap(buffer);
			try {
				while (true) {
					CoderResult result = encoder.encode(in, out, true);
					if (result == CoderResult.UNDERFLOW) {
						length = out.position();
						return;
					}
					if (result == CoderResult.OVERFLOW) {
						int position = out.position();
						int extra = max(16, round(encoder.averageBytesPerChar() * (e.length() - in.position())));
						int newLength = position + extra;
						buffer = Arrays.copyOf(buffer, newLength);
						out = ByteBuffer.wrap(buffer);
						out.position(position);
					}
					if (result.isUnmappable()) {
						throw new IllegalArgumentException("unmappable character for " + encoder.charset());
					}
					if (result.isMalformed()) {
						throw new IllegalStateException("malformed input");
					}

				}
			} finally {
				encoder.reset();
			}
		}

		@Override
		public String get() {
			return new String(buffer, 0, length, encoder.charset());
		}

		@Override
		public TrieSerialization<String> copy() {
			return new StringSerialization(this);
		}

		@Override
		public TrieSerialization<String> resetCopy(int capacity) {
			return new StringSerialization(this, capacity);
		}

	}

	private static class ByteSerialization extends BaseSerialization<byte[]> {

		ByteSerialization() { }

		ByteSerialization(int capacity) {
			super(capacity);
		}

		private ByteSerialization(ByteSerialization that) {
			super(that);
		}

		@Override
		public boolean isSerializable(Object obj) {
			return obj instanceof byte[];
		}

		@Override
		public void set(byte[] prefix) {
			if (prefix.length > buffer.length) {
				buffer = prefix.clone();
			} else {
				System.arraycopy(prefix, 0, buffer, 0, prefix.length);
			}
			length = prefix.length;
		}

		@Override
		public byte[] get() {
			return Arrays.copyOf(buffer, length);
		}

		@Override
		public TrieSerialization<byte[]> copy() {
			return new ByteSerialization(this);
		}

		@Override
		public TrieSerialization<byte[]> resetCopy(int capacity) {
			return new ByteSerialization(capacity);
		}
	}

	private static class LongSerialization extends BaseSerialization<Long> {

		LongSerialization() {
			super(8);
		}

		private LongSerialization(int capacity) {
			super(capacity);
		}

		private LongSerialization(LongSerialization that) {
			super(that);
		}

		@Override
		public boolean isSerializable(Object obj) {
			return obj instanceof Long;
		}

		@Override
		public void set(Long e) {
			long v = e;
			buffer[0] = (byte) (v >> 56);
			buffer[1] = (byte) (v >> 48);
			buffer[2] = (byte) (v >> 40);
			buffer[3] = (byte) (v >> 32);
			buffer[4] = (byte) (v >> 24);
			buffer[5] = (byte) (v >> 16);
			buffer[6] = (byte) (v >>  8);
			buffer[7] = (byte) (v      );
			length = 8;
		}

		@Override
		public Long get() {
			if (length < 8) throw new IllegalStateException("too few bytes");
			long high =
				((buffer[0]       ) << 24) |
				((buffer[1] & 0xff) << 16) |
				((buffer[2] & 0xff) <<  8) |
				((buffer[3] & 0xff)      );
			long low =
					((buffer[4]       ) << 24) |
					((buffer[5] & 0xff) << 16) |
					((buffer[6] & 0xff) <<  8) |
					((buffer[7] & 0xff)      );
			return (high << 32) | low & 0xffffffffL;
		}

		@Override
		public TrieSerialization<Long> copy() {
			return new LongSerialization(this);
		}

		@Override
		public LongSerialization resetCopy(int capacity) {
			return new LongSerialization(capacity);
		}

		@Override
		public Comparator<Long> comparator(ByteOrder byteOrder) {
			return byteOrder == ByteOrder.UNSIGNED ? (x,y) -> Long.compareUnsigned(x, y) : super.comparator(byteOrder);
		}

	}

	// fields

	final Producer<TrieSerialization<E>> serialProducer;
	final ByteOrder byteOrder;
	final TrieNodeSource nodeSource;
	final int capacityHint;

	// constructors

	Tries(Producer<TrieSerialization<E>> serialProducer) {
		this(serialProducer, ByteOrder.UNSIGNED, CompactTrieNodes.SOURCE, DEFAULT_CAPACITY);
	}

	Tries(
			Producer<TrieSerialization<E>> serialProducer,
			ByteOrder byteOrder,
			TrieNodeSource nodeSource,
			int capacityHint
			) {
		this.serialProducer = serialProducer;
		this.byteOrder = byteOrder;
		this.nodeSource = nodeSource;
		this.capacityHint = capacityHint;
	}

	/**
	 * An instance with the same configuration, that creates unindexed tries.
	 *
	 * @return tries with non-indexed elements
	 */

	public Tries<E> unindexed() {
		return this;
	}

	/**
	 * An instance with the same configuration, that creates indexed tries.
	 *
	 * @return tries with indexed elements
	 * @throws IllegalStateException
	 *             if the configured node source does not support counting
	 * @see IndexedTrie
	 */

	public IndexedTries<E> indexed() {
		if (!nodeSource.isCountingSupported()) throw new IllegalStateException("counting not supported");
		return new IndexedTries<>(serialProducer, byteOrder, nodeSource, capacityHint);
	}

	/**
	 * Whether the tries are indexed.
	 *
	 * @return true if the tries created are indexed, false if not
	 * @see #indexed()
	 * @see IndexedTries#unindexed()
	 */

	public boolean indexing() {
		return false;
	}

	// mutation methods

	/**
	 * Converts a byte comparator into a byte-order for the tries. The byte
	 * order is applied consistently at all positions in the trie.
	 *
	 * @param comparator
	 *            a comparator of byte values
	 * @return tries under the specified ordering
	 * @see ByteOrder#from(Comparator)
	 */

	public Tries<E> byteOrder(Comparator<Byte> comparator) {
		return new Tries<>(serialProducer, ByteOrder.from(comparator), nodeSource, capacityHint);
	}

	/**
	 * Specifies a byte-order for the tries. The byte order is applied
	 * consistently at all positions in the trie.
	 *
	 * @param byteOrder
	 *            an ordering of byte values
	 * @return tries under the specified ordering
	 * @see ByteOrder#from(Comparator)
	 */

	public Tries<E> byteOrder(ByteOrder byteOrder) {
		if (byteOrder == null) throw new IllegalArgumentException("null byteOrder");
		return new Tries<>(serialProducer, byteOrder, nodeSource, capacityHint);
	}

	/**
	 * Controls the nodes that will be used to store the elements in tries.
	 *
	 * @param nodeSource
	 *            a source of trie nodes
	 * @return tries using the specified nodes
	 */

	public Tries<E> nodeSource(TrieNodeSource nodeSource) {
		if (nodeSource == null) throw new IllegalArgumentException("null nodeSource");
		return new Tries<>(serialProducer, byteOrder, nodeSource, capacityHint);
	}

	/**
	 * Applies an adapter to the serialization used for the tries to create a
	 * tries over the range of the adapter.
	 *
	 * @param adapter
	 *            a bijective mapping over the trie elements
	 * @param <F>
	 *            the type of the adapted elements
	 *
	 * @return tries adapted to store values in the range of the adapter
	 * @see Trie#asAdaptedWith(Bijection)
	 */

	public <F> Tries<F> adaptedWith(Bijection<E, F> adapter) {
		if (adapter == null) throw new IllegalArgumentException("null adapter");
		Producer<TrieSerialization<F>> adapted = () -> serialProducer.produce().adapt(adapter);
		return new Tries<>(adapted, byteOrder, nodeSource, capacityHint);
	}

	/*  TG: viability TBD
	public Tries<E> capacityHint(int capacityHint) {
		if (capacityHint < 0) throw new IllegalArgumentException("negative capacityHint");
		return new Tries<>(serialProducer, byteOrder, nodeSource, capacityHint);
	}
	*/

	// creation methods

	/**
	 * Creates a new trie. The trie will be empty and will be mutable if the
	 * configured node source supports the creation of mutable nodes.
	 *
	 * @return a new trie
	 */

	public Trie<E> newTrie() {
		return new Trie<>(this, newNodes());
	}

	/**
	 * Creates a copy of an existing trie. The copy will be mutable if the
	 * configured node source supports it. The trie being copied is not required
	 * to have originated from an identically configured {@link Tries} instance
	 * but <em>must</em> use a compatible serialization (this constraint is not
	 * enforced). Copying can be expected to be significantly faster if the trie
	 * shares the same byte order.
	 *
	 * @param trie
	 *            the trie to be copied
	 * @return the copied trie
	 */

	public Trie<E> copyTrie(Trie<E> trie) {
		if (trie.nodes.byteOrder().equals(byteOrder)) {
			// fast path - we can adopt the nodes, they're in the right order
			TrieNodes nodes = nodeSource.copyNodes(trie.nodes, false, capacityHint);
			return new Trie<>(this, nodes);
		} else {
			// slow path - just treat it as an add-all
			Trie<E> newTrie = new Trie<>(this, newNodes());
			newTrie.addAll(trie);
			return newTrie;
		}
	}

	/**
	 * Reads a trie that was previously serialized via its
	 * {@link Trie#writeTo(WriteStream)} method. The stream must have been
	 * generated by a compatible {@link Tries} instance, unless otherwise
	 * documented this means an instance with identical configuration.
	 *
	 * @param stream
	 *            the stream to read from
	 * @return a trie read from the stream
	 */

	public Trie<E> readTrie(ReadStream stream) {
		return new Trie<>(this, nodeSource.deserializer(byteOrder, false, capacityHint).deserialize(stream));
	}

	// private utility methods

	private TrieNodes newNodes() {
		return nodeSource.newNodes(byteOrder, false, capacityHint);
	}

}
