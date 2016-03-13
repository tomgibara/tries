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
package com.tomgibara.tries.nodes;

import com.tomgibara.streams.WriteStream;
import com.tomgibara.tries.ByteOrder;
import com.tomgibara.tries.TrieSerialization;

/**
 * <p>
 * Implementations of this interface record a sequence of nodes from the root of
 * the trie to some other node in the trie. The interface is designed to
 * accommodate traversal and mutation in trees without back-references.
 * 
 * <p>
 * All paths have a fixed capacity, which is the greatest distance that the path
 * may extend from the root. In the interests of performance, implementators are
 * invited to assume that this limit will never be exceeded and that, in
 * general, no invalid calls will be ever be made on the interface.
 * 
 * @author Tom Gibara
 *
 */

public interface TrieNodePath {

	/**
	 * Whether the path is empty.
	 * 
	 * @return true if the path length is zero, false otherwise.
	 */

	boolean isEmpty();
	
	/**
	 * The number of nodes that can be stored in addition to the root.
	 * 
	 * @return the maximum number of times the path may be advanced from the
	 *         root.
	 */

	int capacity();
	
	/**
	 * The number of nodes in the path. This is initially one since all paths
	 * begin at the root. However, the length may legitimately fall to zero if
	 * the root is <em>popped</em> from the path. This condition may be used to
	 * indicate certain states.
	 * 
	 * @return the number of nodes in the path, may be zero, never negative.
	 * @see #pop
	 */

	int length();
	
	/**
	 * Sets the length of the path to one. After a call to this method, the path
	 * will consist of a single node: the root.
	 */

	void reset();

	/**
	 * The head of the path. This is the last node appended to the path. The
	 * head of the path may also change when the former head is popped from the
	 * path. Initially the head of a path is the trie root. The head may become
	 * null if the root is popped from the path.
	 * 
	 * @return the node furthest from the root, may be null if the root has been
	 *         popped from the path
	 */

	TrieNode head();
	
	/**
	 * Advances to the child node of head node with the specified value. If at
	 * the time of the method call no such node exists, a new child node with
	 * the given value is added to the head. Thus this method always advances
	 * the path. In cases where new nodes are created, the implementation may
	 * assume that the last node push onto the path will be explicitly
	 * terminated with a call to {@link #terminate(boolean)}
	 * 
	 * @param value
	 *            a node value
	 */

	void push(byte value);

	/**
	 * Changes the terminal state of the head node. In cases where the removal
	 * of terminal status creates a dangling node, the implementation may assume
	 * that the path will be explicitly pruned.
	 * 
	 * this method is expected to remove it (iteratively if necessary) to
	 * eliminate any redundant nodes.
	 * 
	 * @param terminal
	 *            whether the head should be terminal
	 * @return true if the terminal status of the head was modified
	 * @see TrieNode#isDangling()
	 * @see #prune()
	 */

	boolean terminate(boolean terminal);

	/**
	 * Forces the head to dangle: removing any children and assigning it a
	 * non-terminal status. The implementation may assume that the path will be
	 * explicitly pruned.
	 * 
	 * @see TrieNode#isDangling()
	 * @see #prune()
	 */

	void dangle();
	
	/**
	 * Removes any dangling nodes in present in the path from the trie.
	 * Implementations may assume that the head is not terminal.
	 */

	void prune();

	/**
	 * Advances to the child node of head node with the specified value, if such
	 * a node exists. Otherwise the path remains unchanged.
	 * 
	 * @param value
	 *            the value of the child to advance towards
	 *
	 * @return true if such child existed, false if the path remained unchanged
	 */

	boolean walkValue(byte value);

	/**
	 * <p>
	 * Advances the path over the indicated number of terminating nodes in depth
	 * first order. As a result of calling this method, the path will generally
	 * advance further into the tree by walking children and siblings, but the
	 * method should not backtrack: it should advance as far as it can, and if
	 * the specified count was not achieved, it should return false.
	 * 
	 * <p>
	 * Implementators can expect that this method will only be called on tries
	 * that support accelerated node counts.
	 * 
	 * @param count
	 *            the number of terminating nodes to walk over
	 * @return whether the count was achieved
	 * @see TrieNodes#isCounting()
	 */

