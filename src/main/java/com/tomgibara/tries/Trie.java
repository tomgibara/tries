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
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import com.tomgibara.fundament.Bijection;
import com.tomgibara.fundament.Mutability;
import com.tomgibara.streams.StreamSerializer;
import com.tomgibara.streams.WriteStream;
import com.tomgibara.tries.nodes.TrieNode;
import com.tomgibara.tries.nodes.TrieNodePath;
import com.tomgibara.tries.nodes.TrieNodeSource;
import com.tomgibara.tries.nodes.TrieNodes;

/**
 * <p>
 * A data structure that stores its elements in a byte based trie. Instances are
 * initially obtained from {@link Tries}.
 *
 * <p>
 * The trie, any sub-tries, and other views are all backed by the same nodes;
 * any concurrent access to these must be externally synchronized.
 *
 * <p>
 * Note that no specific notion of equality is not defined for Tries; two tries
 * are equal only if they are the same object. To make equality judgements based
 * on tries as collections of elements use {@link Trie#asSet()} or
 * {@link IndexedTrie#asList()}.
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

	private static final byte[] NO_PREFIX = {};

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
	 *
	 * @return true if the trie was modified; false indicates the trie was
	 * already empty
	 */

	public boolean clear() {
		TrieNodePath path;
		if (prefix.length == 0) {
			// empty prefix is a special case, because root node is allowed to dangle
			if (nodes.root().isDangling()) return false;
			serialization.reset();
			path = nodes.newPath(serialization);
		} else {
			// regular case, root node - if it exists - cannot already dangle
			path = nodes.newPath(serialization);
			serialization.set(prefix);
			if (!path.deserializeWithWalk()) return false; // root doesn't exist - trie must be empty
		}

		path.dangle();
		return true;
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
		return addSerialization();
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
		byte[] buffer = serialization.buffer();
		int length = serialization.length();
		TrieNode node = root();
		for (int i = prefix.length; i < length; i++) {
			node = node.findChild(buffer[i]);
			if (node == null) return false;
		}
		return node.isTerminal();
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
		TrieNodePath path = nodes.newPath(serialization);
		if (!path.deserializeWithWalk()) return false;
		boolean removed = path.terminate(false);
		if (removed) path.prune();
		return removed;
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
	 * contain all elements that are descendants of the specified root, and the
	 * root itself if it's an element of this trie. The returned sub-trie will
	 * be mutable if and only if this trie is mutable.
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
	 * any valid element.
	 *
	 * @param prefix
	 *            a byte sequence with which element serializations are required
	 *            to start
	 * @return a sub-trie containing of all matching elements
	 */

	public Trie<E> subTrieAtPrefix(byte[] prefix) {
		if (prefix == null) throw new IllegalArgumentException("null prefix");
		int capacity = Math.max(serialization.capacity(), prefix.length);
		TrieSerialization<E> s = serialization.resetCopy(capacity);
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
	 * Removes the first element of the trie, if it exists. The removed element
	 * is returned as an optional.
	 *
	 * @return the element removed, or empty if there was no first element
	 * @see #first()
	 */

	public Optional<E> removeFirst() {
		if (isEmpty()) return Optional.empty();
		TrieNodePath path = rootPath();
		while (!path.head().isTerminal() && path.walkChild());
		path.serialize();
		boolean removed = path.terminate(false);
		assert(removed);
		path.prune();
		return Optional.of( serialization.get() );
	}

	/**
	 * Optionally, the last element of the trie, or empty. If it exists, this
	 * is the element whose serialization comes last, with respect to the byte
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
	 * Removes the last element of the trie, if it exists. The removed element
	 * is returned as an optional.
	 *
	 * @return the element removed, or empty if there was no last element
	 * @see #last()
	 */

	public Optional<E> removeLast() {
		if (isEmpty()) return Optional.empty();
		TrieNodePath path = rootPath();
		while (path.walkLastChild());
		path.serialize();
		boolean removed = path.terminate(false);
		assert(removed);
		path.prune();
		return Optional.of( serialization.get() );
	}

	/**
	 * <p>
	 * The ancestors of the specified element within the trie. If the element is
	 * itself contained in the trie, it is not included in the list. The list
	 * may be empty, but is never null.
	 *
	 * <p>
	 * The first element of the returned list is the primogenitor of the
	 * supplied element, with its parent last in the list.
	 *
	 * @param e
	 *            a possible element of the trie
	 * @return the element's ancestors within the trie
	 */

	public Iterator<E> ancestors(E e) {
		checkSerializable(e);
		serialization.set(e);
		// if the trie prefix isn't a proper prefix of e, there can be no ancestors
		if (!serialization.startsWith(prefix) || serialization.length() == prefix.length) return Collections.emptyIterator();
		return new Iterator<E>() {

			TrieSerialization<E> serial = serialization.copy();
			TrieSerialization<E> acc = serial.copy();
			int index = prefix.length;
			TrieNode node = root(); // always points to a terminal node

			{
				acc.trim(index);
				if (node != null && !node.isTerminal()) advance();
			}

			@Override
			public boolean hasNext() {
				return node != null;
			}

			@Override
			public E next() {
				if (node == null) throw new NoSuchElementException();
				try {
					return acc.get();
				} finally {
					advance();
				}
			}

			private void advance() {
				do {
					if (index < serial.length() - 1) {
						byte b = serial.buffer()[index ++];
						acc.push(b);
						node = node.findChild(b);
					} else {
						node = null;
					}
				} while (node != null && !node.isTerminal());
			}

		};
	}

	/**
	 * Returns the specified element if it's contained in the trie, or the
	 * element's parent, or empty.
	 *
	 * @param e
	 *            a possible element of the trie
	 * @return the element, or its parent, or empty
	 */

	public Optional<E> parentOrSelf(E e) {
		checkSerializable(e);
		serialization.set(e);
		if (!serialization.startsWith(prefix)) return Optional.empty();

		byte[] buffer = serialization.buffer();
		int length = serialization.length();
		TrieNode node = root();
		int limit = node.isTerminal() ? prefix.length : -1;
		for (int i = prefix.length; i < length; i++) {
			node = node.findChild(buffer[i]);
			if (node == null) break;
			if (node.isTerminal()) limit = i + 1;
		}
		if (limit == -1) return Optional.empty();
		serialization.trim(limit);
		return Optional.of(serialization.get());
	}

	/**
	 * A comparator consistent with the element ordering in this trie. Each call
	 * to this method creates a new comparator. The comparators returned by this
	 * method must be externally synchronized when used by multiple threads.
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
	 * <p>
	 * Adapts the elements of the trie to produce a trie over a new type of
	 * value. The domain of the bijection must match the type of value
	 * serialized into the trie. The returned trie will contain values in the
	 * range of the bijection.
	 *
	 * <p>
	 * The returned object is a live view of this trie. mutations to either with
	 * will be reflected in the other. The returned trie will be mutable if and
	 * only if this trie is mutable.
	 *
	 * @param adapter
	 *            the bijective mapping that transforms the trie elements
	 * @param <F>
	 *            the new type
	 *
	 * @return a trie over the range of the adapter
	 * @see Tries#adaptedWith(Bijection)
	 */

	public <F> Trie<F> asAdaptedWith(Bijection<E, F> adapter) {
		if (adapter == null) throw new IllegalArgumentException("null adapter");
		TrieSerialization<E> s = serialization.resetCopy();
		s.set(prefix);
		return new Trie<>(s.adapt(adapter), nodes);
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

	public Iterator<E> iteratorFrom(E from) {
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
		serialization.set(prefix);
		TrieNodePath path = nodes.newPath(serialization);
		if (!path.deserializeWithWalk() || path.head().isDangling()) {
			path.reset();
			path.pop();
		}
		path.writeTo(stream);
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
		if (invalidations == latest) return root;
		invalidations = latest;
		root = prefix.length == 0 ? nodes.root() : findRoot(prefix, prefix.length);
		return root;
	}

	// overridden to allow indexed try to compute root index
	TrieNode findRoot(byte[] bytes, int length) {
		TrieNode node = nodes.root();
		for (int i = 0; i < length; i++) {
			node = node.findChild(bytes[i]);
			if (node == null) return null;
		}
		return node;
	}

	Trie<E> newTrie(TrieSerialization<E> s) {
		if (!s.startsWith(prefix)) throw new IllegalArgumentException("root not within trie");
		return new Trie<E>(s, nodes);
	}

	void checkSerializable(E e) {
		if (!serialization.isSerializable(e)) throw new IllegalArgumentException("invalid e");
	}

	void dump() { ((AbstractTrieNodes) nodes).dump(); }

	int availableCapacity() { return ((AbstractTrieNodes) nodes).availableCapacity(); }

	void check() {
		try {
			((PackedTrieNodes) nodes).check(root().getCount());
		} catch (IllegalStateException e) {
			System.err.println(e.getMessage());
			throw e;
		}
	}

	// private helper methods

	private boolean addSerialization() {
		TrieNodePath path = nodes.newPath(serialization);
		path.deserializeWithPush();
		return path.terminate(true);
	}

	private TrieNodePath rootPath() {
		TrieNodePath path = nodes.newPath(serialization);
		serialization.set(prefix);
		path.deserializeWithWalk();
		return path;
	}

	// inner classes

	class NodeIterator implements Iterator<E> {

		final TrieSerialization<E> serial = serialization.resetCopy();
		//TrieNode[] stack = new TrieNode[serial.capacity()];
		//TrieNode next;
		TrieNodePath path = nodes.newPath(serial);
		private E previous = null;
		private boolean removed = false;
		private long invalidations = nodes.invalidations();

		NodeIterator(E e) {
			if (isEmpty()) {
				path.pop();
			} else {
				if (e == null) {
					serial.set(prefix);
				} else {
					serial.set(e);
					if (!serial.startsWith(prefix)) throw new IllegalArgumentException("inital element not in sub-trie");
				}
				path.first(prefix.length);
				if (!path.isEmpty() && !path.head().isTerminal()) advance();
			}
		}

		NodeIterator() { }

		@Override
		public boolean hasNext() {
			checkInvalidations();
			return !path.isEmpty();
		}

		@Override
		public E next() {
			checkInvalidations();
			if (path.isEmpty()) throw new NoSuchElementException();
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
			path.reset();
			path.first(prefix.length);
			if (!path.isEmpty()) advance();
		}

		private void advance() {
			path.advance(prefix.length);
		}

		private void checkInvalidations() {
			long latest = nodes.invalidations();
			if (invalidations == latest) return;
			refresh();
			invalidations = latest;
		}

	}

	private class TrieSet extends AbstractSet<E> {

		@Override
		@SuppressWarnings("unchecked")
		public boolean contains(Object o) {
			return serialization.isSerializable(o) && Trie.this.contains((E) o);
		}

		@Override
		@SuppressWarnings("unchecked")
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
			return Trie.this.addSerialization();
		}

		@Override
		public Iterator<E> iterator() {
			return Trie.this.iterator();
		}

	}
}
