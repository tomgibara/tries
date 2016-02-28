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

import static com.tomgibara.tries.AbstractTrieNode.FLAG_CHILD;
import static com.tomgibara.tries.AbstractTrieNode.FLAG_SIBLING;
import static com.tomgibara.tries.AbstractTrieNode.FLAG_TERMINAL;

import java.util.List;

import com.tomgibara.streams.ReadStream;
import com.tomgibara.streams.WriteStream;


class BasicTrieNodes extends AbstractTrieNodes {

	public static final TrieNodeSource SOURCE = new AbstractTrieNodeSource() {

		@Override
		public BasicTrieNodes newNodes(ByteOrder byteOrder, boolean counting, int capacityHint) {
			return new BasicTrieNodes(byteOrder);
		}

	};

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
	
	@Override
	void adopt(AbstractTrieNode ours, TrieNode theirs) {
		adopt((BasicNode) ours, theirs);
	}

	@Override
	void readComplete() {
		root.computeCounts();
	}
	
	private BasicNode adopt(BasicNode ours, TrieNode theirs) {
		ours.setTerminal(theirs.isTerminal());
		TrieNode sibling = theirs.getSibling();
		if (sibling != null) adopt( ours.insertSibling(sibling.getValue()), sibling);
		TrieNode child = theirs.getChild();
		if (child != null) adopt( ours.insertChild(child.getValue()), child);
		// resolve counting
		int count;
		if (theirs.nodes().isCounting()) {
			count = theirs.getCount();
		} else {
			count = ours.isTerminal() ? 1 : 0;
			BasicNode node = ours.getChild();
			while (node != null) {
				count += node.getCount();
				node = node.getSibling();
			}
		}
		ours.count = count;
		return ours;
	}
	
	private BasicNode readNode(ReadStream stream, List<AbstractTrieNode> awaitingSiblings) {
		byte value = stream.readByte();
		BasicNode node = new BasicNode(value);
		int flags = stream.readByte();
		node.terminal = (flags & FLAG_TERMINAL) != 0;
		if ((flags & FLAG_SIBLING) != 0) awaitingSiblings.add(node);
		if ((flags & FLAG_CHILD) != 0) node.readChild(stream, awaitingSiblings);
		return node;
	}

	private class BasicNode extends AbstractTrieNode {

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

//		@Override
//		public boolean isSibling(TrieNode node) {
//			return sibling == node;
//		}

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
		
//		@Override
//		public boolean isChild(TrieNode node) {
//			return child == node;
//		}

//		@Override
//		public boolean remove(TrieNode childOrSibling) {
//			BasicNode node = (BasicNode) childOrSibling;
//			if (childOrSibling == child) {
//				setChild(node.getSibling());
//				return true;
//			}
//			if (childOrSibling == sibling) {
//				setSibling(node.getSibling());
//				return true;
//			}
//			return false;
//		}
		
		@Override
		public boolean removeChild(TrieNode child) {
			BasicNode c = this.child;
			if (c == null) return false;
			BasicNode n = (BasicNode) child;
			if (n == c) {
				this.child = n.sibling;
				return true;
			}
			while (true) {
				BasicNode s = c.sibling;
				if (s == null) return false;
				if (s == n) {
					c.sibling = s.sibling;
					return true;
				}
				c = s;
			}
		}

		@Override
		public int getCount() {
			return count;
		}

		@Override
		public String toString() {
			String str = Integer.toHexString(value);
			return (str.length() == 1 ? "0" : "") + str + (terminal ? "." : "") + " (" + count + ")";
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
		
		@Override
		void readChild(ReadStream stream, List<AbstractTrieNode> awaitingSiblings) {
			child = readNode(stream, awaitingSiblings);
		}
		
		@Override
		void readSibling(ReadStream stream, List<AbstractTrieNode> awaitingSiblings) {
			sibling = readNode(stream, awaitingSiblings);
		}

		private int computeCounts() {
			int c = terminal ? 1 : 0;
			if (child != null) c += child.computeCounts();
			count = c;
			if (sibling != null) c += sibling.computeCounts();
			return c;
		}
		
	}
}
