package com.tomgibara.tries;

abstract class AbstractTrieNodes implements TrieNodes {

	public abstract AbstractTrieNode root();

	abstract void dump();

	abstract void adopt(AbstractTrieNode ours, TrieNode theirs);
	
	// called when reading has been finished
	abstract void readComplete();
}
