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

import com.tomgibara.fundament.Mutability;
import com.tomgibara.streams.WriteStream;

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

interface TrieNodes extends Mutability<TrieNodes> {

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
	 * Creates a new node with the specified value.
	 * 
	 * @param value
	 *            the byte value of the new node
	 * 
	 * @return a new node containing the specified value
	 */
	//TODO could remove this?
	TrieNode newNode(byte value);
	

//	default int populate(TrieNode root, byte[] values, int length, TrieNode[] stack, TrieNode[] referrers) {
//		ByteOrder byteOrder = byteOrder();
//		TrieNode node = root;
//		TrieNode referrer = null;
//		outer: for (int i = 0; i < length; i++) {
//			byte b = values[i];
//			TrieNode child = node.getChild();
//			if (child == null) return i;
//			referrer = node;
//			while (true) {
//				int c = byteOrder.compare(child.getValue(), b);
//				if (c == 0) {
//					node = child;
//					stack[i] = node;
//					referrers[i] = referrer;
//					continue outer;
//				}
//				if (c > 0 || !child.hasSibling()) return i;
//				referrer = child;
//				child = child.getSibling();
//			}
//		}
//		return length;
//	}

	/**
	 * <p>
	 * Increments the child count of the indicated nodes. Non-counting trees may
	 * ignore this method call.
	 * 
	 * <p>
	 * Only the counts of the first <code>length</code> nodes should be
	 * incremented. The stack may or may not contain the root node. In either
	 * case, the tree must ensure that the root node count is incremented
	 * exactly once.
	 * 
	 * @param stack
	 *            an array of nodes
	 * @param length
	 *            the number of nodes comprising the stack
	 */

	void incCounts(TrieNode[] stack, int length);
	
	/**
	 * <p>
	 * Decrements the child count of the indicated nodes. Non-counting trees may
	 * ignore this method call.
	 * 
	 * <p>
	 * Only the counts of the first <code>length</code> nodes should be
	 * decremented. The stack may or may not contain the root node. In either
	 * case, the tree must ensure that the root node count is incremented
	 * exactly once.
	 * 
	 * @param stack
	 *            an array of nodes
	 * @param length
	 *            the number of nodes comprising the stack
	 */

	void decCounts(TrieNode[] stack, int length);

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
	
	/**
	 * <p>
	 * Serializes node data to a byte stream.
	 * 
	 * <p>
	 * The stack represents a sequence of nodes beginning from the root. When
	 * the stack contains only the root node, all nodes should be recorded in
	 * the stream. When the stack contains nodes other than the root node, the
	 * trie data written should be pruned such that these nodes
	 * <em>but not their siblings or children</em> are be recorded in the
	 * stream, with the exception of the last node's child which, if it exists,
	 * should be recorded in the stream, together with all of its siblings and
	 * children, and their descendants in turn. If the stack is empty (ie. if
	 * the length is zero) this indicates that no node data should be recorded.
	 * 
	 * <p>
	 * The data written to the stream may be organized in any format suited to
	 * the {@link TrieNodes} implementation, so long as the data may be read by
	 * the deserializer supplied by
	 * {@link TrieNodeSource#deserializer(ByteOrder, boolean, int)}.
	 * 
	 * @param stream
	 *            the stream to which node data should be written
	 * @param stack
	 *            an array of nodes
	 * @param length
	 *            the number of nodes comprising the stack
	 */
	
	void writeTo(WriteStream stream, TrieNode[] stack, int length);
	
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