	boolean walkCount(int count);

	/**
	 * Advances to the child of the head, as returned by
	 * {@link TrieNode#getChild()}, if it exists.
	 * 
	 * @return true if the path was advanced, false otherwise
	 */

	boolean walkChild();

	/**
	 * Advances to the last child of the head, as returned by
	 * {@link TrieNode#getLastChild()}, if it exists.
	 * 
	 * @return true if the path was advanced, false otherwise
	 */

	boolean walkLastChild();

	/**
	 * Changes the current head of the list to its sibling, as returned by
	 * {@link TrieNode#getChild()}, if it exists.
	 * 
	 * @return true if the head was changed to its sibling, false otherwise
	 */

	boolean walkSibling();

	/**
	 * Shortens the path by removing the current head node. Callers are
	 * permitted to pop the root node to create an empty path. At this point
	 * this method will return null and no futher modifications may be attempted
	 * to this path.
	 * 
	 * @return the path node furthest from the root, possibly null
	 * @see #head()
	 */

	TrieNode pop();

	/**
	 * <p>
	 * Writes the byte values of the path nodes into a serialization. If the
	 * serialization is not empty, it is assumed to match the initial path
	 * segment.
	 * 
	 * @param serialization
	 *            a serialization to populate
	 */

	void serialize(TrieSerialization<?> serialization);
	
	/**
	 * <p>
	 * Advances the path by walking the head through each byte value of the
	 * serialization in turn. If at any point, no such child exists, the method
	 * returns false, otherwise true is returned.
	 * 
	 * <p>
	 * In cases where this path already extends beyond the root node when this
	 * method is called, the serialization may be assumed to match the initial
	 * length of the path.
	 * 
	 * @param serialization
	 *            a serialization to walk
	 * @return true if a path matching the serialization was produced, false
	 *         otherwise
	 */

	boolean deserialize(TrieSerialization<?> serialization);

	/**
	 * <p>
	 * Modifies the path so that its serialization matches the supplied
	 * serialization. In the case where no such path exists, the path should be
	 * modified so that its serialization is the next available serialization as
	 * per the established byte order. If there is no </em>next</em>
	 * serialization the path should become empty.
	 * 
	 * <p>
	 * Where an match is not possible, the supplied serialization must be
	 * mutated to match the final path state. In cases where this path already
	 * extends beyond the root node when this method is called, the
	 * serialization may be assumed to match the initial length of the path.
	 * 
	 * <p>
	 * In some cases, this method will be called with a non-zero
	 * <code>minimumLength</code>. In this case the path must not backtrack to a
	 * length less than this value, instead it should return in an empty state.
	 * 
	 * @param serialization
	 *            a serialization
	 * @param minimumLength
	 *            a length beyond which the path should not backtrack
	 */

	void first(TrieSerialization<?> serialization, int minimumLength);

	/**
	 * <p>
	 * Advances the path to the next terminal node in traversal order. The
	 * method is supplied with a serialization that matches the path. This
	 * serialization be mutated so that it continues to match the path when this
	 * method terminates.
	 * 
	 * <p>
	 * A call to this method will never leave the head unchanged. In the case
	 * where there is no subsequent terminal node, the path should return in an
	 * empty state.
	 * 
	 * <p>
	 * In some cases, this method will be called with a non-zero
	 * <code>minimumLength</code>. In this case the path must not backtrack to a
	 * length less than this value, instead it should return in an empty state.
	 * 
	 * @param serialization
	 *            a serialization that matches the current path that is updated
	 *            with path changes
	 * 
	 * @param minimumLength
	 *            a length beyond which the path should not backtrack
	 * @see #isEmpty()
	 */

	void advance(TrieSerialization<?> serialization, int minimumLength);

	/**
	 * <p>
	 * Serializes node data to a byte stream.
	 * 
	 * <p>
	 * When the path contains only the root node, all nodes should be recorded
	 * in the stream. When the path extends beyond the root node, the trie data
	 * written should be pruned such that these nodes
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
	 */

	void writeTo(WriteStream stream);

}
