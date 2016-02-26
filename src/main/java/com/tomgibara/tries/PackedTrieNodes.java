package com.tomgibara.tries;

import static java.lang.Math.max;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.tomgibara.streams.ReadStream;

/*
 * Each node is organized into either 3 ints, or 4 (if counts are maintained).
 * In addition to it's value and a child pointer, node can support either:
 *   * a sequence of up to 5 child values prior to the referenced child
 *   * a sibling pointer.
 * The implementation takes care of packing child nodes when the tree is built
 * and splitting them out into separate nodes when siblings need to be added.
 * Child nodes are not re-packed when sibling nodes are deleted; re-packing only
 * occurs when the nodes are compacted.
 *
 * The first int contains:
 *  * bits [0-7]   the node's byte value
 *  * bits [8-13]  flags indicating terminal status
 *  * bit  15      flag indicating the presence of a sibling node
 *  * bits [16-18] the number of value bytes stored
 *  * bits [24-31] the second byte value (if present)
 *  
 * At present, the number of value bytes is never less than 1. A value of zero
 * is reserved for future use to indicate a 'binary tree node type' for
 * accelerating iteration over siblings.
 *  
 * The second int contains either a pointer to the sibling node OR 4 additional
 * byte values.
 *  
 * The third int always points to a child node. Absence of a child node is
 * indicated by a pointer to the root node (which always resides at index 0).
 * 
 * The value of the fourth int (if present) is simply the number of terminal
 * nodes contained in all ancestors *not including those packed in the node
 * itself*.
 * 
 * Diagramatically structure of the first two ints is something like:
 * 
 *  _HEAD_
 *  |76543210|76543210|76543210|76543210|
 *  |<-XVL1->|_____CNT|S_<-TR->|<-VALU->|
 *
 * _   = unused
 * S   = has sibling
 * TR  = terminator flags [0,5]
 * CNT = number of embedded children [0,5]
 *
 *  _SIBLING_OR_VALUES_
 *  |76543210|76543210|76543210|76543210|
 *  |<-XVL5->|<-XVL4->|<-XVL3->|<-XVL2->|
 */

class PackedTrieNodes extends AbstractTrieNodes {

	// statics
	
	static TrieNodeSource SOURCE = new AbstractTrieNodeSource() {
		
		@Override
		public PackedTrieNodes newNodes(ByteOrder byteOrder, boolean counting, int capacityHint) {
			return new PackedTrieNodes(byteOrder, capacityHint, counting);
		}

	};
	
	// fields

	private final ByteOrder byteOrder;
	private final boolean counting;
	private final int nodeSize;
	private final PackedNode root;
	private int capacity;
	private int[] data;
	private int freeIndex;
	private int freeCount;
	private int nodeCount;
	private int nodeLimit;
	private long invalidations = 0;
	
	// constructor
	
	PackedTrieNodes(ByteOrder byteOrder, int capacity, boolean counting) {
		this.byteOrder = byteOrder;
		this.counting = counting;
		this.capacity = capacity;
		nodeSize = counting ? 4 : 3;
		data = new int[capacity * nodeSize];
		freeCount = 0;
		nodeCount = 0;
		nodeLimit = 0;
		root = newNode();
	}
	
	// trie nodes methods

	@Override
	public boolean isCounting() {
		return counting;
	}
	
	@Override
	public ByteOrder byteOrder() {
		return byteOrder;
	}
	
	@Override
	public int nodeCount() {
		return nodeCount;
	}
	
	@Override
	public long storageSize() {
		return data.length * 4L;
	}
	
	@Override
	public PackedNode root() {
		return root;
	}

	@Override
	public void ensureExtraCapacity(int extraCapacity) {
		int free = (capacity - nodeLimit) + freeCount;
		if (free >= extraCapacity) return;
		extraCapacity = max(capacity, max(extraCapacity, 256));
		compact(capacity + extraCapacity, counting);
	}
	
	@Override
	public PackedNode newNode(byte value) {
		PackedNode n = newNode();
		n.setChildValue(0, value);
		n.setValueCount(1);
		return n;
	}
	
	@Override
	public void incCounts(TrieNode[] stack, int length) {
		if (!counting) return;
		if (length == 0) return;
		int last = ((PackedNode) stack[length - 1]).index;
		int previous = last;
		for (int i = length - 1; i >= 0; i--) {
			PackedNode node = (PackedNode) stack[i];
			int index = node.index;
			if (index == previous) continue;
			node.adjustCount(1);
			previous = index;
		}
		if (previous != 0 && last != 0) root.adjustCount(1);
	}
	
