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
	public AbstractTrieNode findChildOrNext(byte value) {
		ByteOrder order = nodes().byteOrder();
		AbstractTrieNode child = getChild();
		while (child != null) {
			int c = order.compare(child.getValue(), value);
			if (c >= 0) break;
			child = child.getSibling();
		}
		return child;
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

	// package scoped methods

	/**
	 * Returns the child node of this node with the specified value. If at the
	 * time of the method call no such node exists, a new child node with the
	 * given value is added to this node. Thus this method never returns null.
	 * 
	 * @param value
	 *            a node value
	 * @return a child node with the specified value
	 */
	//TODO DOC
	AbstractTrieNode findOrInsertChild(byte value) {
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
	
	/**
	 * Removes the supplied node from the list of child nodes of this node.
	 * 
	 * @param child a child node of this node
	 * @return true iff the node was a child and was removed
	 */
	//TODO DOC
	abstract boolean removeChild(TrieNode child);

	// any current sibling becomes sibling of new sibling
	abstract AbstractTrieNode insertSibling(byte value);

	// any current child becomes sibling of new child
	abstract AbstractTrieNode insertChild(byte value);

	abstract void readChild(ReadStream stream, List<AbstractTrieNode> awaitingSiblings);

	abstract void readSibling(ReadStream stream, List<AbstractTrieNode> awaitingSiblings);

	void writeNode(WriteStream stream, int mask) {
		stream.writeByte(getValue());
		stream.writeByte((byte) (flags() & mask));
	}
	
	void writeNodes(WriteStream stream) {
		stream.writeByte(getValue());
		stream.writeByte((byte) flags());
		if (hasChild()) getChild().writeNodes(stream);
		if (hasSibling()) getSibling().writeNodes(stream);
	}

	int flags() {
		int flags = 0;
		if (isTerminal()) flags |= FLAG_TERMINAL;
		if (hasSibling()) flags |= FLAG_SIBLING;
		if (hasChild()) flags |= FLAG_CHILD;
		return flags;
	}

}
