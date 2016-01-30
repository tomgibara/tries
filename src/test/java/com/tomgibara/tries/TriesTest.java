package com.tomgibara.tries;

import static com.tomgibara.tries.TrieTest.bytes;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TriesTest {

	@Test
	public void testBytes() {
		Trie<byte[]> trie = Tries.bytes().newTrie();
		trie.add(bytes("boo"));
		trie.add(bytes("book"));
		trie.add(bytes("bookie"));
		
		assertTrue(trie.contains(bytes("boo")));
		assertTrue(trie.contains(bytes("book")));
		assertTrue(trie.contains(bytes("bookie")));
	}
	
}
