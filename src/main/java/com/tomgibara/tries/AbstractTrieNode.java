/*
 * Copyright 2016 Tom Gibara
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

import java.util.List;

import com.tomgibara.streams.ReadStream;
import com.tomgibara.streams.WriteStream;

abstract class AbstractTrieNode implements TrieNode {

	// statics
	
	static final int FLAG_TERMINAL = 1;
	static final int FLAG_CHILD    = 2;
	static final int FLAG_SIBLING  = 4;
	
	// node methods

	@Override
	public abstract AbstractTrieNode getChild();
	
	@Override
	public abstract AbstractTrieNode getSibling();
	
	@Override
	public boolean isDangling() {
		return !isTerminal() && !hasChild();
	}
	
	@Override
	public AbstractTrieNode getLastChild() {
		AbstractTrieNode child = getChild();
		if (child != null) while (child.hasSibling()) child = child.getSibling();
		return child;
	}

	@Override
	public AbstractTrieNode findChild(byte value) {
		ByteOrder order = nodes().byteOrder();
		AbstractTrieNode child = getChild();
		if (child == null) return null;
		while (true) {
			int c = order.compare(child.getValue(), value);
			if (c == 0) return child;
			if (c > 0) return null;
			if (!child.hasSibling()) return null;
			child = child.getSibling();
		}
	}

	@Override
	public TrieNode findChildOrNext(byte value) {
		ByteOrder order = nodes().byteOrder();
		TrieNode child = getChild();
		while (child != null) {
			int c = order.compare(child.getValue(), value);
			if (c >= 0) break;
			child = child.getSibling();
		}
		return child;
	}
	
	@Override
	public AbstractTrieNode findOrInsertChild(byte value) {
		ByteOrder order = nodes().byteOrder();
		AbstractTrieNode child = getChild();
		if (child == null) return insertChild(value);
		AbstractTrieNode previous = null;
		while (true) {
			int c = order.compare(child.getValue(), value);
			if (c == 0) return child;
			if (c > 0) return previous == null ? insertChild(value) : previous.insertSibling(value);
			if (!child.hasSibling()) return child.insertSibling(value);
			previous = child;
			child = child.getSibling();
		}
	}
	
	@Override
	public int countToChild(byte value) {
		ByteOrder order = nodes().byteOrder();
		int count = isTerminal() ? 1 : 0;
		AbstractTrieNode child = getChild();
		while (child != null && order.compare(child.getValue(), value) < 0) {
			count += child.getCount();
			child = child.getSibling();
		}
		return count;
	}
	
	@Override
	public void delete() { }

	@Override
	public void writeNodes(WriteStream stream) {
		CountingStream counter = new CountingStream();
		doWriteNodes(counter);
		// each node writes two bytes
		int count = counter.count() >> 1;
		// write number of nodes
		stream.writeInt(count);
		// write nodes
		doWriteNodes(stream);
	}

	// package scoped methods

	// any current sibling becomes sibling of new sibling
	abstract AbstractTrieNode insertSibling(byte value);

	// any current child becomes sibling of new child
	abstract AbstractTrieNode insertChild(byte value);

	abstract void readChild(ReadStream stream, List<AbstractTrieNode> awaitingSiblings);

	abstract void readSibling(ReadStream stream, List<AbstractTrieNode> awaitingSiblings);
	
	private void writeNode(WriteStream stream) {
		stream.writeByte(getValue());
		stream.writeByte(flags());
		if (hasChild()) getChild().writeNode(stream);
		if (hasSibling()) getSibling().writeNode(stream);
	}

	byte flags() {
		int flags = 0;
		if (isTerminal()) flags |= FLAG_TERMINAL;
		if (hasSibling()) flags |= FLAG_SIBLING;
		if (hasChild()) flags |= FLAG_CHILD;
		return (byte) flags;
	}
	private void doWriteNodes(WriteStream stream) {
		// root always written with zero value
		stream.writeByte((byte) 0);
		stream.writeByte(flags());
		if (hasChild()) getChild().writeNode(stream);
		// sibling of root never written
	}

	//TODO move to streams package?
	private static class CountingStream implements WriteStream {

		private int count = 0;
		
		public int count() {
			return count;
		}
		
		@Override
		public void writeByte(byte v) { count ++; }
		
		@Override
		public void writeBytes(byte[] bs) { count += bs.length; }
		
		@Override
		public void writeBytes(byte[] bs, int off, int len) { count += len; }
		
		@Override
		public void writeInt(int v) { count += 4; }
		
		@Override
		public void writeBoolean(boolean v) { count += 1; }
		
		@Override
		public void writeShort(short v) { count += 2; }
		
		@Override
		public void writeLong(long v) { count += 8; }
		
		@Override
		public void writeFloat(float v) { count += 4; }
		
		@Override
		public void writeDouble(double v) { count += 8; }
		
		@Override
		public void writeChar(char v) { count += 2; }
		
		@Override
		public void writeChars(char[] cs) { count += 4; }
		
		@Override
		public void writeChars(char[] cs, int off, int len) { count += len; }
		
		@Override
		public void writeChars(CharSequence cs) { count += 4 + cs.length(); }
		
	}
}
