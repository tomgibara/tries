package com.tomgibara.tries;


interface TrieNode {

	// attributes
	
	TrieNodes nodes();
	
	byte getValue();
	
	boolean isTerminal();
	
	// has dangling - non terminal and has no child
	boolean isDangling();
	
	void setTerminal(boolean terminal);

	// sibling
	
	boolean hasSibling();
	
	TrieNode getSibling();
	
	// only needed by remove method
	boolean isSibling(TrieNode node);
	
	// child
	
	default boolean hasChild() {
		return getChild() != null;
	}
	
	TrieNode getChild();
	
	// just for symmetry with sibling methods
	boolean isChild(TrieNode node);
	
	// child navigation
	
	TrieNode getLastChild();
	
	TrieNode findChild(byte value);

	TrieNode findChildOrNext(byte value);
	
	TrieNode findOrInsertChild(byte value);
	
	// to is the node's child or one of its siblings, but not including the value supplied
	int countToChild(byte value);

	// miscellaneous
	
	boolean remove(TrieNode childOrSibling);
	
	int getCount();

	// often a no-op
	void delete();

}
