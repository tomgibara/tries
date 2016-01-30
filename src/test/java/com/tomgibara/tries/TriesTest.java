package com.tomgibara.tries;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TriesTest {

	private static byte[] bytes(String str) {
		return str.getBytes(TrieTest.UTF8);
	}
	
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
