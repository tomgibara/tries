package com.tomgibara.tries;

import com.tomgibara.fundament.Mutability;


interface TrieNodes extends Mutability<TrieNodes> {

	ByteOrder byteOrder();
	
	boolean isCounting();
	
	//NOTE may be a very slow operation
	int nodeCount();
	
	//NOTE may be a very slow operation
	long storageSize();
	
	TrieNode root();

	default void ensureExtraCapacity(int extraCapacity) {
		/* a no-op for many possible implementations */
	}
	
	TrieNode newNode(byte value);
	
	default int populate(TrieNode root, byte[] values, int length, TrieNode[] stack, TrieNode[] referrers) {
		ByteOrder byteOrder = byteOrder();
		TrieNode node = root;
		TrieNode referrer = null;
		outer: for (int i = 0; i < length; i++) {
			byte b = values[i];
			TrieNode child = node.getChild();
			if (child == null) return i;
			referrer = node;
			while (true) {
				int c = byteOrder.compare(child.getValue(), b);
				if (c == 0) {
					node = child;
					stack[i] = node;
					referrers[i] = referrer;
					continue outer;
				}
				if (c > 0 || !child.hasSibling()) return i;
				referrer = child;
				child = child.getSibling();
			}
		}
		return length;
	}
	
	void incCounts(TrieNode[] stack, int length);
	
	void decCounts(TrieNode[] stack, int length);
	
	default void compact() {
		/* no-op by default */
	}

	void clear();
	
	long invalidations();
	
	// mutability
	
	default TrieNodes immutableView() {
		return new ImmutableNodes(this);
	}

	@Override
	//TODO want a specialized node implementation for immutable copy
	default TrieNodes immutableCopy() {
		return mutableCopy().immutableView();
	}
	
	//TODO create basic node copy
//	default TrieNodes mutableCopy() {
//		
//	}
}
