package com.tomgibara.tries;

import java.util.Arrays;

import com.tomgibara.streams.WriteStream;
import com.tomgibara.tries.nodes.TrieNode;
import com.tomgibara.tries.nodes.TrieNodePath;

abstract class AbstractTrieNodePath implements TrieNodePath {

	final AbstractTrieNodes nodes;
	final AbstractTrieNode[] stack;
	AbstractTrieNode head;
	int length = 1;
	
	AbstractTrieNodePath(AbstractTrieNodes nodes, AbstractTrieNode[] stack) {
		this.nodes = nodes;
		this.stack = stack;
		stack[0] = head = nodes.root();
	}

	@Override
	public int capacity() {
		return stack.length - 1;
	}

	@Override
	public boolean isEmpty() {
		return length == 0;
	}
	
	@Override
	public int length() {
		return length;
	}

	@Override
	public void reset() {
		length = 1;
		head = stack[0];
	}

	@Override
	public TrieNode head() {
		return head;
	}

	@Override
	public void push(byte value) {
		stack[length ++] = head = head.findOrInsertChild(value);
	}

	@Override
	public boolean terminate(boolean terminal) {
		if (head.isTerminal() == terminal) return false;
		if (terminal) {
			head.setTerminal(true);
			incrementCounts();
		} else {
			decrementCounts(-1);
			head.setTerminal(false);
		}
		return true;
	}

	@Override
	public void prune() {
		// we do no pruning if the node has a child
		// because our tree can have no dangling nodes (except possibly the root)
		// so if the node has a child, there must be terminations further along the tree
		if (length > 1 && !head.hasChild()) {
			int i = length - 2;
			AbstractTrieNode child = head;
			AbstractTrieNode parent = null;
			for (; i >= 1; i--) {
				parent = stack[i];
				if (parent.isTerminal() || parent.getChild().hasSibling()) break;
				child = parent;
			}
			if (i < 1) parent = nodes.root();
			boolean removed = parent.removeChild(child);
			assert(removed); // we know the child is there
			// finally, delete any detached nodes
			for (int j = length - 1; j > i ; j--) stack[j].delete();
		}
	}

	@Override
	public boolean walkValue(byte value) {
		AbstractTrieNode node = head.findChild(value);
		if (node == null) return false;
		stack[length ++] = head = node;
		return true;
	}

	@Override
	public boolean walkCount(int index) {
		int count = head.getCount();

		while (!head.isTerminal() || index != 0) {
			if (index < count) {
				if (head.isTerminal()) index--;
				stack[length++] = head = head.getChild();
			} else {
				index -= count;
				stack[length - 1] = head = head.getSibling();
			}
			count = head.getCount();
		}

		return index < head.getCount();
	}

	@Override
	public boolean walkChild() {
		AbstractTrieNode node = head.getChild();
		if (node == null) return false;
		stack[length++] = head = node;
		return true;
	}

	@Override
	public boolean walkLastChild() {
		AbstractTrieNode node = head.getLastChild();
		if (node == null) return false;
		stack[length++] = head = node;
		return true;
	}

	@Override
	public boolean walkSibling() {
		AbstractTrieNode node = head.getSibling();
		if (node == null) return false;
		stack[length-1] = head = node;
		return true;
	}

	@Override
	public TrieNode pop() {
		AbstractTrieNode node = stack[--length];
		head = length == 0 ? null : stack[length - 1];
		return node;
	}

	@Override
	public void serialize(TrieSerialization<?> serialization) {
		for (int i = serialization.length() + 1; i < length; i++) {
			serialization.push(stack[i].getValue());
		}
	}

	@Override
	public boolean deserialize(TrieSerialization<?> serialization) {
		byte[] buffer = serialization.buffer();
		int len = serialization.length();

		for (int i = 0; i < len; i++) {
			if (!walkValue(buffer[i])) return false;
		}
		return true;
	}

	@Override
	public void first(TrieSerialization<?> serial, int minimumLength) {
		int len = serial.length();
		byte[] buffer = serial.buffer();

		for (int i = length - 1; i < len; i++) {
			byte value = buffer[i];
			head = head.findChildOrNext(value);
			if (head == null) {
				serial.trim(i);
				for (;i > minimumLength; i--) {
					head = stack[length - 1];
					head = stack[length - 1] = head.getSibling();
					if (head != null) {
						serial.replace(head.getValue());
						return;
					}
					serial.pop();
					length--;
				}
				head = null;
				serial.reset();
				length = 0;
				return;
			}
			stack[length++] = head;
			byte v = head.getValue();
			if (v != value) {
				if (i >= minimumLength) {
					serial.trim(i);
					serial.push(v);
				} else {
					head = null;
					serial.reset();
					length = 0;
				}
				return;
			}
		}
	}

	@Override
	public void advance(TrieSerialization<?> serial, int minimumLength) {
		outer: do {
			if (head.hasChild()) {
				stack[length ++] = head = head.getChild();
				serial.push(head.getValue());
				continue outer;
			}
			if (length == 1) {
				length = 0;
				head = null;
				return;
			}
			do {
				if (head.hasSibling()) {
					stack[length - 1] = head = head.getSibling();
					serial.replace(head.getValue());
					continue outer;
				}
				serial.pop();
				length --;
				// note may be less than prefix length if former element was the prefix
				if (serial.length() <= minimumLength) {
					length = 0;
					head = null;
					return;
				}
				head = stack[length - 1];
			} while (length > 1);
		} while (!head.isTerminal());
	}

	@Override
	public void writeTo(WriteStream stream) {
		CountingStream counter = new CountingStream();
		nodes.writeNodes(counter, stack, length);
		// each node writes two bytes
		int count = counter.count() >> 1;
		// write number of nodes
		stream.writeInt(count);
		// write nodes
		nodes.writeNodes(stream, stack, length);
	}

	// object methods
	
	@Override
	public String toString() {
		return Arrays.asList(Arrays.copyOf(stack, length)).toString();
	}
	
	// package scoped methods
	
	void push(AbstractTrieNode node) {
		stack[length ++] = head = node;
	}
	
	/*
	 * Decrements the child count of all of the nodes in this path. Non-counting
	 * trees may ignore this method call.
	 */

	// called with -ve amount
	abstract void decrementCounts(int adj);

	/*
	 * Increments the child count of all of the nodes in this path. Non-counting
	 * trees may ignore this method call.
	 */

	abstract void incrementCounts();

	TrieNode[] stack() {
		return stack;
	}

	// inner classes
	
	//TODO move to streams package?
	private static class CountingStream implements WriteStream {

		private int count = 0;
		
		public int count() {
			return count;
		}
		
		@Override
		public void writeByte(byte v) { count ++; }
		
		@Override
		public void writeBytes(byte[] bs) { count += bs.length; }
		
		@Override
		public void writeBytes(byte[] bs, int off, int len) { count += len; }
		
		@Override
		public void writeInt(int v) { count += 4; }
		
		@Override
		public void writeBoolean(boolean v) { count += 1; }
		
		@Override
		public void writeShort(short v) { count += 2; }
		
		@Override
		public void writeLong(long v) { count += 8; }
		
		@Override
		public void writeFloat(float v) { count += 4; }
		
		@Override
		public void writeDouble(double v) { count += 8; }
		
		@Override
		public void writeChar(char v) { count += 2; }
		
		@Override
		public void writeChars(char[] cs) { count += 4; }
		
		@Override
		public void writeChars(char[] cs, int off, int len) { count += len; }
		
		@Override
		public void writeChars(CharSequence cs) { count += 4 + cs.length(); }
		
	}

}