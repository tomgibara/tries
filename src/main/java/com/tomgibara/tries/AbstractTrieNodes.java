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
import com.tomgibara.tries.nodes.TrieNode;
import com.tomgibara.tries.nodes.TrieNodes;

abstract class AbstractTrieNodes implements TrieNodes {

	private static final int MASK_CHILD_ONLY = AbstractTrieNode.FLAG_CHILD;
	private static final int MASK_CHILD_OR_TERMINAL = AbstractTrieNode.FLAG_CHILD | AbstractTrieNode.FLAG_TERMINAL;

	public abstract AbstractTrieNode root();

	abstract AbstractTrieNode newNode(byte value);

	abstract AbstractTrieNode[] newStack(int length);

	// called when reading has been finished
	abstract void readComplete();

	abstract void ensureExtraCapacity(int extraCapacity);

	abstract void adopt(AbstractTrieNode ours, TrieNode theirs);

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

	// exposed for testing
	abstract void dump();
	abstract int availableCapacity();
}
