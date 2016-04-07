package com.tomgibara.tries.nodes;

import com.tomgibara.streams.WriteStream;

public final class ImmutableTrieNodePath implements TrieNodePath {

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

	@Override
	public int length() {
		return path.length();
	}

	@Override
	public void reset() {
		path.reset();
	}

	@Override
	public TrieNode head() {
		return path.head();
	}

	@Override
	public void push(byte value) {
		imm();
	}

	@Override
	public boolean terminate(boolean terminal) {
		return imm();
	}

	@Override
	public void dangle() {
		imm();
	}

	@Override
	public void prune() {
		imm();
	}
	
	@Override
	public boolean walkValue(byte value) {
		return path.walkValue(value);
	}

	@Override
	public boolean walkCount(int count) {
		return path.walkCount(count);
	}

	@Override
	public boolean walkChild() {
		return path.walkChild();
	}

	@Override
	public boolean walkLastChild() {
		return path.walkLastChild();
	}

	@Override
	public boolean walkSibling() {
		return path.walkSibling();
	}

	@Override
	public TrieNode pop() {
		return path.pop();
	}

	@Override
	public void serialize() {
		path.serialize();
	}

	@Override
	public boolean deserializeWithWalk() {
		return path.deserializeWithWalk();
	}

	@Override
	public void deserializeWithPush() {
		imm();
	}
	
	@Override
	public void first(int minimumLength) {
		path.first(minimumLength);
	}

	@Override
	public void advance(int minimumLength) {
		path.advance(minimumLength);
	}

	@Override
	public void writeTo(WriteStream stream) {
		path.writeTo(stream);
	}

}
