package com.tomgibara.tries;

import java.util.Comparator;

interface TrieNodes {

	Comparator<Byte> byteOrder();
	
	boolean isCounting();
	
	int nodeCount();
	
	long storageSize();
	
	TrieNode root();

	void ensureExtraCapacity(int extraCapacity);
	
	TrieNode newNode(byte value);
	
	int populate(TrieNode root, byte[] values, int length, TrieNode[] stack, TrieNode[] referrers);
	
	void incCounts(TrieNode[] stack, int length);
	
	void decCounts(TrieNode[] stack, int length);
	
	void compact();

	void clear();
	
	long invalidations();
}
