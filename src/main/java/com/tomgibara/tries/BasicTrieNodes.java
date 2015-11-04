package com.tomgibara.tries;


class BasicTrieNodes extends AbstractTrieNodes {

	// NOTE these are just estimates
	private final int OBJ_HEADER_SIZE = 12;
	private final int PTR_SIZE_IN_BYTES = 4;
	
	private final ByteOrder byteOrder;
	private final BasicNode root;
	private long invalidations = 0L;
	
	BasicTrieNodes(ByteOrder byteOrder) {
		this.byteOrder = byteOrder;
		root = new BasicNode();
	}
	
	@Override
	public boolean isMutable() {
		return true;
	}

	@Override
	public BasicTrieNodes mutableCopy() {
		BasicTrieNodes copy = new BasicTrieNodes(byteOrder);
		copy.adopt(copy.root, root);
		return copy;
	}

	@Override
	public ByteOrder byteOrder() {
		return byteOrder;
	}

	@Override
	public boolean isCounting() {
		return true;
	}

	@Override
	public int nodeCount() {
		return root.countNodes();
	}

	@Override
	public long storageSize() {
		return nodeCount() * (OBJ_HEADER_SIZE + PTR_SIZE_IN_BYTES * 3 + 6);
	}

	@Override
	public BasicNode root() {
		return root;
	}

	@Override
	public BasicNode newNode(byte value) {
		return new BasicNode(value);
	}

	@Override
	public void incCounts(TrieNode[] stack, int length) {
		int rootCount = root.getCount();
		for (int i = 0; i < length; i++) {
			((BasicNode) stack[i]).count ++;
		}
		root.count = rootCount + 1;
	}

	@Override
	public void decCounts(TrieNode[] stack, int length) {
		int rootCount = root.getCount();
		for (int i = 0; i < length; i++) {
			((BasicNode) stack[i]).count --;
		}
		root.count = rootCount - 1;
	}

	@Override
	public void clear() {
		root.child = null;
		root.sibling = null;
		root.count = 0;
	}

	@Override
	public long invalidations() {
		return invalidations;
	}
	
	@Override
	void dump() {
		// TODO Auto-generated method stub
	}
	
	private BasicNode adopt(BasicNode ours, BasicNode theirs) {
		ours.setTerminal(theirs.isTerminal());
		BasicNode sibling = theirs.getSibling();
		if (sibling != null) adopt( ours.insertSibling(sibling.getValue()), sibling);
		BasicNode child = theirs.getChild();
		if (child != null) adopt( ours.insertChild(child.getValue()), child);
		ours.count = theirs.getCount();
		return ours;
	}

	private class BasicNode implements TrieNode {

		private final byte value;
		private boolean terminal;
		private BasicNode sibling;
		private BasicNode child;
		private int count;
		
		BasicNode() {
			value = 0;
		}
		
		BasicNode(byte value) {
			this.value = value;
		}

		@Override
		public BasicTrieNodes nodes() {
			return BasicTrieNodes.this;
		}
		
		@Override
		public byte getValue() {
			return value;
		}

		@Override
		public boolean isTerminal() {
			return terminal;
		}

		@Override
		public void setTerminal(boolean terminal) {
			if (terminal == this.terminal) return;
			this.terminal = terminal;
			invalidations ++;
		}

		@Override
		public boolean hasSibling() {
			return sibling != null;
		}

		@Override
		public BasicNode getSibling() {
			return sibling;
		}

		@Override
		public BasicNode insertSibling(byte value) {
			BasicNode sibling = newNode(value);
			sibling.setSibling(this.sibling);
			setSibling(sibling);
			return sibling;
		}

		@Override
		public boolean isSibling(TrieNode node) {
			return sibling == node;
		}

		@Override
		public BasicNode getChild() {
			return child;
		}

		@Override
		public BasicNode insertChild(byte value) {
			BasicNode child = newNode(value);
			child.setSibling(this.child);
			setChild(child);
			return child;
		}

		@Override
		public boolean remove(TrieNode childOrSibling) {
			BasicNode node = (BasicNode) childOrSibling;
			if (childOrSibling == child) {
				setChild(node.getSibling());
				return true;
			}
			if (childOrSibling == sibling) {
				setSibling(node.getSibling());
				return true;
			}
			return false;
		}

		@Override
		public int getCount() {
			return count;
		}

		void setSibling(BasicNode sibling) {
			if (sibling == this.sibling) return;
			this.sibling = sibling;
			invalidations ++;
		}
		
		void setChild(BasicNode child) {
			if (child == this.child) return;
			this.child = child;
			invalidations ++;
		}

		int countNodes() {
			int count = 1;
			if (child != null) count += child.countNodes();
			if (sibling != null) count += sibling.countNodes();
			return count;
		}



	}
}
