package com.tomgibara.tries;

import static com.tomgibara.tries.AbstractTrieNode.FLAG_CHILD;
import static com.tomgibara.tries.AbstractTrieNode.FLAG_SIBLING;
import static com.tomgibara.tries.AbstractTrieNode.FLAG_TERMINAL;

import java.util.ArrayList;
import java.util.List;

import com.tomgibara.streams.StreamDeserializer;
import com.tomgibara.streams.StreamException;

abstract class AbstractTrieNodeSource implements TrieNodeSource {

	public abstract AbstractTrieNodes newNodes(ByteOrder byteOrder, boolean counting, int capacityHint);
	
	@Override
	public TrieNodes copyNodes(TrieNodes nodes, boolean counting, int capacityHint) {
		AbstractTrieNodes newNodes = newNodes(nodes.byteOrder(), counting, capacityHint);
		newNodes.adopt(newNodes.root(), nodes.root());
		return newNodes;
	}
	
	@Override
	public StreamDeserializer<TrieNodes> deserializer(ByteOrder byteOrder, boolean counting, int capacityHint) {
		AbstractTrieNodes nodes = newNodes(byteOrder, counting, capacityHint);
		List<AbstractTrieNode> siblings = new ArrayList<>();
		return stream -> {
			int count = stream.readInt();
			nodes.ensureExtraCapacity(count);
			byte rootValue = stream.readByte();
			if (rootValue != 0) throw new StreamException("zero root value expected");
			int flags = stream.readByte();
			AbstractTrieNode root = nodes.root();
			if ((flags & FLAG_TERMINAL) != 0) root.setTerminal(true);
			if ((flags & FLAG_SIBLING) != 0) throw new StreamException("unexpected root sibling");
			if ((flags & FLAG_CHILD) != 0) root.readChild(stream, siblings);
			while (!siblings.isEmpty()) {
				siblings.remove(siblings.size() - 1).readSibling(stream, siblings);
			}
			nodes.readComplete();
			return nodes;
		};
	}

}
