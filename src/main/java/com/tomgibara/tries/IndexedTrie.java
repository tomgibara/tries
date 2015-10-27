package com.tomgibara.tries;

import com.tomgibara.tries.Tries.Serialization;

public class IndexedTrie<E> extends Trie<E> {

	// fields
	
	//TODO is this needed?
	private int rootIndex = 0;

	// constructors
	
	IndexedTrie(Tries<E> tries, TrieNodes nodes) {
		super(tries, nodes);
	}
	
	IndexedTrie(Serialization<E> serialization, TrieNodes nodes) {
		super(serialization, nodes);
	}

	@Override
	public IndexedTrie<E> subTrie(E root) {
		return (IndexedTrie<E>) super.subTrie(root);
	}
	
	// trie methods
	
	public E get(int index) {
		if (index < 0) throw new IllegalArgumentException("negative index");
		TrieNode node = root();
		if (node == null || index >= node.getCount()) throw new IllegalArgumentException("index too large");
		int count = node.getCount();
		serialization.set(prefix);
		while (!node.isTerminal() || index != 0) {
			//System.out.println(index + " AT " + node + " WITH COUNT " + count);
			if (index < count) {
				if (node.isTerminal()) index--;
				node = node.getChild();
				serialization.push(node.getValue());
			} else {
				index -= count;
				node = node.getSibling();
				serialization.replace(node.getValue());
			}
			count = node.getCount();
		}
		return serialization.get();
	}

	// package scoped methods
	
	@Override
	//TODO pity we can't perform count and find at the same time
	TrieNode findRoot(byte[] bytes, int length) {
		TrieNode node = nodes.root();
		int index = 0;
		for (int i = 0; i < length; i++) {
			byte value = bytes[i];
			index += node.countTo(value);
			node = node.findChild(value);
			if (node == null) break;
		}
		// if (node != null && node.isTerminal()) index++;
		rootIndex = index;
		return node;

	}

	@Override
	IndexedTrie<E> newTrie(Serialization<E> s) {
		return new IndexedTrie<E>(s, nodes);
	}

}
