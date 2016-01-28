package com.tomgibara.tries;

class ImmutableNodes implements TrieNodes {

	private final TrieNodes nodes;
	
	ImmutableNodes(TrieNodes nodes) {
		this.nodes = nodes;
	}
	
	private static final <T> T imm() {
		throw new IllegalStateException("immutable");
	}
	
	// mutability
	
	@Override
	public boolean isMutable() {
		return false;
	}

	@Override
	public TrieNodes mutableCopy() {
		return nodes.mutableCopy();
	}

	@Override
	public TrieNodes immutableCopy() {
		return nodes.immutableCopy();
	}

	@Override
	public TrieNodes immutableView() {
		return this;
	}

	@Override
	public ByteOrder byteOrder() {
		return nodes.byteOrder();
	}

	// nodes methods
	
	@Override
	public boolean isCounting() {
		return nodes.isCounting();
	}

	@Override
	public int nodeCount() {
		return nodes.nodeCount();
	}

	@Override
	public long storageSize() {
		return nodes.storageSize();
	}

	@Override
	public TrieNode root() {
		return wrap(nodes.root());
	}

	@Override
	public void ensureExtraCapacity(int extraCapacity) {
		return; // no-op
	}

	@Override
	public TrieNode newNode(byte value) {
		return imm();
	}

	@Override
	public int populate(TrieNode root, byte[] values, int length, TrieNode[] stack, TrieNode[] referrers) {
		int len = nodes.populate(unwrap(root), values, length, stack, referrers);
		for (int i = 0; i < len; i++) {
			stack[i] = wrap(stack[i]);
			referrers[i] = wrap(referrers[i]);
		}
		return len;
	}

	@Override
	public void incCounts(TrieNode[] stack, int length) {
		imm();
	}

	@Override
	public void decCounts(TrieNode[] stack, int length) {
		imm();
	}

	@Override
	public void compact() {
		// no-op
	}

	@Override
	public void clear() {
		imm();
	}

	@Override
	public long invalidations() {
		return nodes.invalidations();
	}

	// private helper methods
	
	ImmNode wrap(TrieNode node) {
		return node == null ? null : new ImmNode(node);
	}

	TrieNode unwrap(TrieNode node) {
		return node == null ? null : ((ImmNode) node).node;
	}

	// inner classes
	
	private class ImmNode implements TrieNode {

		private final TrieNode node;
		
		public ImmNode(TrieNode node) {
			this.node = node;
		}

		@Override
		public TrieNodes nodes() {
			return ImmutableNodes.this;
		}
		
		@Override
		public byte getValue() {
			return node.getValue();
		}

		@Override
		public boolean isTerminal() {
			return node.isTerminal();
		}

		@Override
		public boolean isDangling() {
			return node.isDangling();
		}

		@Override
		public void setTerminal(boolean terminal) {
			node.setTerminal(terminal);
		}

		@Override
		public boolean hasSibling() {
			return node.hasSibling();
		}

		@Override
		public TrieNode getSibling() {
			return wrap(node.getSibling());
		}

		@Override
		public boolean isSibling(TrieNode node) {
			return this.node.isSibling(unwrap(node));
		}

		@Override
		public boolean hasChild() {
			return node.hasChild();
		}

		@Override
		public TrieNode getChild() {
			return wrap(node.getChild());
		}
		
		@Override
		public boolean isChild(TrieNode node) {
			return this.node.isChild(node);
		}

		@Override
		public TrieNode getLastChild() {
			return wrap(node.getLastChild());
		}

		@Override
		public TrieNode findChild(byte value) {
			return wrap(node.findChild(value));
		}

		@Override
		public TrieNode findChildOrNext(byte value) {
			return wrap(node.findChildOrNext(value));
		}

		@Override
		public TrieNode findOrInsertChild(byte value) {
			return imm();
		}

		@Override
		public int countToChild(byte value) {
			return node.countToChild(value);
		}

		@Override
		public boolean remove(TrieNode childOrSibling) {
			return node.remove(unwrap(childOrSibling));
		}
		
		@Override
		public boolean removeChild(TrieNode child) {
			return node.removeChild(unwrap(child));
		}

		@Override
		public int getCount() {
			return node.getCount();
		}

		@Override
		public void delete() {
			node.delete();
		}
		
	}
	
}
