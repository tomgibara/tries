package com.tomgibara.tries;

interface TrieNode {

	byte getValue();
	
	//TODO currently unused
	void setValue(byte value);
	
	boolean isTerminal();
	
	// has dangling - non terminal and has no child
	boolean isDangling();
	
	void setTerminal(boolean terminal);
	
	TrieNode getSibling();
	
	boolean hasSibling();
	
	//TODO currently unused
	void setSibling(TrieNode sibling);
	
	// any current sibling becomes sibling of new sibling
	TrieNode insertSibling(byte value);

	boolean isSibling(TrieNode node);
	
	boolean hasChild();
	
	TrieNode getChild();
	
	TrieNode getLastChild();
	
	//TODO currently unused
	void setChild(TrieNode child);
	
	// any current child becomes sibling of new child
	TrieNode insertChild(byte value);
	
	TrieNode findChild(byte value);

	TrieNode findChildOrNext(byte value);
	
	TrieNode findOrInsertChild(byte value);
	
	boolean remove(TrieNode childOrSibling);
	
	int getCount();

	// to is the node's child or one of its siblings, but not including the value supplied
	int countTo(byte value);
	
	void delete();
}
