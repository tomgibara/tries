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

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import com.tomgibara.fundament.Mutability;
import com.tomgibara.streams.StreamSerializer;
import com.tomgibara.streams.WriteStream;

/**
 * <p>
 * A data structure that stores its elements in a byte based trie. Instances are
 * initially obtained from {@link Tries}.
 *
 * <p>
 * The trie, any sub-tries, and other views are all backed by the same nodes;
 * any concurrent access to these must be externally synchronized.
 *
 * @author Tom Gibara
 *
 * @param <E>
 *            the type of element stored in the trie
 *
 * @see <a href="https://en.wikipedia.org/wiki/Trie">Trie in Wikipedia</a>
 * @see Tries
 */

public class Trie<E> implements Iterable<E>, Mutability<Trie<E>> {

	// statics
	
	private final byte[] NO_PREFIX = {};
	
	private static byte[] toPrefix(TrieSerialization<?> s) {
		return Arrays.copyOf(s.buffer(), s.length());
	}

	// fields

	final TrieNodes nodes;
	final TrieSerialization<E> serialization;
	final byte[] prefix;
	private TrieNode root;
	private long invalidations;
	
	// constructors
	
	Trie(Tries<E> tries, TrieNodes nodes) {
		this.nodes = nodes;
		serialization = tries.serialProducer.produce();
		prefix = NO_PREFIX;
		root = nodes.root();
		invalidations = nodes.invalidations();
	}
	
	Trie(TrieSerialization<E> serialization, TrieNodes nodes) {
		this.serialization = serialization;
		this.nodes = nodes;
		prefix = toPrefix(serialization);
		invalidations = -1L; // to force computation of root
	}

	Trie(Trie<E> trie, TrieNodes nodes) {
		this.nodes = nodes;
		this.serialization = trie.serialization.resetCopy();
		this.prefix = trie.prefix;
		invalidations = -1L; // to force computation of root
	}
	
	// trie methods

	/**
	 * The number of elements in the trie.
	 * 
	 * @return the number of elements
	 */
	
	public int size() {
		TrieNode root = root();
		return root == null ? 0 : root.getCount();
	}
	
	/**
	 * True if and only if the size is zero.
	 * 
	 * @return whether the trie contains no elements
	 * @see #size()
	 */
	
	public boolean isEmpty() {
		TrieNode root = root();
		return root == null ? true : root.isDangling();
	}

	/**
	 * Removes all elements from the trie.
	 */

	public void clear() {
		if (prefix.length == 0) {
			nodes.clear();
		} else {
			//TODO optimize
			for (Iterator<E> i = iterator(); i.hasNext(); i.remove()) i.next();
			// ensure prefix is terminal
			// then repeatedly remove child and eradicate it
			// finally reset terminal status
		}
	}
	
	/**
	 * An estimate of number of bytes used to store the trie elements.
	 * 
	 * @return the storage size in bytes
	 */

	public long storageSizeInBytes() {
		return nodes.storageSize();
	}

	/**
	 * Compacts the node storage backing the trie. Depending on the
	 * implementation of the trie nodes, this action may do nothing. In some
	 * implementations memory usage may be reduced and/or performance may be
	 * improve.
	 * 
	 * @see Tries#nodeSource(TrieNodeSource)
	 */

	public void compactStorage() {
		nodes.compact();
	}
	
	/**
	 * Adds an element to trie. The supplied object must be serializable by the
	 * serializer of this trie.
	 * 
	 * @param e
	 *            the element to add to the trie
	 * @return true if the element was added to the trie, false if trie already
	 *         contained the element
	 */

	public boolean add(E e) {
		checkSerializable(e);
		serialization.set(e);
		if (!serialization.startsWith(prefix)) throw new IllegalArgumentException("element not in sub-trie");
		return add(serialization.buffer(), serialization.length());
	}

	/**
	 * Tests whether an element is contained by this trie. The supplied object
	 * must be serializable by the serializer of this trie.
	 * 
	 * @param e
	 *            a possible element
	 * @return true
	 */

	public boolean contains(E e) {
		checkSerializable(e);
		serialization.set(e);
		if (!serialization.startsWith(prefix)) return false;
		return contains(serialization.buffer(), serialization.length());
	}
	
