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


class ImmutableNodes implements TrieNodes {

	// statics
	
	private static final <T> T imm() {
		throw new IllegalStateException("immutable");
	}

	static ImmutableNodes nodes(TrieNodes nodes) {
		return new ImmutableNodes(nodes);
	}
	
	// fields
	
	private final TrieNodes nodes;
	
	// constructors
	
	private ImmutableNodes(TrieNodes nodes) {
		this.nodes = nodes;
	}
	
	// accessors
	
	TrieNodes nodes() {
		return nodes;
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
		return nodes.root();
	}

	@Override
	public void ensureExtraCapacity(int extraCapacity) {
		return; // no-op
	}

	@Override
	public TrieNodePath newPath(int capacity) {
		return new ImmutableTrieNodePath(nodes.newPath(capacity));
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

}