	@Override
	public void decCounts(TrieNode[] stack, int length) {
		if (!counting) return;
		if (length == 0) return;
		PackedNode lastNode = (PackedNode) stack[length - 1];
		int last = lastNode.index;
		int previous = last;
		for (int i = length - 1; i >= 0; i--) {
			PackedNode node = (PackedNode) stack[i];
			int index = node.index;
			if (index == previous) continue;
			if (index != 0) node.adjustCount(-1);
			previous = index;
		}
		if (last != 0) root.adjustCount(-1);
	}

	@Override
	public void compact() {
		compact(nodeCount, counting);
	}
	
	@Override
	public void clear() {
		freeCount = 0;
		freeIndex = -1;
		nodeLimit = 1;
		nodeCount = 1;
		root.setTerminal(false);
		root.setSibling(null);
		root.setChild(null);
		if (counting) root.setExternalCount(0);
	}
	
	@Override
	public long invalidations() {
		return invalidations;
	}

	// mutability
	
	@Override
	public boolean isMutable() {
		return true;
	}
	
	@Override
	public TrieNodes mutableCopy() {
		PackedTrieNodes copy = new PackedTrieNodes(byteOrder, nodeCount, counting);
		copy.adopt(copy.root, root);
		return copy;
	}
	
	// package scoped methods

	@Override
	void dump() {
		dump(System.out, 0, root);
	}
	
	@Override
	void adopt(AbstractTrieNode ours, TrieNode theirs) {
		adopt((PackedNode)ours, theirs, theirs.nodes().isCounting());
	}
	
	@Override
	void readComplete() {
		compact(nodeCount, false);
	}
	
	void check(int count) {
		check(root, count);
	}
	
	// private helper methods
	
	private void dump(PrintStream out, int indent, PackedNode node) {
		if (node == null) return;
		out.print(String.format("% 6d:", node.index));
		for (int i = 0; i < indent; i++) {
			out.print(node.ordinal != 0 && i == indent - 1 ? '+' : ' ');
		}
		out.print(node.valueAsString());
		if (node.isTerminal()) out.print("*");
		if (counting) out.print(" (" + node.getExternalCount() + "|" + node.internalCount() + ")");
		out.println();
		
		dump(out, indent + 1, node.getChild());
		dump(out, indent, node.getSibling());
	}
	
	private void check(PackedNode node, int count) {
		//TODO if counted, check count is zero?
		if (node == null) return;
		
		// check structure
		if (node.isDangling() && node.index != 0) throw new IllegalStateException("dangling node: " + node);
		if (node.getValueCount() <= node.ordinal) throw new IllegalStateException("too many children in node: " + node);
		
		// check ordering
		
		// check count
		if (counting) {
			if (count < 0) throw new IllegalStateException("Expected negative count of " + count + " on node " + node);
			
			if (!node.hasSibling()) {
				if (count != node.getCount()) throw new IllegalStateException("Expected count of " + count + " on node " + node + " but count was " + node.getCount());
			}

			// check subnodes
			check(node.getChild(), node.isTerminal() ? node.getCount() - 1 : node.getCount());
			check(node.getSibling(), count - node.getCount());
		} else {
			// check subnodes
			check(node.getChild(), count);
			check(node.getSibling(), count);
		}
	}

	private PackedNode newNode() {
		int index;
		if (nodeLimit < capacity) {
			index = nodeLimit++;
		} else if (freeCount > 0) {
			index = freeIndex;
			freeIndex = data[freeIndex * nodeSize];
			freeCount --;
		} else {
			throw new IllegalStateException("No free nodes");
		}
		nodeCount++;
		Arrays.fill(data, index * nodeSize, index * nodeSize + nodeSize, 0);
		PackedNode node = new PackedNode(index);
		node.setValueCount(1);
		return node;
	}

	private PackedNode adopt(PackedNode ours, TrieNode theirs, boolean theirsCount) {
		ours.setTerminal(theirs.isTerminal());
		TrieNode sibling = theirs.getSibling();
		if (sibling != null) adopt( ours.insertSibling(sibling.getValue()), sibling, theirsCount);
		TrieNode child = theirs.getChild();
		if (child != null) adopt( ours.insertChild(child.getValue()), child, theirsCount);
		// resolve counting
		if (counting && ours.ordinal == 0) {
			int count;
			if (theirsCount) {
				count = theirs.getCount();
			} else {
				count = ours.isTerminal() ? 1 : 0;
				PackedNode node = ours.getChild();
				while (node != null) {
					count += node.getCount();
					node = node.getSibling();
				}
			}
			ours.setExternalCount(count - ours.internalCount());
		}
		return ours;
	}
	
