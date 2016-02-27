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

import com.tomgibara.fundament.Producer;
import com.tomgibara.storage.Stores;
import com.tomgibara.streams.ReadStream;
import com.tomgibara.streams.StreamBytes;
import com.tomgibara.streams.StreamDeserializer;
import com.tomgibara.streams.StreamSerializer;
import com.tomgibara.streams.Streams;
import com.tomgibara.streams.WriteStream;

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
 * return new immutable instances of this class via a builder pattern.
 * 
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

	/**
	 * Uses a serializer/deserializer pair to define a {@link TrieSerialization}
	 * from which tries are defined.
	 * 
	 * @param type
	 *            the type of object marshalled by the serializers.
	 * @param serializer
	 *            writes objects of the specified type to a byte stream
	 * @param deserializer
	 *            reads objects of the spefified type from a byte stream
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

	public static Tries<String> strings(Charset charset) {
		if (charset == null) throw new IllegalArgumentException("null charset");
		return new Tries<>(() -> new StringSerialization(charset));
	}

	/**
	 * Creates tries that can store byte arrays.
	 * 
	 * @return tries containing byte arrays
	 */

	public static Tries<byte[]> bytes() {
		return new Tries<>(() -> new ByteSerialization());
	}

	// used for byte tries
	static TrieSerialization<byte[]> newByteSerialization(int capacity) {
		return new ByteSerialization(capacity);
	}
	
	// inner classes
	
	private static abstract class BaseSerialization<E> implements TrieSerialization<E> {

		byte[] buffer = new byte[16];
		int length = 0;

		BaseSerialization() { }
		
		BaseSerialization(int capacity) {
			buffer = new byte[capacity];
		}
		
//		BaseSerialization(BaseSerialization<E> that) {
//			this.buffer = new byte[that.buffer.length];
//		}
//		
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
			return Stores.bytes(buffer).resizedCopy(length).asList().toString();
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
		public StreamSerialization<E> resetCopy(int capacity) {
			return new StreamSerialization<>(this, capacity);
		}
	}
	
	private static class StringSerialization extends BaseSerialization<String> {

		private final CharsetEncoder encoder;

		StringSerialization(Charset charset) {
			encoder = charset.newEncoder();
		}
		
		private  StringSerialization(StringSerialization that, int capacity) {
			super(capacity);
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
		public TrieSerialization<String> resetCopy(int capacity) {
			return new StringSerialization(this, capacity);
		}
		
	}

	private static class ByteSerialization extends BaseSerialization<byte[]> {
		
		ByteSerialization() { }
		
		ByteSerialization(int capacity) {
			super(capacity);
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
				length = prefix.length;
			}
		}

		@Override
		public byte[] get() {
			return Arrays.copyOf(buffer, length);
		}
		
		@Override
		public TrieSerialization<byte[]> resetCopy(int capacity) {
			return new ByteSerialization(capacity);
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
	 * An instance of this class, with the same configuration, that creates
	 * indexed tries.
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
	 * An instance of this class, with the same configuration, that returns
	 * indexed tries or non-indexed tries as-per the parameter.
	 * 
	 * @param indexed
	 *            whether the tries generated the returned object should index
	 *            their elements
	 * @return tries with the indidcated indexation
	 * @see #indexed()
	 * @see IndexedTries#unindexed()
	 */

	public Tries<E> indexed(boolean indexed) {
		return indexed ? indexed() : this;
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
	 * Creates a copy of an existing trie. The copy will be mutable of the
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
