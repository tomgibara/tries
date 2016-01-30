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
import com.tomgibara.streams.StreamBytes;
import com.tomgibara.streams.StreamDeserializer;
import com.tomgibara.streams.StreamSerializer;
import com.tomgibara.streams.Streams;
import com.tomgibara.streams.WriteStream;

public class Tries<E> {

	// statics
	
	private static final int DEFAULT_CAPACITY = 16;
	
	public static <E> Tries<E> serial(Class<E> type, StreamSerializer<E> serializer, StreamDeserializer<E> deserializer) {
		if (type == null) throw new IllegalArgumentException("null type");
		if (serializer == null) throw new IllegalArgumentException("null serializer");
		if (deserializer == null) throw new IllegalArgumentException("null deserializer");
		return new Tries<>(() -> new StreamSerialization<E>(type, serializer, deserializer));
	}
	
	public static Tries<String> strings(Charset charset) {
		if (charset == null) throw new IllegalArgumentException("null charset");
		return new Tries<>(() -> new StringSerialization(charset));
	}

	public static Tries<byte[]> bytes() {
		return new Tries<>(() -> new ByteSerialization());
	}

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

		private StreamSerialization(StreamSerialization<E> that) {
			super(that.buffer.length);
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
		public StreamSerialization<E> resetCopy() {
			return new StreamSerialization<>(this);
		}
	}
	
	private static class StringSerialization extends BaseSerialization<String> {

		private final CharsetEncoder encoder;

		StringSerialization(Charset charset) {
			encoder = charset.newEncoder();
		}
		
		private  StringSerialization(StringSerialization that) {
			super(that.buffer.length);
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
		public TrieSerialization<String> resetCopy() {
			return new StringSerialization(this);
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
		public TrieSerialization<byte[]> resetCopy() {
			return new ByteSerialization(buffer.length);
		}
	}
	
	// fields

	final Producer<TrieSerialization<E>> serialProducer;
	private int capacityHint = DEFAULT_CAPACITY;
	private ByteOrder byteOrder = ByteOrder.UNSIGNED;
	private TrieNodeSource nodeSource = CompactTrieNodes.SOURCE;

	// constructors

	Tries(Producer<TrieSerialization<E>> serialProducer) {
		this.serialProducer = serialProducer;
	}
	
	// mutation methods
	
	public Tries<E> byteOrder(Comparator<Byte> comparator) {
		this.byteOrder = ByteOrder.from(comparator);
		return this;
	}
	
	public Tries<E> byteOrder(ByteOrder byteOrder) {
		if (byteOrder == null) throw new IllegalArgumentException("null byteOrder");
		this.byteOrder = byteOrder;
		return this;
	}
	
	public Tries<E> nodeSource(TrieNodeSource nodeSource) {
		if (nodeSource == null) throw new IllegalArgumentException("null nodeSource");
		this.nodeSource = nodeSource;
		return this;
	}
	
	public Tries<E> capacityHint(int capacityHint) {
		if (capacityHint < 0) throw new IllegalArgumentException("negative capacityHint");
		this.capacityHint = capacityHint;
		return this;
	}
	
	// creation methods
	
	public Trie<E> newTrie() {
		return new Trie<>(this, newNodes());
	}
	
	public Trie<E> newTrie(Trie<E> trie) {
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
	
	public IndexedTrie<E> newIndexedTrie() {
		return new IndexedTrie<>(this, newIndexedNodes());
	}

	public IndexedTrie<E> newIndexedTrie(Trie<E> trie) {
		if (trie.nodes.byteOrder().equals(byteOrder)) {
			// fast path - we can adopt the nodes, they're in the right order
			TrieNodes nodes = nodeSource.copyNodes(trie.nodes, true, capacityHint);
			return new IndexedTrie<>(this, nodes);
		} else {
			// slow path - just treat it as an add-all
			IndexedTrie<E> newTrie = new IndexedTrie<>(this, newIndexedNodes());
			newTrie.addAll(trie);
			return newTrie;
		}
	}
	
	// package scoped methods
	
	TrieNodes newNodes() {
		return nodeSource.newNodes(byteOrder, false, capacityHint);
	}
	
	TrieNodes newIndexedNodes() {
		return nodeSource.newNodes(byteOrder, true, capacityHint);
	}
	
}
