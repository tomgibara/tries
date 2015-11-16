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
		serialization = tries.serialProducer.produce();
		this.nodes = nodes;
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

	public int size() {
		TrieNode root = root();
		return root == null ? 0 : root.getCount();
	}
	
	public boolean isEmpty() {
		TrieNode root = root();
		return root == null ? true : root.isDangling();
	}
	
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
	
	public long storageSizeInBytes() {
		return nodes.storageSize();
	}
	
	public void compactStorage() {
		nodes.compact();
	}
	
	public boolean add(E e) {
		checkSerializable(e);
		serialization.set(e);
		if (!serialization.startsWith(prefix)) throw new IllegalArgumentException("element not in sub-trie");
		return add(serialization.buffer(), serialization.length());
	}
	
	public boolean contains(E e) {
		checkSerializable(e);
		serialization.set(e);
		if (!serialization.startsWith(prefix)) return false;
		return contains(serialization.buffer(), serialization.length());
	}
	
	public boolean remove(E e) {
		checkSerializable(e);
		serialization.set(e);
		if (!serialization.startsWith(prefix)) return false;
		return remove(serialization.buffer(), serialization.length());
	}
	
	public boolean addAll(Iterator<E> iterator) {
		if (iterator == null) throw new IllegalArgumentException("null iterator");
		boolean mutated = false;
		while (iterator.hasNext()) {
			E e = iterator.next();
			mutated = add(e) || mutated;
		}
		return mutated;
	}

	public boolean addAll(Iterable<E> iterable) {
		if (iterable == null) throw new IllegalArgumentException("null iterable");
		return addAll(iterable.iterator());
	}
	
	public boolean containsAll(Iterator<E> iterator) {
		//TODO could implement optimally by building eq
		if (iterator == null) throw new IllegalArgumentException("null iterator");
		while (iterator.hasNext()) {
			if (!contains(iterator.next())) return false;
		}
		return true;
	}

	public boolean containsAll(Iterable<E> iterable) {
		if (iterable == null) throw new IllegalArgumentException("null iterable");
		return containsAll(iterable.iterator());
	}

	public Trie<E> subTrie(E root) {
		if (root == null) throw new IllegalArgumentException("null root");
		TrieSerialization<E> s = serialization.resetCopy();
		s.set(root);
		if (!s.startsWith(prefix)) throw new IllegalArgumentException("new sub root not in sub-trie");
		return newTrie(s);
	}
	
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
		if (!node.isTerminal()) throw new IllegalStateException();
		return Optional.of(serialization.get());
	}

	// a comparator consistent with the trie ordering
	// each call creates a new comparator, comparator is not threadsafe
	public Comparator<E> comparator() {
		return serialization.comparator(nodes.byteOrder());
	}

	// can be used to generate a hasher from a hash
	public StreamSerializer<E> serializer() {
		TrieSerialization<E> ts = serialization.resetCopy();
		return (t,s) -> {
			ts.set(t);
			s.writeBytes(ts.buffer(), 0, ts.length());
		};
	}
	
	public Set<E> asSet() {
		return new TrieSet();
	}
	
	// iterable methods
	
	public Iterator<E> iterator() {
		return new NodeIterator(null);
	}
	
	public Iterator<E> iterator(E from) {
		if (from == null) throw new IllegalArgumentException("null from");
		return new NodeIterator(from);
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

	// overridden to allow indexed try to compute root index
	TrieNode findRoot(byte[] bytes, int length) {
		return find(bytes, length);
	}

	Trie<E> newTrie(TrieSerialization<E> s) {
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
		TrieNode[] ancestors = new TrieNode[length];
		TrieNode[] stack = new TrieNode[length];
		TrieNode root = nodes.root();
		// locate the target node, recording intermediate nodes (stack) and the nodes that refer to them (referrers)
		if (nodes.populate(root, bytes, length, stack, ancestors) < length) return false;
		if (length == 0) { // treat the root node as a special case
			if (!root.isTerminal()) return false;
			nodes.decCounts(stack, length);
			root.setTerminal(false);
		} else {
			int i = length - 1;
			TrieNode node = stack[i];
			if (!node.isTerminal()) return false;
			nodes.decCounts(stack, length);
			node.setTerminal(false);
			if (node.hasChild()) {
				// we do nothing because our tree can have no dangling nodes (except possibly the root)
				// so if the node has a child, there must be terminations further along the tree
			} else {
				// walk backwards looking for the first referrer we must preserve
				// and then we remove the ancestor node pointed by that referrer
				// since it's clear that it must be the last node we can lose
				for (; i >= 0; i--) {
					node = stack[i];
					TrieNode ancestor = ancestors[i];
					// work out if we can must preserve this referrer
					// ("remove" here indicates removal of the referrent)
					boolean isSibling = ancestor.isSibling(node);
					boolean branched = isSibling ? ancestor.hasChild() : node.hasSibling();
					boolean remove =
							i == 0 || // we've reached the root, must stop
							ancestor.isTerminal() || // we've reached a terminal node, must stop here
							branched; // ancestor has an additional branch to preserve
					if (remove) {
						ancestor.remove(node);
						break;
					}
				}
				// finally, delete any detatched nodes
				for (int j = length - 1; j >= i ; j--) stack[j].delete();
			}
		}
		return true;
	}

	private int walk(byte[] bytes, int length, TrieNode[] stack) {
		TrieNode node = nodes.root();
		for (int i = 0; i < length; i++) {
			node = node.findChild(bytes[i]);
			if (node == null) return i;
			stack[i] = node;
		}
		return length;
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
