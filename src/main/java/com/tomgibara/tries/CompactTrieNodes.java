package com.tomgibara.tries;

import static java.lang.Math.max;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;

/*
 *  Everything as per PackedTrieNodes except, further to this...
 *  
 *  The logic is extended in the following way: Nodes may have 'compact'
 *  siblings. In this case the sibling flag is set, but the sibling pointer is
 *  negative, it counts the number of siblings that are stored immediately and
 *  contiguously after the node itself.
 *  
 *  These compact siblings are then amenable to linear scanning and binary
 *  searching. Generally, this makes lookups on compacted trees faster, at the
 *  expense of making removals slower (because time must be spent removing the
 *  compact status of prior siblings).
 */

class CompactTrieNodes extends AbstractTrieNodes {

	// statics
	
	private static final boolean BINARY_SEARCH = true;
	
	static TrieNodeSource SOURCE = (byteOrder, counting, capacityHint) -> new CompactTrieNodes(byteOrder, capacityHint, counting);
	
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
	
	CompactTrieNodes(ByteOrder byteOrder, int capacity, boolean counting) {
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
		compact(capacity + extraCapacity);
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
		compact(nodeCount);
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
		if (counting) root.setCount(0);
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
		CompactTrieNodes copy = new CompactTrieNodes(byteOrder, nodeCount, counting);
		copy.adopt(copy.root, root);
		return copy;
	}
	
	// package scoped methods

	@Override
	void dump() {
		dump(System.out, 0, root);
	}
	
	void check(int count) {
		check(root, count);
	}
	