	private void checkCounts(PackedNode ours, PackedNode theirs) {
		if (ours == null && theirs == null) return;
		if (ours == null || theirs == null) throw new IllegalStateException("Missing node " + ours + " " + theirs);
		if (ours.getCount() != theirs.getCount()) throw new IllegalStateException("Different sizes between " + ours + " " + theirs + " ("+ours.getCount()+") ("+theirs.getCount()+")");
		checkCounts(ours.getChild(), theirs.getChild());
		checkCounts(ours.getSibling(), theirs.getSibling());
	}
	
	private int count(PackedNode node, int count) {
		if (node.isTerminal()) count ++;
		if (node.hasChild()) count += count(node.getChild(), 0);
		if (node.hasSibling()) count += count(node.getSibling(), 0);
		return count;
	}
	
	private int count(PackedNode node) {
		int count = node.isTerminal() ? 1 : 0;
		if (node.hasChild()) count += count(node.getChild(), 0);
		return count;
	}
	
	private boolean isFree(int index) {
		for (int i = freeCount; i > 0; i--) {
			int free = freeIndex;
			if (index == free) return true;
			free = data[freeIndex * nodeSize];
		}
		return false;
	}
	
	private void compact(int newCapacity, boolean trustCount) {
		PackedTrieNodes those = new PackedTrieNodes(byteOrder, newCapacity, counting);
		those.adopt(those.root, root, trustCount);
		capacity = newCapacity;
		data = those.data;
		nodeLimit = those.nodeLimit;
		freeIndex = those.freeIndex;
		freeCount = those.freeCount;
		invalidations ++;
		this.nodeCount = those.nodeCount;
	}

	private int compare(byte a, byte b) {
		return byteOrder.compare(a, b);
	}

	// inner classes
	
	class PackedNode extends AbstractTrieNode {

		// statics
		
		private static final int MAX_VALUES = 6;
		
		private static final int VALUE_MASK      = 0x000000ff;
		private static final int TERMINAL_MASK   = 0x00003f00;
		private static final int TERMINAL_SHIFT  = 8;
		private static final int SIBLING_MASK    = 0x00008000;
		private static final int COUNT_MASK      = 0x00070000;
		private static final int COUNT_SHIFT     = 16;
		
		// fields
		
		private int index;
		private int offset;
		// points to packed child - 0 is start node, always < valueCount
		private int ordinal;

		// constructors
		
		private PackedNode(int index) {
			this(index, 0);
		}

		private PackedNode(int index, int ordinal) {
			this.index = index;
			this.ordinal = ordinal;
			this.offset = index * nodeSize;
		}

		// attributes
		
		@Override
		public PackedTrieNodes nodes() {
			return PackedTrieNodes.this;
		}
		
		@Override
		public byte getValue() {
			return getChildValue(ordinal);
		}
		
		@Override
		public boolean isTerminal() {
			return (getTerminals() & (1 << ordinal)) != 0;
		}
		
		@Override
		public void setTerminal(boolean terminal) {
			if (terminal == isTerminal()) return;
			int terminals = getTerminals();
			terminals ^= (1 << ordinal);
			setTerminals(terminals);
		}

		// sibling
		
		@Override
		public boolean hasSibling() {
			return getSiblingFlag();
		}
		
		@Override
		public PackedNode getSibling() {
			int siblingIndex = getSiblingIndex();
			return siblingIndex == 0 ? null : new PackedNode(siblingIndex);
		}
		
//		@Override
//		public boolean isSibling(TrieNode node) {
//			return getSiblingFlag() && getSiblingIndex() == ((PackedNode) node).index;
//		}
		
		// child
		
		public PackedNode getChild() {
			int childOrd = ordinal + 1;
			if (childOrd < getValueCount()) return new PackedNode(index, childOrd);
			int childIndex = getChildIndex();
			return childIndex == 0 ? null : new PackedNode(childIndex);
		}
		
