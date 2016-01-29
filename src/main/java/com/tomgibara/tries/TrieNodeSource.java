package com.tomgibara.tries;

public interface TrieNodeSource {

	// implementation models trie nodes using Java objects. This is fast, at
	// the expense of a large memory overhead
	static TrieNodeSource forSpeed() {
		return BasicTrieNodes.SOURCE;
	}
	
	// implementation models trie nodes using integers, with additional
	// logic to byte-pack non-branching child sequences, further reducing memory
	// overhead. This implementation typically operates approximately half as
	// fast as #sourceForSpeed
	static TrieNodeSource forCompactness() {
		return PackedTrieNodes.SOURCE;
	}
	
	// An implementation similar to that of #forCompactness with further logic
	// that linearizes siblings during compaction to enable binary searching.
	// This typically doubles the speed of lookups over #forCompactness at the
	// expense of doubling the time for removals.

	static TrieNodeSource forCompactLookups() {
		return CompactTrieNodes.SOURCE;
	}
	
	TrieNodes newNodes(ByteOrder byteOrder, boolean counting, int capacityHint);
	
	TrieNodes copyNodes(TrieNodes nodes, boolean counting, int capacityHint);
}
