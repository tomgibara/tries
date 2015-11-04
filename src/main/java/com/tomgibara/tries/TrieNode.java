package com.tomgibara.tries;


interface TrieNode {

	// attributes
	
	TrieNodes nodes();
	
	byte getValue();
	
	boolean isTerminal();
	
	// has dangling - non terminal and has no child
	default boolean isDangling() {
		return !isTerminal() && !hasChild();
	}
	
	void setTerminal(boolean terminal);

	// sibling
	
	boolean hasSibling();
	
	TrieNode getSibling();
	
	// any current sibling becomes sibling of new sibling
	TrieNode insertSibling(byte value);

	boolean isSibling(TrieNode node);
	
	// child
	
	default boolean hasChild() {
		return getChild() != null;
	}
	
	TrieNode getChild();
	
	// any current child becomes sibling of new child
	TrieNode insertChild(byte value);

	// child navigation
	
	default TrieNode getLastChild() {
		TrieNode child = getChild();
		if (child != null) while (child.hasSibling()) child = child.getSibling();
		return child;
	}
	
	default TrieNode findChild(byte value) {
		ByteOrder order = nodes().byteOrder();
		TrieNode child = getChild();
		if (child == null) return null;
		while (true) {
			int c = order.compare(child.getValue(), value);
			if (c == 0) return child;
			if (c > 0) return null;
			if (!child.hasSibling()) return null;
			child = child.getSibling();
		}
	}

	default TrieNode findChildOrNext(byte value) {
		ByteOrder order = nodes().byteOrder();
		TrieNode child = getChild();
		while (child != null) {
			int c = order.compare(child.getValue(), value);
			if (c >= 0) break;
			child = child.getSibling();
		}
		return child;
	}
	
	default TrieNode findOrInsertChild(byte value) {
		ByteOrder order = nodes().byteOrder();
		TrieNode child = getChild();
		if (child == null) return insertChild(value);
		TrieNode previous = null;
		while (true) {
			int c = order.compare(child.getValue(), value);
			if (c == 0) return child;
			if (c > 0) return previous == null ? insertChild(value) : previous.insertSibling(value);
			if (!child.hasSibling()) return child.insertSibling(value);
			previous = child;
			child = child.getSibling();
		}
	}
	
	// to is the node's child or one of its siblings, but not including the value supplied
	default int countToChild(byte value) {
		ByteOrder order = nodes().byteOrder();
		int count = isTerminal() ? 1 : 0;
		TrieNode child = getChild();
		while (child != null && order.compare(child.getValue(), value) < 0) {
			count += child.getCount();
			child = child.getSibling();
		}
		return count;
	}

	// miscellaneous
	
	boolean remove(TrieNode childOrSibling);
	
	int getCount();

	default void delete() {
		/* often a no-op */
	}

}
