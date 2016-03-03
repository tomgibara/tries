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


/**
 * <p>
 * A node in {@link TrieNodes} tree. Note that in some implementations,
 * instances of nodes may only be valid between successive invalidations.
 * 
 * <p>
 * Note that in the documentation for this class, comparisons of node values are
 * always in the context of the {@link ByteOrder} applied to the
 * {@link TrieNodes} containing the node and its children.
 * 
 * @author Tom Gibara
 *
 */
interface TrieNode {

	// attributes

	/**
	 * The tree of nodes to which this node belongs.
	 * 
	 * @return the tree containing this node.
	 */

	TrieNodes nodes();

	/**
	 * The byte value at this node
	 * 
	 * @return the node's value
	 */

	byte getValue();

	/**
	 * Whether this node terminates a complete byte sequence within the tree.
	 * 
	 * @return whether this node terminates a valid byte sequence
	 */

	boolean isTerminal();
	
	/**
	 * Sets whether this is a terminating node. Terminating nodes form define
	 * the valid byte sequences within the tree.
	 * 
	 * @param terminal whether the node is terminal.
	 */

	void setTerminal(boolean terminal);

	// sibling
	
	/**
	 * Whether this node has a sibling node.
	 * 
	 * @return true iff the node has a sibling.
	 */

	boolean hasSibling();
	
	/**
	 * The sibling node of this node, if any.
	 * 
	 * @return the sibling node, or null
	 */

	TrieNode getSibling();
	
	// child

	/**
	 * Whether this node has a child node.
	 * 
	 * @return true iff the node has a child
	 */

	default boolean hasChild() {
		return getChild() != null;
	}
	
	/**
	 * The child node of this node, if any. Any child returned by this method
	 * will have the least value of any of its siblings (as per the byte order
	 * applied to the tree).
	 * 
	 * @return the child node, or null
	 */

	TrieNode getChild();
	
	// child navigation
	
	/**
	 * The last child of this node, if any. Any child returned by this method
	 * will have the greatest value of any of its siblings.
	 * 
	 * @return the last child node, or null.
	 */

	TrieNode getLastChild();
	
	/**
	 * Finds, if it exists, the child node of this node with the specified
	 * value.
	 * 
	 * @param value
	 *            the desired node value
	 * @return a child with the specified value, or null if no such child exists
	 */

	TrieNode findChild(byte value);

	// counting

	/**
	 * The number of terminating descendants of this node, including the node
	 * itself.
	 * 
	 * @return the number terminated paths that include this node.
	 */

	int getCount();

	/**
	 * Identifies all of the child nodes with values not meeting or exceeding
	 * the supplied value, and returns the sum of their counts, plus one if this
	 * node is a terminating node.
	 * 
	 * @param value
	 *            the cut-off node value
	 * @return the number of terminations up-to but not including the node with
	 *         the specified value
	 */

	int countToChild(byte value);

	// mutation

	/**
	 * This method is called on a node to signal that any associated storage may
	 * be released by the managing {@link TrieNodes}. This method will only be
	 * called on detached nodes and indicates the node will not be reattached to
	 * the tree. In many possible implementations, this will be a no-op.
	 */

	void delete();

	// convenience
	
	/**
	 * Whether this node is non-terminal and has no child.
	 * @return true iff the node does not terminate and has no child
	 */

	boolean isDangling();

}
