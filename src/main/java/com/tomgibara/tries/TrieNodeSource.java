package com.tomgibara.tries;

public interface TrieNodeSource {

	TrieNodes newNodes(ByteOrder byteOrder, boolean counting, int capacityHint);
	
}
