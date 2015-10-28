package com.tomgibara.tries;

interface TrieNode {

	// attributes
	
	byte getValue();
	
	boolean isTerminal();
	
	// has dangling - non terminal and has no child
	boolean isDangling();
	
	void setTerminal(boolean terminal);

	// sibling
	
	boolean hasSibling();
	
	TrieNode getSibling();
	
	// any current sibling becomes sibling of new sibling
	TrieNode insertSibling(byte value);

	boolean isSibling(TrieNode node);
	
	// child
	
	boolean hasChild();
	
	TrieNode getChild();
	
	// any current child becomes sibling of new child
	TrieNode insertChild(byte value);

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

	void delete();

}
