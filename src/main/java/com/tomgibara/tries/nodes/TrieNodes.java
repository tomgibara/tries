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
package com.tomgibara.tries.nodes;

import com.tomgibara.fundament.Mutability;
import com.tomgibara.tries.ByteOrder;
import com.tomgibara.tries.IndexedTrie;
import com.tomgibara.tries.Trie;

/**
 * <p>
 * A tree of {@link TrieNode} instances. The specific implementation of this
 * interface has a significant impact on the performance characteristics of any
 * {@link Trie} that uses it.
 * 
 * <p>
 * All trees operate with a fixed byte ordering which is reported by the
 * {@link #byteOrder()} method and imposes an ordering on sibling nodes. Trees
 * also support mutability controls as per the <code>Mutability</code>
 * interface.
 * 
 * <p>
 * Some tree implementations may support recording the number of terminating
 * children at each node. This is required to support the functionality exposed
 * by {@link IndexedTrie}.
 * 
 * <p>
 * Some tree implementations may invalidate nodes in response to structural
 * changes. This is managed via the {@link #ensureExtraCapacity(int)} and
 * {@link #invalidations()} methods.
 * 
 * @author Tom Gibara
 * @see TrieNodeSource
 */

public interface TrieNodes extends Mutability<TrieNodes> {

	/**
	 * The byte order that specifies the ordering of child nodes.
	 * 
	 * @return the byte order for this tree of nodes.
	 */

	ByteOrder byteOrder();
	
	/**
	 * Whether the nodes of this tree maintain a count their children.
	 * 
	 * @return
	 */

	boolean isCounting();
	
	/**
	 * The number of nodes in the tree. Note that this may be a slow operation
	 * since implementations are not required to maintain an active count of
	 * the number of nodes in the tree.
	 * 
	 * @return the total number of nodes in the tree
	 */
	
	int nodeCount();
	
	/**
	 * An estimate of the memory used to store the tree, measured in bytes. Note
	 * that if the tree is not counting node children, this may be a very slow
	 * operation.
	 * 
	 * @return an estimate of the number of bytes of memory used to store the
	 *         tree
	 */
	
	long storageSize();

	/**
	 * The root node of the tree.
	 * 
	 * @return the root of the tree, never null
	 */

	TrieNode root();

	/**
	 * This method is called to provide the tree with an opportunity to allocate
	 * new node storage. The method is guaranteed to be called prior to the
	 * addition of nodes to the tree. This means that complete node
	 * reorganizations that might invalidate existing node objects can be
	 * avoided. Note that this may be a no-op for many possible tree
	 * implementations.
	 * 
	 * @param extraCapacity
	 *            the extra number of nodes that may be needed
	 * @see #invalidations()
	 */
	
	default void ensureExtraCapacity(int extraCapacity) {
		/* a no-op for many possible implementations */
	}

	/**
	 * <p>
	 * Creates a new path that can traverse the trie. If the trie is mutable,
	 * the path may be used to modify the trie.
	 * 
	 * <p>
	 * The new path will consist of a single node: the root of this trie.
	 * 
	 * @param capacity
	 *            the maximum number of nodes that the path might be required to
	 *            traverse, in addition to the root.
	 * @return a new path
	 */

	TrieNodePath newPath(int capacity);

	/**
	 * Instructs the tree that node storage should be compacted. The tree should
	 * make a best effort to organise its nodes to optimize for space and/or
	 * access time. In some implementations, this will be a no-op.
	 */

	default void compact() {
		/* no-op by default */
	}

	/**
	 * Removes all of the nodes from the tree, leaving only the root node.
	 * In addition, the root node is stripped of any terminal status.
	 * 
	 * @see TrieNode#isTerminal()
	 */
	
	void clear();
	
	/**
	 * Returns the number of invalidations that have occurred on this tree. An
	 * invalidation occurs when a structural change is made that invalidates one
	 * or more node instances belonging to this tree. Note that many possible
	 * tree implementations will never invalidate nodes.
	 * 
	 * @return the number of invalidations that have occurred
	 */
	
	long invalidations();
	
	// mutability
	
	default TrieNodes immutableView() {
		return ImmutableNodes.nodes(this);
	}

	@Override
	//TODO want a specialized node implementation for immutable copy
	default TrieNodes immutableCopy() {
		return mutableCopy().immutableView();
	}
	
	//TODO create basic node copy
//	default TrieNodes mutableCopy() {
//		
//	}
}