		@Override
		public boolean hasChild() {
			return ordinal + 1 < getValueCount() || getChildIndex() != 0;
		}
		
//		@Override
//		public boolean isChild(TrieNode node) {
//			PackedNode n = (PackedNode) node;
//			if (n.ordinal != 0) { // node is packed
//				// packed in the same node and successors
//				return n.index == this.index && n.ordinal == this.ordinal + 1;
//			} else {
//				// this is the only/last node and the child index matches
//				return this.ordinal + 1 == getValueCount() && n.index == getChildIndex();
//			}
//		}
		
//		@Override
//		public boolean remove(TrieNode childOrSibling) {
//			PackedNode n = (PackedNode) childOrSibling;
//			if (index == n.index && n.ordinal == ordinal + 1) {
//				// note, truncates packed descendants
//				setValueCount(ordinal + 1);
//				setChild(null);
//				return true;
//			}
//			if (getChildIndex() == n.index) {
//				setChildIndex(n.getSiblingIndex());
//				return true;
//			}
//			if (getSiblingFlag() && getSiblingIndex() == n.index) {
//				setSibling(n.getSibling());
//				return true;
//			}
//			return false;
//		}
		
		@Override
		public boolean removeChild(TrieNode child) {
			if (child == null) return false;
			PackedNode n = (PackedNode) child;
			// n may be a packed child
			if (index == n.index && n.ordinal == ordinal + 1) {
				// note, truncates packed descendants
				setValueCount(ordinal + 1);
				setChild(null);
				return true;
			}
			if (n.ordinal != 0) return false;

			// n may be an unpacked child
			int i = getChildIndex();
			if (i == 0) return false;
			if (i == n.index) {
				setChildIndex(n.getSiblingIndex());
				return true;
			}

			// n may be a sibling
			PackedNode c = new PackedNode(i);
			while (true) {
				PackedNode s = c.getSibling();
				if (s == null) return false;
				if (s.index == n.index) {
					c.setSibling(s.getSibling());
					return true;
				}
				c = s;
			}
		}
		
		public int getCount() {
			return counting ? getExternalCount() + internalCount() : count(this);
		}
		
		public void delete() {
			if (ordinal > 0) {
				int childCount = getValueCount() - 1;
				if (ordinal == childCount) {
					setValueCount(childCount);
				} else {
					//NOTE we ignore this because this state is temporarily possible
					// for efficiency/simplicity, removal truncates packed descendents
				}
			} else {
				data[offset] = freeIndex;
				freeIndex = index;
				nodeCount --;
			}
		}
		
		private String valueAsString() {
			int value = getValue() & 0xff;
			if (value >= 32 && value < 127) return String.valueOf((char) value);
			String str = Integer.toHexString(value);
			return str.length() == 1 ? "0" + str : str;
		}
		
		@Override
		public String toString() {
			int followers = getValueCount() - ordinal - 1;
			String following = followers < 0 ? "!" : String.join("", Collections.nCopies(followers, "."));
			return index + "+" + ordinal + " " + valueAsString() + following + (isTerminal() ? "*" : "") + (counting ? ">" + getExternalCount() : "");
		}

		// package scoped implementations

		@Override
		PackedNode insertChild(byte value) {
			PackedNode existingChild = separateChild();
			if (getValueCount() < MAX_VALUES && !hasSibling() && existingChild == null) {
				int childOrd = ordinal + 1;
				// insert new value
				setChildValue(childOrd, value);
				// update count
				setValueCount(getValueCount() + 1);
				// fabricate child node
				return new PackedNode(index, childOrd);
			}
			PackedNode child = newNode(value);
			//TODO replace with setting index & flag
			child.setSibling(existingChild);
			setChildIndex(child.index);
			return child;
		}

		@Override
		PackedNode insertSibling(byte value) {
			PackedNode sibling = newNode(value);
			sibling.setSibling(getSibling());
			setSibling(sibling);
			return sibling;
		}

		@Override
		void readChild(ReadStream stream, List<AbstractTrieNode> awaitingSiblings) {
			readNode(stream, false, awaitingSiblings);
		}

		@Override
		void readSibling(ReadStream stream, List<AbstractTrieNode> awaitingSiblings) {
			readNode(stream, true, awaitingSiblings);
		}

		// private helper methods

		private void readNode(ReadStream stream, boolean sibling, List<AbstractTrieNode> awaitingSiblings) {
			byte value = stream.readByte();
			int flags = stream.readByte();
			boolean isTerminal = (flags & FLAG_TERMINAL) != 0;
			boolean hasSibling = (flags & FLAG_SIBLING) != 0;
			boolean hasChild = (flags & FLAG_CHILD) != 0;

			PackedNode node;
			if (sibling) {
				node = newNode(value);
				setSibling(node);
			} else {
				node = insertChild(value);
				if (hasSibling) node = separateChild();
			}
			if (hasSibling) awaitingSiblings.add(node);
			node.setTerminal(isTerminal);
			if (hasChild) node.readNode(stream, false, awaitingSiblings);
		}

