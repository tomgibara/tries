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

import static com.tomgibara.tries.TrieTest.bytes;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigInteger;

import org.junit.Test;

import com.tomgibara.fundament.Bijection;
import com.tomgibara.streams.StreamDeserializer;
import com.tomgibara.tries.nodes.TrieNodeSource;
import com.tomgibara.tries.nodes.TrieNodes;

public class TriesTest {

	@Test
	public void testBytes() {
		Trie<byte[]> trie = Tries.serialBytes().newTrie();
		trie.add(bytes("boo"));
		trie.add(bytes("book"));
		trie.add(bytes("bookie"));
		
		assertTrue(trie.contains(bytes("boo")));
		assertTrue(trie.contains(bytes("book")));
		assertTrue(trie.contains(bytes("bookie")));
	}
	
	@Test
	public void testNonCounting() {
		Tries<byte[]> tries = Tries.serialBytes().nodeSource(new NonCountedNodeSource());
		tries.newTrie();
		try {
			tries.indexed();
			fail();
		} catch (IllegalStateException e) {
			/* expected */
		}
		try {
			tries.indexed(true);
		} catch (IllegalStateException e) {
			/* expected */
		}
		tries = Tries.serialBytes().nodeSource(Tries.sourceForSpeed()).indexed();
		try {
			tries.nodeSource(new NonCountedNodeSource());
		} catch (IllegalStateException e) {
			/* expected */
		}
	}
	
	class NonCountedNodeSource implements TrieNodeSource {

		private TrieNodeSource source = Tries.sourceForSpeed();
		
		@Override
		public boolean isCountingSupported() { return false; }

		@Override
		public TrieNodes newNodes(ByteOrder byteOrder, boolean counting, int capacityHint) {
			return source.newNodes(byteOrder, counting, capacityHint);
		}

		@Override
		public TrieNodes copyNodes(TrieNodes nodes, boolean counting, int capacityHint) {
			return source.copyNodes(nodes, counting, capacityHint);
		}

		@Override
		public StreamDeserializer<TrieNodes> deserializer(ByteOrder byteOrder, boolean counting, int capacityHint) {
			return source.deserializer(byteOrder, counting, capacityHint);
		}

	}

	@Test
	public void testAdaptedWith() {
		Bijection<byte[], BigInteger> adapter = Bijection.fromFunctions(
				byte[].class,             BigInteger.class,
				bs -> new BigInteger(bs), bi -> bi.toByteArray()
				);
		Trie<BigInteger> ints = Tries.serialBytes().adaptedWith(adapter) .newTrie();
		BigInteger bi = new BigInteger("395403948504000");
		ints.add(bi);
		assertTrue(ints.asBytesTrie().contains(bi.toByteArray()));
	}

}
