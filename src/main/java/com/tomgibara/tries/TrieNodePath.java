package com.tomgibara.tries;

import com.tomgibara.streams.WriteStream;

// retires decCounts(), incCounts(), findOrInsertChild, removeChild, delete, Nodes.writeTo
public interface TrieNodePath {

	// if length is 0
	boolean isEmpty();
	
	// number of nodes that can be stored in addition to the root
	int capacity();
	
	// starts at 1, containing root - may become empty
	int length();
	
	// sets length to 1 and head to root
	void reset();
	
	// head of the path, may be null if root is popped
	TrieNode head();
	
	// findOrInsertChild()
	//TODO should return node?
	void push(byte value);
	
	// decCounts()
	void decrementCounts();
	
	// as per logic in doRemove
	void prune();
	
	// incCounts
	void incrementCounts();
	
	// findChild()
	//TODO should return node?
	boolean walkValue(byte value);

	boolean walkCount(int count);
	
	TrieNode walkChild();
	
	TrieNode walkSibling();
	
	// cannot pop root
	TrieNode pop();

	void serialize(TrieSerialization<?> serialization);
	
	boolean deserialize(TrieSerialization<?> serialization);
	
	void first(TrieSerialization<?> serialization);

	void advance(TrieSerialization<?> serialization, int prefixLength);

	void writeTo(WriteStream stream);

	//TODO TEMPORARY
	TrieNode[] stack();
}