	// siblings must have ordinal zero, so this is a useful optimization
	// faster than new PackedNode(index).getValue();
	byte getSiblingValue(int index) {
		return (byte)  data[index * nodeSize];
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
		if (counting) out.print(" (" + node.getCountX() + "|" + node.extraCount() + ")");
		out.print(" -- " + node.getSiblingIndex() + " " + node.getCompactCount());
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

	// returns number of siblings
	private int adopt(PackedNode ours, PackedNode theirs) {
		ours.setTerminal(theirs.isTerminal());
		PackedNode sibling = theirs.getSibling();
		int sibcount = sibling == null ? 0 : 1 + adopt( ours.insertSibling(sibling.getValue()), sibling);
		if (sibcount != 0) ours.setSiblingIndex(-1 - sibcount);
		PackedNode child = theirs.getChild();
		//TODO need force if there are too many children to pack too?
		if (child != null) adopt( ours.insertChild(child.getValue(), child.hasSibling()), child);
		if (counting && ours.ordinal == 0) ours.setCount(theirs.getCount() - ours.extraCount());
		return sibcount;
	}
	
	@SuppressWarnings("unused")
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
	
	@SuppressWarnings("unused")
	private boolean isFree(int index) {
		for (int i = freeCount; i > 0; i--) {
			int free = freeIndex;
			if (index == free) return true;
			free = data[freeIndex * nodeSize];
		}
		return false;
	}
	
	private void compact(int newCapacity) {
		CompactTrieNodes those = new CompactTrieNodes(byteOrder, newCapacity, counting);
		those.adopt(those.root, root);
		capacity = newCapacity;
		data = those.data;
		nodeLimit = those.nodeLimit;
		freeIndex = those.freeIndex;
		freeCount = those.freeCount;
		invalidations ++;
		this.nodeCount = those.nodeCount;
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
		public CompactTrieNodes nodes() {
			return CompactTrieNodes.this;
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
		
		@Override
		public boolean isSibling(TrieNode node) {
			return getSiblingFlag() && getSiblingIndex() == ((PackedNode) node).index;
		}
		
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
		
		@Override
		public boolean isChild(TrieNode node) {
			PackedNode n = (PackedNode) node;
			if (n.ordinal != 0) { // node is packed
				// packed in the same node and successors
				return n.index == this.index && n.ordinal == this.ordinal + 1;
			} else {
				// this is the only/last node and the child index matches
				return this.ordinal + 1 == getValueCount() && n.index == getChildIndex();
			}
		}
		
		@Override
		public boolean remove(TrieNode childOrSibling) {
			PackedNode n = (PackedNode) childOrSibling;
			if (index == n.index && n.ordinal == ordinal + 1) {
				// note, truncates packed descendants
				setValueCount(ordinal + 1);
				setChild(null);
				return true;
			}
			if (getChildIndex() == n.index) {
				setChildIndex(n.getSiblingIndex());
				return true;
			}
			if (getSiblingFlag() && getSiblingIndex() == n.index) {
				setSibling(n.getSibling());
				return true;
			}
			return false;
		}
		
		@Override
		public boolean removeChild(TrieNode child) {
			PackedNode n = (PackedNode) child;
			int ni = n.index;
			// n may be a packed child
			if (index == ni && n.ordinal == ordinal + 1) {
				// note, truncates packed descendants
				setValueCount(ordinal + 1);
				setChild(null);
				return true;
			}
			if (n.ordinal != 0) return false;

			// n may be a direct child
			int ci = getChildIndex();
			if (ci == 0) return false;
			if (ci == ni) {
				setChildIndex(n.getSiblingIndex());
				return true;
			}

			// n my be a sibling of our child
			PackedNode c = new PackedNode(ci);
			while (true) {
				// watch out for a compact sibling
				int count = c.getCompactCount();
				if (count > 0) {
					if (ni < ci || ni > ci + count) return false;
					new PackedNode(ni - 1).setSibling(n.getSibling());
					decompactNodes(ci, ni - 1);
					return true;
				}

				// otherwise walk the reference
				PackedNode s = c.getSibling();
				if (s == null) return false;
				int si = s.index;
				if (si == ni) {
					c.setSibling(s.getSibling());
					return true;
				}

				c = s;
				ci = si;
			}
		}
		
		@Override
		public int getCount() {
			return counting ? getCountX() + extraCount() : count(this);
		}
		
		@Override
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
		
		@Override
		public PackedNode findChild(byte value) {
			ByteOrder order = nodes().byteOrder();
			PackedNode child = getChild();
			if (child == null) return null;
			while (true) {
				PackedNode compact = child.findCompactSibling(value);
				if (compact != null) return compact;
				int c = order.compare(child.getValue(), value);
				if (c == 0) return child;
				if (c > 0) return null;
				if (!child.hasSibling()) return null;
				child = child.getSibling();
			}
		}
		
		@Override
		public PackedNode findChildOrNext(byte value) {
			ByteOrder order = nodes().byteOrder();
			PackedNode child = getChild();
			if (child == null) return null;
			while (true) {
				PackedNode compact = child.findCompactSiblingOrNext(value);
				if (compact != null) return compact;
				int c = order.compare(child.getValue(), value);
				if (c >= 0) return child;
				if (!child.hasSibling()) return null;
				child = child.getSibling();
			}
		}
		
		//should simply reimplement this method
		@Override
		public PackedNode findOrInsertChild(byte value) {
			PackedNode child = getChild();
			if (child == null) return insertChild(value);
			PackedNode previous = null;
			ByteOrder order = nodes().byteOrder();
			while (true) {
				// first see if child has compact siblings
				int count = child.getCompactCount();
				if (count > 0) {
					// are we cleared to use binary search?
					if (BINARY_SEARCH) {
						int from = child.index;
						int to = from + count;
						int mid;
						int c;
						while (true) {
							mid = (from + to) >> 1;
							byte nodeValue = getSiblingValue(mid);
							c = order.compare(nodeValue, value);
							if (c == 0) return new PackedNode(mid);
							if (from == to) break;
							if (c > 0) {
								to = mid;
							} else {
								from = mid + 1;
							}
						};
						assert(mid >= child.index && mid <= child.index + count);
						if (c < 0) {
							// mid node is less, make new node its sibling
							decompactNodes(child.index, mid);
							PackedNode node = new PackedNode(mid);
							return node.insertSibling(value);
						}
						if (mid == child.index) {
							// new node has to go before current child
							return previous == null ? insertChild(value) : previous.insertSibling(value);
						}
						// new node needs to go before mid node
						decompactNodes(child.index, mid - 1);
						PackedNode node = new PackedNode(mid - 1);
						return node.insertSibling(value);
					}
					// not okay to do a binary search, so use a linear scan
					int limit = child.index + count + 1;
					for (int i = child.index; i < limit; i++) {
						//TODO eliminate object creation
						PackedNode node = new PackedNode(i);
						byte nodeValue = node.getValue();
						int c = order.compare(nodeValue, value);
						if (c == 0) return node;
						if (c > 0) {
							decompactNodes(child.index, i);
							return previous == null ? insertChild(value) : previous.insertSibling(value);
						}
						previous = node;
					}
					decompactNodes(child.index, previous.index);
					return previous.insertSibling(value);
				}
				// otherwise proceed by walking the chain of siblings
				int c = order.compare(child.getValue(), value);
				if (c == 0) return child;
				if (c > 0) return previous == null ? insertChild(value) : previous.insertSibling(value);
				if (!child.hasSibling()) return child.insertSibling(value);
				previous = child;
				child = child.getSibling();
			}
		}

		@Override
		public PackedNode getLastChild() {
			PackedNode child = getChild();
			if (child == null) return null;
			// fast case - child has compact siblings
			int count = child.getCompactCount();
			if (count > 0) return new PackedNode(child.index + count);
			// slow path - walk the chain
			while (child.hasSibling()) child = child.getSibling();
			return child;
		}

		// object methods
		
		@Override
		public String toString() {
			int followers = getValueCount() - ordinal - 1;
			String following = followers < 0 ? "!" : String.join("", Collections.nCopies(followers, "."));
			return index + "+" + ordinal + " " + valueAsString() + following + (isTerminal() ? "*" : "") + (counting ? ">" + getCountX() : "") + (getCompactCount() > 0 ? " (" + getCompactCount() + ")" : "");
		}

		// package scoped implementations

		PackedNode insertChild(byte value) {
			return insertChild(value, false);
		}

		PackedNode insertChild(byte value, boolean forceNewNode) {
			PackedNode existingChild = separateChild();
			if (!forceNewNode && getValueCount() < MAX_VALUES && !hasSibling() && existingChild == null) {
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
			// decompact any earlier compact nodes
			// if our prior node has compact nodes, we must be its sibling
			if (ordinal == 0) {
				int i = index;
				while (true) {
					PackedNode prev = new PackedNode(--i);
					if (prev.getCompactCount() <= 0) break;
					prev.decompact();
				}
			}
			PackedNode sibling = newNode(value);
			sibling.setSibling(getSibling());
			setSibling(sibling);
			return sibling;
		}

		// private helper methods

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
			int count = getCountX();
			child.setChildIndex(getChildIndex());
			child.setValueCount(valueCount - childOrd);
			int childTerminals = terminals >> childOrd;
			int parentTerminals = terminals & ~(-1 << childOrd);
			child.setTerminals(childTerminals);
			setChildIndex(child.index);
			setValueCount(childOrd);
			setTerminals(parentTerminals);
			if (counting) {
				child.setCount(count);
				setCount(count + child.extraCount());
			}
			return child;
		}

		private PackedNode findCompactSibling(byte value) {
			int count = getCompactCount();
			if (count <= 0) return null;
			ByteOrder order = nodes().byteOrder();
			// binary search
			if (BINARY_SEARCH) {
				int from = index;
				int to = index + count;
				int mid;
				while (true) {
					mid = (from + to) >> 1;
					byte nodeValue = getSiblingValue(mid);
					int c = order.compare(nodeValue, value);
					if (c == 0) return new PackedNode(mid);
					if (from == to) return null;
					if (c > 0) {
						to = mid;
					} else {
						from = mid + 1;
					}
				}
			}
			// linear scan
			int limit = index + count;
			for (int i = index; i <= limit; i++) {
				byte nodeValue = getSiblingValue(i);
				int c = order.compare(nodeValue, value);
				if (c == 0) return new PackedNode(i);
				if (c > 0) return null;
			}
			return null;
		}

		private PackedNode findCompactSiblingOrNext(byte value) {
			int count = getCompactCount();
			if (count <= 0) return null;
			ByteOrder order = nodes().byteOrder();
			// binary search
			if (BINARY_SEARCH) {
				int from = index;
				int to = index + count;
				int mid;
				while (true) {
					mid = (from + to) >> 1;
					byte nodeValue = getSiblingValue(mid);
					int c = order.compare(nodeValue, value);
					if (c == 0) return new PackedNode(mid);
					if (from == to) {
						if (c > 0) return new PackedNode(mid);
						return mid == index + count ? null : new PackedNode(mid + 1);
					};
					if (c > 0) {
						to = mid;
					} else {
						from = mid + 1;
					}
				}
			}
			// linear scan
			int limit = index + count;
			for (int i = index; i <= limit; i++) {
				byte nodeValue = getSiblingValue(i);
				int c = order.compare(nodeValue, value);
				if (c >= 0) return new PackedNode(i);
			}
			return null;
		}

		// data access methods
		
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
			int valueCount = (data[offset] & COUNT_MASK) >> COUNT_SHIFT;
			assert (valueCount > 0); // a node always has its own value, unless deleted
			return valueCount;
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
			//TODO externalize this check (?)
			if (!getSiblingFlag()) return 0;
			int siblingIndex = data[offset + 1];
			// note no-sibling shouldn't be possible here because we're checking that we have a sibling
			if (siblingIndex < 0) return index + 1;
			return siblingIndex;
		}
		
		private int getCompactCount() {
			// we need to check if the node has a sibling first
			// otherwise we could be testing & returning the values of packed children
			if (!getSiblingFlag()) return 0;
			int count = data[offset + 1];
			return count < 0 ? -1 - count : -1;
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

		private void setCount(int count) {
			if (count < 0) throw new IllegalStateException();
			data[offset + 3] = count;
			invalidations ++;
		}

		private int getCountX() {
			return data[offset + 3];
		}

		private int extraCount() {
			return Integer.bitCount(getTerminals() >> ordinal);
		}

		private void decompactNodes(int fromIndex, int toIndex) {
			//TODO optimize away object creations
			for (int j = fromIndex; j < toIndex; j++) {
				PackedNode tmp = new PackedNode(j);
				tmp.decompact();
			}
		}

		private void decompact() {
			assert(hasSibling());
			setSiblingIndex(getSiblingIndex());
		}

		// for testing only
		
		private String valueAsString() {
			int value = getValue() & 0xff;
			if (value >= 32 && value < 127) return String.valueOf((char) value);
			String str = Integer.toHexString(value);
			return str.length() == 1 ? "0" + str : str;
		}
		
		@SuppressWarnings("unused")
		private int countChildren() {
			PackedNode child = getChild();
			int count = 0;
			while (child != null) {
				count++;
				child = child.getSibling();
			}
			return count;
		}
		

	}

}
