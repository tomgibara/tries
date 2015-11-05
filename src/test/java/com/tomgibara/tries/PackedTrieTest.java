package com.tomgibara.tries;

public class PackedTrieTest extends TrieTest {

	@Override
	protected TrieNodeSource getNodeSource() {
		return PackedTrieNodes.SOURCE;
	}
	
	
}