	/**
	 * Removes an element from a trie. The supplied object must be serializable
	 * by the serializer of this trie.
	 * 
	 * @param e
	 *            the element to remove from the trie
	 * @return true if the element was removed from the trie, false if the
	 *         element was not contained in the trie
	 */

	public boolean remove(E e) {
		checkSerializable(e);
		serialization.set(e);
		if (!serialization.startsWith(prefix)) return false;
		return remove(serialization.buffer(), serialization.length());
	}

	/**
	 * Adds all the of the items returned by an iterator. All of the items must
	 * be serializable by the serializer of this trie.
	 * 
	 * @param iterator
	 *            iterates over the items to be added to the trie
	 * @return true if any of the iterator's items were added to the trie, false
	 *         otherwise.
	 */

	public boolean addAll(Iterator<E> iterator) {
		if (iterator == null) throw new IllegalArgumentException("null iterator");
		boolean mutated = false;
		while (iterator.hasNext()) {
			E e = iterator.next();
			mutated = add(e) || mutated;
		}
		return mutated;
	}

	/**
	 * Adds all the of the items of an iterable. All of the items must be
	 * serializable by the serializer of this trie.
	 * 
	 * @param iterable
	 *            provides an iterator over the items to be added to the trie
	 * @return true if any of the iterable's items were added to the trie, false
	 *         otherwise.
	 */

	public boolean addAll(Iterable<E> iterable) {
		if (iterable == null) throw new IllegalArgumentException("null iterable");
		return addAll(iterable.iterator());
	}
	
	/**
	 * Removes all the of the items returned by an iterator. All of the items
	 * must be serializable by the serializer of this trie.
	 * 
	 * @param iterator
	 *            iterates over the items to be removed from the trie
	 * @return true if any of the iterator's items were previously contained in
	 *         the trie, false otherwise.
	 */

	public boolean removeAll(Iterator<E> iterator) {
		if (iterator == null) throw new IllegalArgumentException("null iterator");
		boolean mutated = false;
		while (iterator.hasNext()) {
			E e = iterator.next();
			mutated = remove(e) || mutated;
		}
		return mutated;
	}

	/**
	 * Removes all the of the items of an iterable. All of the items must be
	 * serializable by the serializer of this trie.
	 * 
	 * @param iterable
	 *            provides an iterator over the items to be removed from the
	 *            trie
	 * @return true if any of the iterable's items were previously contained in
	 *         the trie, false otherwise.
	 */

	public boolean removeAll(Iterable<E> iterable) {
		if (iterable == null) throw new IllegalArgumentException("null iterable");
		return removeAll(iterable.iterator());
	}

	/**
	 * Tests whether the trie contains all of the items returned by an iterator.
	 * 
	 * @param iterator
	 *            an iterator over the items to be tested
	 * 
	 * @return true if the trie contains all of the items, false otherwise
	 */

	public boolean containsAll(Iterator<E> iterator) {
		//TODO could implement optimally by building eq
		if (iterator == null) throw new IllegalArgumentException("null iterator");
		while (iterator.hasNext()) {
			if (!contains(iterator.next())) return false;
		}
		return true;
	}

	/**
	 * Tests whether the trie contains all of the items contained in an
	 * iterable.
	 * 
	 * @param iterable
	 *            contains the items to be tested
	 * 
	 * @return true if the trie contains all of the items, false otherwise
	 */
	
	public boolean containsAll(Iterable<E> iterable) {
		if (iterable == null) throw new IllegalArgumentException("null iterable");
		return containsAll(iterable.iterator());
	}

	/**
	 * <p>
	 * Returns a sub-trie that is rooted at the given element. The sub-trie will
	 * contain all elements that are ancestors of the root, or the root itself.
	 * The sub-trie will be mutable if and only if this trie is mutable.
	 * 
	 * <p>
	 * Note that the root must be a valid element for the trie, but it does not
	 * necessarily need to be an element of the trie.
	 * 
	 * @param root
	 *            the root of the trie
	 * @return a sub-trie
	 */

	public Trie<E> subTrie(E root) {
		if (root == null) throw new IllegalArgumentException("null root");
		TrieSerialization<E> s = serialization.resetCopy();
		s.set(root);
		return newTrie(s);
	}

