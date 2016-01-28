package com.tomgibara.tries;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

public class IndexedTrie<E> extends Trie<E> {

	// constructors
	
	IndexedTrie(Tries<E> tries, TrieNodes nodes) {
		super(tries, nodes);
	}
	
	IndexedTrie(TrieSerialization<E> serialization, TrieNodes nodes) {
		super(serialization, nodes);
	}
	
	IndexedTrie(IndexedTrie<E> trie, TrieNodes nodes) {
		super(trie, nodes);
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
	
	public int indexOf(E e) {
		checkSerializable(e);
		serialization.set(e);
		if (!serialization.startsWith(prefix)) throw new IllegalArgumentException("element not in sub-trie");
		return indexOf(serialization.buffer(), serialization.length());
	}
	
	public List<E> asList() {
		return new TrieList();
	}

	// package scoped methods
	
	@Override
	TrieNode findRoot(byte[] bytes, int length) {
		TrieNode node = nodes.root();
		for (int i = 0; i < length; i++) {
			byte value = bytes[i];
			node = node.findChild(value);
			if (node == null) break;
		}
		return node;

	}

	@Override
	IndexedTrie<E> newTrie(TrieSerialization<E> s) {
		return new IndexedTrie<E>(s, nodes);
	}

	// mutability methods
	
	@Override
	public IndexedTrie<E> immutableView() {
		return new IndexedTrie<E>(this, nodes.immutableView());
	}
	
	@Override
	public IndexedTrie<E> immutableCopy() {
		return new IndexedTrie<E>(this, nodes.immutableCopy());
	}
	
	@Override
	public IndexedTrie<E> mutableCopy() {
		return new IndexedTrie<E>(this, nodes.mutableCopy());
	}

	@Override
	public IndexedTrie<E> mutable() {
		return isMutable() ? this : mutableCopy();
	}
	
	@Override
	public IndexedTrie<E> immutable() {
		return isMutable() ? immutableCopy() : this;
	}
	
	// private utility methods
	
	private int indexOf(byte[] bytes, int length) {
		//TODO can we start from logical root?
		TrieNode node = root();
		int index = 0;
		for (int i = prefix.length; i < length; i++) {
			byte value = bytes[i];
			index += node.countToChild(value);
			node = node.findChild(value);
			if (node == null) return -1 - index;
		}
		return index;
	}

	// inner classes
	
	private class TrieList extends AbstractList<E> {

		@Override
		public E get(int index) {
			return IndexedTrie.this.get(index);
		}

		@Override
		public int size() {
			return IndexedTrie.this.size();
		}

		@Override
		public boolean isEmpty() {
			return IndexedTrie.this.isEmpty();
		}
		
		@Override
		public boolean contains(Object o) {
			return serialization.isSerializable(o) && IndexedTrie.this.contains((E) o);
		}
		
		@Override
		public int indexOf(Object o) {
			if (!serialization.isSerializable(o)) return -1;
			serialization.set((E) o);
			if (!serialization.startsWith(prefix)) return -1;
			int index = IndexedTrie.this.indexOf(serialization.buffer(), serialization.length());
			return index < 0 ? -1 : index;
		}
		
		@Override
		public int lastIndexOf(Object o) {
			return indexOf(o);
		}

		@Override
		public boolean remove(Object o) {
			if (!serialization.isSerializable(o)) return false;
			return IndexedTrie.this.remove((E) o);
		}

		@Override
		public E remove(int index) {
			//TODO
			throw new UnsupportedOperationException();
		}
		
		@Override
		public Iterator<E> iterator() {
			return IndexedTrie.this.iterator();
		}

		@Override
		public ListIterator<E> listIterator() {
			return new TrieIterator(0);
		}

		@Override
		public ListIterator<E> listIterator(int index) {
			return new TrieIterator(index);
		}

		@Override
		public void clear() {
			IndexedTrie.this.clear();
		}

	}
	
	private class TrieIterator extends NodeIterator implements ListIterator<E> {

		private int index;
		
		TrieIterator(int index) {
			this.index = index;
			sync(true);
		}
		
		@Override
		public E next() {
			E e = super.next();
			index ++;
			return e;
		}

		@Override
		public boolean hasPrevious() {
			return index > 0;
		}

		@Override
		public E previous() {
			if (index <= 0) throw new NoSuchElementException();
			index --;
			sync(true);
			return serial.get();
		}

		@Override
		public int nextIndex() {
			return index;
		}

		@Override
		public int previousIndex() {
			return index - 1;
		}

		@Override
		public void remove() {
			super.remove();
			index --;
		}

		@Override
		public void set(E e) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void add(E e) {
			throw new UnsupportedOperationException();
		}

		@Override
		void refresh() {
			sync(false);
		}
		
		private void sync(boolean strict) {
			if (index < 0) throw new IllegalArgumentException("negative index");
			next = root();
			int size = next == null ? 0 : next.getCount();
			if (index == size) {
				next = null;
				return;
			} else if (index > size) {
				if (strict) {
					index = size;
					next = null;
					return;
				} else {
					throw new IllegalArgumentException("index too large");
				}
			}
			int count = next.getCount();
			serial.set(prefix);

			int i = index;
			while (!next.isTerminal() || i != 0) {
				if (i < count) {
					if (next.isTerminal()) i--;
					next = next.getChild();
					serial.push(next.getValue());
					stack[serial.length() - 1] = next;
				} else {
					i -= count;
					next = next.getSibling();
					serial.replace(next.getValue());
					stack[serial.length() - 1] = next;
				}
				count = next.getCount();
			}
		}

	}
}
