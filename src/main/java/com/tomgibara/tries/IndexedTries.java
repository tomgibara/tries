/*
 * Copyright 2016 Tom Gibara
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

import java.util.Comparator;

import com.tomgibara.fundament.Producer;
import com.tomgibara.streams.ReadStream;

/**
 * A class for creating {@link IndexedTrie} instances. Instances of this class
 * are obtained via the {@link Tries#indexed()} and
 * {@link Tries#indexed(boolean)} methods. It has the same characteristics
 * as the {@link Tries} class.
 * 
 * @author Tom Gibara
 *
 * @param <E>
 *            the type of elements to be stored
 * @see Tries#indexed()
 * @see Tries#indexed(boolean)
 */

public class IndexedTries<E> extends Tries<E> {

	IndexedTries(
			Producer<TrieSerialization<E>> serialProducer,
			ByteOrder byteOrder,
			TrieNodeSource nodeSource,
			int capacityHint) {
		super(serialProducer, byteOrder, nodeSource, capacityHint);
	}

	/**
	 * An instance of this class, with the same configuration, that creates
	 * unindexed tries.
	 * 
	 * @return tries with non-indexed elements
	 */

	public Tries<E> unindexed() {
		return new Tries<>(serialProducer, byteOrder, nodeSource, capacityHint);
	}

	@Override
	public IndexedTries<E> indexed() {
		return this;
	}
	
	@Override
	public Tries<E> indexed(boolean indexed) {
		return indexed ? this : unindexed();
	}
	
	// mutation methods

	@Override
	public IndexedTries<E> byteOrder(Comparator<Byte> comparator) {
		return new IndexedTries<>(serialProducer, ByteOrder.from(comparator), nodeSource, capacityHint);
	}

	@Override
	public IndexedTries<E> byteOrder(ByteOrder byteOrder) {
		if (byteOrder == null) throw new IllegalArgumentException("null byteOrder");
		return new IndexedTries<>(serialProducer, byteOrder, nodeSource, capacityHint);
	}
	
	@Override
	public IndexedTries<E> nodeSource(TrieNodeSource nodeSource) {
		if (nodeSource == null) throw new IllegalArgumentException("null nodeSource");
		return new IndexedTries<>(serialProducer, byteOrder, nodeSource, capacityHint);
	}
	
	@Override
	public IndexedTries<E> capacityHint(int capacityHint) {
		if (capacityHint < 0) throw new IllegalArgumentException("negative capacityHint");
		return new IndexedTries<>(serialProducer, byteOrder, nodeSource, capacityHint);
	}

	// creation methods
	
	@Override
	public IndexedTrie<E> newTrie() {
		return new IndexedTrie<>(this, newNodes());
	}

	@Override
	public IndexedTrie<E> copyTrie(Trie<E> trie) {
		if (trie.nodes.byteOrder().equals(byteOrder)) {
			// fast path - we can adopt the nodes, they're in the right order
			TrieNodes nodes = nodeSource.copyNodes(trie.nodes, true, capacityHint);
			return new IndexedTrie<>(this, nodes);
		} else {
			// slow path - just treat it as an add-all
			IndexedTrie<E> newTrie = new IndexedTrie<>(this, newNodes());
			newTrie.addAll(trie);
			return newTrie;
		}
	}
	
	@Override
	public IndexedTrie<E> readTrie(ReadStream stream) {
		return new IndexedTrie<>(this, nodeSource.deserializer(byteOrder, true, capacityHint).deserialize(stream));
	}

	// private utility methods
	
	private TrieNodes newNodes() {
		return nodeSource.newNodes(byteOrder, true, capacityHint);
	}
	

}