	/**
	 * <p>
	 * Returns a sub-trie that is restricted to all elements whose serialization
	 * starts with the given prefix. The sub-trie will be mutable if and only if
	 * this trie is mutable.
	 * 
	 * <p>
	 * Note that the prefix is not required to be a complete serialization of
	 * any valid element; it's only required that the prefix itself is valid for
	 * this trie.
	 * 
	 * @param prefix
	 *            a byte sequence with which element serializations are required
	 *            to start
	 * @return a sub-trie containing of all matching elements
	 */

	public Trie<E> subTrieAtPrefix(byte[] prefix) {
		if (prefix == null) throw new IllegalArgumentException("null prefix");
		TrieSerialization<E> s = serialization.resetCopy();
		s.set(prefix);
		return newTrie(s);
	}

	/**
	 * Optionally, the first element of the trie, or empty. If it exists, this
	 * is the element whose serialization comes first, with respect to the byte
	 * order defined for the trie, or equivalently, the least element of the
	 * induced comparator.
	 * 
	 * @return optionally, the first element of the trie
	 * @see #comparator()
	 */

	public Optional<E> first() {
		if (isEmpty()) return Optional.empty();
		serialization.set(prefix);
		TrieNode node = root();
		while (!node.isTerminal()) {
			node = node.getChild();
			serialization.push(node.getValue());
		}
		return Optional.of(serialization.get());
	}
	
	/**
	 * Optionally, the first element of the trie, or empty. If it exists, this
	 * is the element whose serialization comes first, with respect to the byte
	 * order defined for the trie, or equivalently, the greatest element of the
	 * induced comparator.
	 * 
	 * @return optionally, the last element of the trie
	 * @see #comparator()
	 */

	public Optional<E> last() {
		if (isEmpty()) return Optional.empty();
		serialization.set(prefix);
		TrieNode node = root();
		while (true) {
			TrieNode child = node.getLastChild();
			if (child == null) break; // node should be terminal
			node = child;
			serialization.push(node.getValue());
		}
		assert(node.isTerminal());
		return Optional.of(serialization.get());
	}

	/**
	 * A comparator consistent with the element ordering in this trie. Each call
	 * to this method creates a new comparator that is not threadsafe.
	 * 
	 * @return a comparator giving the element order applied by the trie
	 */

	public Comparator<E> comparator() {
		return serialization.comparator(nodes.byteOrder());
	}

	/**
	 * Provides a stream serializer for the valid elements of this trie. Each
	 * call to this method creates a new serializer that is not threadsafe. This
	 * may be used for, among other things, defining hashes consistent with
	 * the element equality implied by this trie.
	 * 
	 * @return a stream serializer for the trie
	 */

	public StreamSerializer<E> serializer() {
		TrieSerialization<E> ts = serialization.resetCopy();
		return (t,s) -> {
			ts.set(t);
			s.writeBytes(ts.buffer(), 0, ts.length());
		};
	}
	
	/**
	 * Exposes a the trie elements as a set. The returned object is a live view
	 * of this trie. mutations to either with will be reflected in the other.
	 * The returned set will be mutable if and only if the trie is mutable.
	 * 
	 * @return the trie as a set
	 */

	public Set<E> asSet() {
		return new TrieSet();
	}
	
	/**
	 * Exposes the underlying serializations of the contained elements as a trie
	 * of byte arrays. The returned trie is immutable, but provides a live view
	 * of this trie; any modifications made to this trie will be reflected in
	 * the returned trie.
	 * 
	 * @return the underlying element serializations as a byte array trie
	 */

	public Trie<byte[]> asBytesTrie() {
		return new Trie<byte[]>(Tries.newByteSerialization(serialization.buffer().length), nodes.immutableView());
	}

	/**
	 * An iterator over all of the elements in the trie. Elements are returned
	 * in a stable order as per the trie's associated comparator.
	 * 
	 * @return an iterator over the elemets of the trie
	 * @see #comparator()
	 */

	public Iterator<E> iterator() {
		return new NodeIterator(null);
	}
	
	/**
	 * An iterator over the elements of the trie beginning with the first
	 * element that that is greater than or equal to the given element under the
	 * trie's comparator.
	 * 
	 * @param from
	 *            the element less than which all elements are skipped.
	 * @return an iterator over all matching elements
	 * @see #comparator()
	 */

