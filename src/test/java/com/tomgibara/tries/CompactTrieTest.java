package com.tomgibara.tries;

public class CompactTrieTest extends TrieTest {

	@Override
	protected TrieNodeSource getNodeSource() {
		return CompactTrieNodes.SOURCE;
	}
	
	
}
