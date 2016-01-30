package com.tomgibara.tries;

import com.tomgibara.fundament.Mutability;

/**
 * <p>
 * A tree of {@link TrieNode} instances. All trees operate with a fixed byte
 * ordering which is reported by the {@link #byteOrder()} method. Trees also
 * support mutability controls as per the <code>Mutability</code> interface.
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
	 * The number of nodes in the tree. Note that if the tree is not counting
	 * node children, this may be a very slow operation.
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
	
	// mutability
	
	default TrieNodes immutableView() {
		return new ImmutableNodes(this);
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