	public Iterator<E> iterator(E from) {
		if (from == null) throw new IllegalArgumentException("null from");
		return new NodeIterator(from);
	}

	/**
	 * Serializes the elements stored in the trie to a stream that may be
	 * deserialized with
	 * {@link Tries#readTrie(com.tomgibara.streams.ReadStream)}.
	 * 
	 * @param stream
	 *            the stream to which the trie elements are to be written
	 */

	public void writeTo(WriteStream stream) {
		if (stream == null) throw new IllegalArgumentException("null stream");
		root().writeNodes(stream);
	}
	
	// mutability methods
	
	@Override
	public boolean isMutable() {
		return nodes.isMutable();
	}

	@Override
	public Trie<E> immutableView() {
		return new Trie<E>(this, nodes.immutableView());
	}
	
	@Override
	public Trie<E> immutableCopy() {
		return new Trie<E>(this, nodes.immutableCopy());
	}
	
	@Override
	public Trie<E> mutableCopy() {
		return new Trie<E>(this, nodes.mutableCopy());
	}
	
	// object methods
	
	public String toString() {
		return asSet().toString();
	}
	
	// package scoped methods
	
	TrieNode root() {
		long latest = nodes.invalidations();
		if (prefix.length == 0 || invalidations == latest) return root;
		invalidations = latest;
		root = findRoot(prefix, prefix.length);
		return root;
	}
	
	int stackToRoot(TrieNode[] stack) {
		byte[] bytes = prefix;
		int length = prefix.length;
		TrieNode node = nodes.root();
		for (int i = 0; i < length; i++) {
			node = node.findChild(bytes[i]);
			if (node == null) return i;
			stack[i] = node;
		}
		return length;
	}

	// overridden to allow indexed try to compute root index
	TrieNode findRoot(byte[] bytes, int length) {
		return find(bytes, length);
	}

	Trie<E> newTrie(TrieSerialization<E> s) {
		if (!s.startsWith(prefix)) throw new IllegalArgumentException("root not within trie");
		return new Trie<E>(s, nodes);
	}
	
	void checkSerializable(E e) {
		if (!serialization.isSerializable(e)) throw new IllegalArgumentException("invalid e");
	}

	void dump() { ((AbstractTrieNodes) nodes).dump(); }
	
	void check() {
		try {
			((PackedTrieNodes) nodes).check(root().getCount());
		} catch (IllegalStateException e) {
			System.err.println(e.getMessage());
			throw e;
		}
	}
	
	boolean doRemove(TrieNode[] stack, int length) {
		if (length == 0) {
			TrieNode root = nodes.root();
			if (!root.isTerminal()) return false;
			nodes.decCounts(stack, length);
			root.setTerminal(false);
			return true;
		}
		TrieNode node = stack[length - 1];
		if (!node.isTerminal()) return false; // not present
		nodes.decCounts(stack, length);
		node.setTerminal(false);
		// we do no pruning if the node has a child
		// because our tree can have no dangling nodes (except possibly the root)
		// so if the node has a child, there must be terminations further along the tree
		if (!node.hasChild()) {
			int i = length - 2;
			TrieNode child = node;
			TrieNode parent = null;
			for (; i >= 0; i--) {
				parent = stack[i];
				if (parent.isTerminal() || parent.getChild().hasSibling()) break;
				child = parent;
			}
			if (i < 0) parent = nodes.root();
			boolean removed = parent.removeChild(child);
			assert(removed); // we know the child is there
			// finally, delete any detached nodes
			for (int j = length - 1; j > i ; j--) stack[j].delete();
		}
		return true;
	}
	
	// private helper methods
	
	private boolean add(byte[] bytes, int length) {
		nodes.ensureExtraCapacity(length);
		//TODO could optimize by maintaining stack, not just root?
		TrieNode root = nodes.root();
		TrieNode node = root;
		TrieNode[] stack = new TrieNode[length];
		for (int i = 0; i < length; i++) {
			stack[i] = node = node.findOrInsertChild(bytes[i]);
		}
		if (node.isTerminal()) return false; // already present
		node.setTerminal(true);
		nodes.incCounts(stack, length);
		return true;
	}

	private TrieNode find(byte[] bytes, int length) {
		TrieNode node = nodes.root();
		for (int i = 0; i < length; i++) {
			node = node.findChild(bytes[i]);
			if (node == null) return null;
		}
		return node;
	}

