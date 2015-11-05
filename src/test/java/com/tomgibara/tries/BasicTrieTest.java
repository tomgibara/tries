package com.tomgibara.tries;

public class BasicTrieTest extends TrieTest {

	@Override
	protected TrieNodeSource getNodeSource() {
		return BasicTrieNodes.SOURCE;
	}

}
