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

import com.tomgibara.streams.WriteStream;

abstract class AbstractTrieNodes implements TrieNodes {

	private static final int MASK_CHILD_ONLY = AbstractTrieNode.FLAG_CHILD;
	private static final int MASK_CHILD_OR_TERMINAL = AbstractTrieNode.FLAG_CHILD | AbstractTrieNode.FLAG_TERMINAL;
	
	@Override
	public void writeTo(WriteStream stream, TrieNode[] stack, int length) {
		CountingStream counter = new CountingStream();
		writeNodes(counter, stack, length);
		// each node writes two bytes
		int count = counter.count() >> 1;
		// write number of nodes
		stream.writeInt(count);
		// write nodes
		writeNodes(stream, stack, length);
	}
	
	public abstract AbstractTrieNode root();

	abstract void dump();

	abstract void adopt(AbstractTrieNode ours, TrieNode theirs);
	
	// called when reading has been finished
	abstract void readComplete();

	void writeNodes(WriteStream stream, TrieNode[] stack, int length) {
		if (length == 0) return;
		int last = length - 1;
		for (int i = 0; i < last; i++) {
			AbstractTrieNode node = (AbstractTrieNode) stack[i];
			node.writeNode(stream, MASK_CHILD_ONLY);
		}
		AbstractTrieNode node = (AbstractTrieNode) stack[last];
		node.writeNode(stream, MASK_CHILD_OR_TERMINAL);
		node = node.getChild();
		if (node != null) node.writeNodes(stream);
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