	private boolean contains(byte[] bytes, int length) {
		TrieNode node  = find(bytes, length);
		return node == null ? false : node.isTerminal();
	}

	private boolean remove(byte[] bytes, int length) {
		nodes.ensureExtraCapacity(1);
		TrieNode[] stack = new TrieNode[length];
		if (length > 0) {
			TrieNode node = nodes.root();
			for (int i = 0; i < length; i++) {
				node = node.findChild(bytes[i]);
				if (node == null) return false;
				stack[i] = node;
			}
		}
		return doRemove(stack, length);
	}
	
	// inner classes
	
	class NodeIterator implements Iterator<E> {

		final TrieSerialization<E> serial = serialization.resetCopy();
		TrieNode[] stack = new TrieNode[serial.buffer().length];
		TrieNode next;
		private E previous = null;
		private boolean removed = false;
		private long invalidations = nodes.invalidations();

		NodeIterator(E e) {
			if (isEmpty()) {
				next = null;
			} else {
				if (e == null) {
					serial.set(prefix);
				} else {
					serial.set(e);
					if (!serial.startsWith(prefix)) throw new IllegalArgumentException("inital element not in sub-trie");
				}
				sync();
				if (next != null && !next.isTerminal()) advance();
			}
		}

		NodeIterator() { }

		@Override
		public boolean hasNext() {
			checkInvalidations();
			return next != null;
		}

		@Override
		public E next() {
			checkInvalidations();
			if (next == null) throw new NoSuchElementException();
			previous = serial.get();
			removed = false;
			advance();
			return previous;
		}
		
		@Override
		public void remove() {
			if (removed) throw new IllegalStateException("element already removed");
			if (previous == null) throw new IllegalStateException("no previous element");
			Trie.this.remove(previous);
			removed = true;
		}

		void refresh() {
			if (previous == null) {
				serial.reset();
			} else {
				serial.set(previous);
			}
			sync();
			if (next != null) advance();
		}
		
		private void advance() {
			int length = serial.length();
			outer: do {
				if (next.hasChild()) {
					next = next.getChild();
					serial.push(next.getValue());
					stack[length++] = next;
					continue outer;
				}
				while (length > 0) {
					if (next.hasSibling()) {
						next = next.getSibling();
						serial.replace(next.getValue());
						stack[length - 1] = next;
						continue outer;
					}
					serial.pop();
					length--;
					if (length == prefix.length) {
						next = null;
						return;
					}
					next = stack[length - 1];
				}
			} while (!next.isTerminal());
		}

		private void checkInvalidations() {
			long latest = nodes.invalidations();
			if (invalidations == latest) return;
			refresh();
			invalidations = latest;
		}
		
		private void sync() {
			byte[] bytes = Arrays.copyOf(serial.buffer(), serial.length());
			serial.reset();
			
			next = nodes.root();
			for (int i = 0; i < bytes.length; i++) {
				byte value = bytes[i];
				next = next.findChildOrNext(value);
				if (next == null) {
					for (;i > 0; i--) {
						next = stack[i - 1];
						next = stack[i - 1] = next.getSibling();
						if (next != null) {
							serial.replace(next.getValue());
							return;
						}
						serial.pop();
					}
					return;
				}
				byte v = next.getValue();
				serial.push(v);
				stack[i] = next;
				if (v != value) return;
			}
		}
		
	}

	private class TrieSet extends AbstractSet<E> {
		
		@Override
		public boolean contains(Object o) {
			return serialization.isSerializable(o) && Trie.this.contains((E) o);
		}
		
		@Override
		public boolean remove(Object o) {
			return serialization.isSerializable(o) && Trie.this.remove((E) o);
		}

		@Override
		public int size() {
			return Trie.this.size();
		}

		@Override
		public void clear() {
			Trie.this.clear();
		}

		@Override
		public boolean isEmpty() {
			return Trie.this.isEmpty();
		}

		@Override
		public boolean add(E e) {
			if (!serialization.isSerializable(e)) return false;
			serialization.set(e);
			if (!serialization.startsWith(prefix)) return false;
			return Trie.this.add(serialization.buffer(), serialization.length());
		}

		@Override
		public Iterator<E> iterator() {
			return Trie.this.iterator();
		}

	}
}
