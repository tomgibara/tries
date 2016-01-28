package com.tomgibara.tries;

abstract class AbstractTrieNode implements TrieNode {

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

	// package scoped methods

	// any current sibling becomes sibling of new sibling
	abstract AbstractTrieNode insertSibling(byte value);

	// any current child becomes sibling of new child
	abstract AbstractTrieNode insertChild(byte value);

}