		private void separate() {
			if (ordinal == 0 && getValueCount() == 1) return;
			if (ordinal == 0) {
				separateChild();
			} else {
				PackedNode parent = new PackedNode(index, ordinal - 1);
				parent.separateChild();
				//TODO ugly - any better way?
				index = parent.getChildIndex();
				ordinal = 0;
				offset = index * nodeSize;
			}
		}
		
		private void setSibling(TrieNode sibling) {
			if (sibling == null) {
				setSiblingFlag(false);
			} else {
				// to have a sibling, we mustn't be packed ...
				separate();
				// ... and we mustn't have packed children ...
				separateChild();
				// ... now we are safe to set a sibling
				setSiblingIndex(((PackedNode) sibling).index);
				setSiblingFlag(true);
			}
		}

		//TODO currently only called with null - consider changing to clearChild
		private void setChild(TrieNode child) {
			if (child == null) {
				if (!hasChild()) return;
				separateChild();
				setChildIndex(0);
			} else {
				separateChild();
				setChildIndex(((PackedNode) child).index);
			}
		}
		
		private PackedNode separateChild() {
			int childOrd = ordinal + 1;
			if (childOrd >= getValueCount()) return getChild(); // no child to separate
			PackedNode child = newNode();
			int valueCount = getValueCount();
			for (int i = childOrd; i < valueCount; i++) {
				child.setChildValue(i - childOrd, getChildValue(i));
			}
			int terminals = getTerminals();
			int count = getExternalCount();
			child.setChildIndex(getChildIndex());
			child.setValueCount(valueCount - childOrd);
			int childTerminals = terminals >> childOrd;
			int parentTerminals = terminals & ~(-1 << childOrd);
			child.setTerminals(childTerminals);
			setChildIndex(child.index);
			setValueCount(childOrd);
			setTerminals(parentTerminals);
			if (counting) {
				child.setExternalCount(count);
				setExternalCount(count + child.internalCount());
			}
			return child;
		}

		private byte getChildValue(int i) {
			switch (i) {
			case 0 : return (byte)  data[offset    ];
			case 1 : return (byte) (data[offset    ] >> 24);
			default: return (byte) (data[offset + 1] >> ((i - 2) * 8));
			}
		}
		
		private void setChildValue(int i, byte value) {
			switch (i) {
			case 0 : data[offset] = data[offset] & ~VALUE_MASK         | value & 0xff; break;
			case 1 : data[offset] = data[offset] & ~(VALUE_MASK << 24) | value << 24 ; break;
			default:
				int s = (i - 2) * 8;
				data[offset + 1] = data[offset + 1] & ~(VALUE_MASK << s)  | (value & 0xff) << s;
			}
			invalidations ++;
		}
		
		private int getValueCount() {
			return (data[offset] & COUNT_MASK) >> COUNT_SHIFT;
		}
		
		private void setValueCount(int valueCount) {
			data[offset] = data[offset] & ~COUNT_MASK | (valueCount << COUNT_SHIFT);
		}
		
		private int getTerminals() {
			return (data[offset] & TERMINAL_MASK) >> TERMINAL_SHIFT;
		}
		
		private void setTerminals(int terminals) {
			data[offset] = data[offset] & ~TERMINAL_MASK | (terminals << TERMINAL_SHIFT);
		}

		private boolean getSiblingFlag() {
			return (data[offset] & SIBLING_MASK) != 0;
		}
		
		private void setSiblingFlag(boolean siblingFlag) {
			if (siblingFlag) {
				data[offset] |= SIBLING_MASK;
			} else {
				data[offset] &= ~SIBLING_MASK;
			}
		}
		
		private int getSiblingIndex() {
			//TODO eliminate check
			return !getSiblingFlag() ? 0 : data[offset + 1];
		}
		
		private void setSiblingIndex(int siblingIndex) {
			data[offset + 1] = siblingIndex;
			invalidations ++;
		}

		private int getChildIndex() {
			return data[offset + 2];
		}

		private void setChildIndex(int childIndex) {
			data[offset + 2] = childIndex;
			invalidations ++;
		}

		private void adjustCount(int delta) {
			data[offset + 3] += delta;
			invalidations ++;
		}

		private void setExternalCount(int count) {
			if (count < 0) throw new IllegalStateException();
			data[offset + 3] = count;
			invalidations ++;
		}

		private int getExternalCount() {
			return data[offset + 3];
		}

		private int internalCount() {
			return Integer.bitCount(getTerminals() >> ordinal);
		}

	}

}
