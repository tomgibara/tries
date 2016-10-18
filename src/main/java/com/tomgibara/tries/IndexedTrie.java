/*
 * Copyright 2015 Tom Gibara
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.tomgibara.tries;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import com.tomgibara.fundament.Bijection;
import com.tomgibara.tries.nodes.TrieNode;
import com.tomgibara.tries.nodes.TrieNodePath;
import com.tomgibara.tries.nodes.TrieNodes;

/**
 * <p>
 * A trie that can efficiently return the index of any element. Elements are
 * ordered by the comparator associated with the trie; all valid indices are
 * positive and less than the size of the trie; the first element of the trie
 * has an index of zero. Instances are initially obtained from
 * {@link IndexedTries}.
 * 
 * <p>
 * The trie, any sub-tries, and other views are all backed by the same nodes;
 * any concurrent access to these must be externally synchronized.
 * 
 * @author Tom Gibara
 *
 * @param <E>
 *            the type of element stored in the trie
 * @see IndexedTries
 */

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

	@Override
	public IndexedTrie<E> subTrieAtPrefix(byte[] prefix) {
		return (IndexedTrie<E>) super.subTrieAtPrefix(prefix);
	}

	// trie methods
	
	/**
	 * The element at the specified index. 
	 * 
	 * @param index a valid index for the trie
	 * @return the element at the index
	 */

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
	
	/**
	 * Removes the element at the specified index.
	 * 
	 * @param index a valid index for the trie
	 * @return the removed element
	 */

	public E remove(int index) {
		if (index < 0) throw new IllegalArgumentException("negative index");
		serialization.set(prefix);
		TrieNodePath path = nodes.newPath(serialization);
		if (!path.deserializeWithWalk() || index >= path.head().getCount()) throw new IllegalArgumentException("index too large"); // ie. root doesn't exist, and nor does any node below it, or there aren't enough children
		if (!path.walkCount(index)) return null;
		path.serialize();
		boolean removed = path.terminate(false);
		assert(removed);
		path.prune();
		return serialization.get();
	}

	/**
	 * The index of the given element. If the element does not exist in the trie
	 * then the value <code>-1-n</code> is returned where <code>n</code> is the
	 * index that the element would occupy if it was added to to the trie.
	 * 
	 * @param e
	 *            an valid element for the trie
	 * @return the index of the element, or a negative value indicating the
	 *         index it would occupy
	 */

	public int indexOf(E e) {
		checkSerializable(e);
		serialization.set(e);
		if (!serialization.startsWith(prefix)) throw new IllegalArgumentException("element not in sub-trie");
		return indexOf(serialization.buffer(), serialization.length());
	}

	/**
	 * The trie as a list. The list does not support adding elements, but does
	 * supports removal if the trie is mutable. The returned object is a live
	 * view of this trie. mutations to either will be reflected in the other.
	 * 
	 * @return the trie as a list
	 */

	public List<E> asList() {
		return new TrieList();
	}

	@Override
	public <F> IndexedTrie<F> asAdaptedWith(Bijection<E, F> adapter) {
		if (adapter == null) throw new IllegalArgumentException("null adapter");
		TrieSerialization<E> s = serialization.resetCopy();
		s.set(prefix);
		return new IndexedTrie<>(s.adapt(adapter), nodes);
	}

	@Override
	public IndexedTrie<byte[]> asBytesTrie() {
		return new IndexedTrie<byte[]>(Tries.newByteSerialization(serialization.buffer().length), nodes.immutableView());
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
		return node.isTerminal() ? index : -1 - index;
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
		@SuppressWarnings("unchecked")
		public boolean contains(Object o) {
			return serialization.isSerializable(o) && IndexedTrie.this.contains((E) o);
		}
		
		@Override
		@SuppressWarnings("unchecked")
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
		@SuppressWarnings("unchecked")
		public boolean remove(Object o) {
			if (!serialization.isSerializable(o)) return false;
			return IndexedTrie.this.remove((E) o);
		}

		@Override
		public E remove(int index) {
			return IndexedTrie.this.remove(index);
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
			path.reset();
			TrieNode root = root();
			int size = root == null ? 0 : root.getCount();
			if (index == size) {
				path.pop();
				return;
			} else if (index > size) {
				if (strict) {
					index = size;
					path.pop();
					return;
				} else {
					throw new IllegalArgumentException("index too large");
				}
			}
			serial.set(prefix);
			path.deserializeWithWalk();
			path.walkCount(index);
			path.serialize();
		}

	}
}
