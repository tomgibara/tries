package com.tomgibara.tries;

import com.tomgibara.streams.WriteStream;

public class ImmutableTrieNodePath implements TrieNodePath {

	private static final <T> T imm() {
		throw new IllegalStateException("immutable");
	}

	private final TrieNodePath path;

	ImmutableTrieNodePath(TrieNodePath path) {
		this.path = path;
	}

	public int capacity() {
		return path.capacity();
	}

	@Override
	public boolean isEmpty() {
		return path.isEmpty();
	}

	public int length() {
		return path.length();
	}

	@Override
	public void reset() {
		path.reset();
	}

	public TrieNode head() {
		return path.head();
	}

	public void push(byte value) {
		imm();
	}

	public void decrementCounts() {
		imm();
	}

	public void prune() {
		imm();
	}

	public void incrementCounts() {
		imm();
	}

	public boolean walkValue(byte value) {
		return path.walkValue(value);
	}

	@Override
	public boolean walkCount(int count) {
		return path.walkCount(count);
	}

	public TrieNode walkChild() {
		return path.walkChild();
	}

	public TrieNode walkSibling() {
		return path.walkSibling();
	}

	public TrieNode pop() {
		return path.pop();
	}
	
	@Override
	public void serialize(TrieSerialization<?> serialization) {
		path.serialize(serialization);
	}

	@Override
	public boolean deserialize(TrieSerialization<?> serialization) {
		return path.deserialize(serialization);
	}

	@Override
	public void first(TrieSerialization<?> serialization) {
		path.first(serialization);
	}

	@Override
	public void advance(TrieSerialization<?> serialization, int prefixLength) {
		path.advance(serialization, prefixLength);
	}

	@Override
	public void writeTo(WriteStream stream) {
		path.writeTo(stream);
	}
}
