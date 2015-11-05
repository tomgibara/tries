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
import com.tomgibara.streams.ByteReadStream;
import com.tomgibara.streams.ByteWriteStream;
import com.tomgibara.streams.StreamDeserializer;
import com.tomgibara.streams.StreamSerializer;

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
	
	// inner classes
	
	private static abstract class BaseSerialization<E> implements TrieSerialization<E> {

		byte[] buffer = new byte[16];
		int length = 0;

		BaseSerialization() { }
		
		BaseSerialization(BaseSerialization<E> that) {
			this.buffer = new byte[that.buffer.length];
			this.length = 0;
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
			try(ByteReadStream s = new ByteReadStream(buffer, 0, length)) {
				return s.readWith(deserializer).produce();
			}
		}
		
		@Override
		public void set(E e) {
			try (ByteWriteStream s = new ByteWriteStream(buffer)) {
				s.writeWith(serializer).consume(e);
				length = s.position();
				buffer = s.getBytes(false);
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
		public TrieSerialization<String> resetCopy() {
			return new StringSerialization(this);
		}
		
	}

	// fields

	final Producer<TrieSerialization<E>> serialProducer;
	private int capacityHint = DEFAULT_CAPACITY;
	private ByteOrder byteOrder = ByteOrder.UNSIGNED;
	private TrieNodeSource nodeSource = PackedTrieNodes.SOURCE;

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
	
	public IndexedTrie<E> newIndexedTrie() {
		return new IndexedTrie<>(this, newIndexedNodes());
	}

	// package scoped methods
	
	TrieNodes newNodes() {
		return nodeSource.newNodes(byteOrder, false, capacityHint);
	}
	
	TrieNodes newIndexedNodes() {
		return nodeSource.newNodes(byteOrder, true, capacityHint);
	}
}
