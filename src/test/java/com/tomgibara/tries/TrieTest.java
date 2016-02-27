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

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.tomgibara.storage.Stores;
import com.tomgibara.streams.EndOfStreamException;
import com.tomgibara.streams.ReadStream;
import com.tomgibara.streams.StreamBytes;
import com.tomgibara.streams.Streams;

public abstract class TrieTest {

	static final Charset UTF8 = Charset.forName("UTF8");
	static final Charset ASCII = Charset.forName("ASCII");

	private static final boolean DESCRIBE = false;
	
	private static void dump(String title, Trie<?> trie) {
		if (DESCRIBE) {
			System.out.println(title);
			((Trie<?>) trie).dump();
		}
	}
	
	private static void describe(String str) {
		if (DESCRIBE) System.out.println(str);
	}

	private static void check(Trie<?> trie) {
		((Trie<?>) trie).check();
	}

	private static void checkIAE(Runnable r) {
		try {
			r.run();
			fail("IAE expected");
		} catch (IllegalArgumentException e) {
			/* expected */
		}
	}
	
	private static void checkImm(Runnable r) {
		try {
			r.run();
			fail("expected immutable");
		} catch (IllegalStateException e) {
			/* expected */
		}
	}
	
	static long time(Runnable r) {
		long start = System.currentTimeMillis();
		r.run();
		long finish = System.currentTimeMillis();
		return finish - start;
	}

	static List<String> readWords() throws IOException {
		List<String> words = new ArrayList<String>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(WordsTest.class.getResourceAsStream("/words.txt")))) {
			while (true) {
				String word = reader.readLine();
				if (word == null) break;
				words.add(word);
			}
		}
		return words;
	}
	
	static byte[] bytes(String str) {
		return str.getBytes(TrieTest.UTF8);
	}
	
	static <E> void checkSerialization(Tries<E> tries, Trie<E> trie, boolean indexed) {
		StreamBytes bytes = Streams.bytes();
		trie.writeTo(bytes.writeStream());
		ReadStream stream = bytes.readStream();
		Trie<E> copy = tries.indexed(indexed).readTrie(stream);

		try { // check exhaustion
			stream.readByte();
			fail("unused bytes");
		} catch (EndOfStreamException e) {
			/* expected */
		}

		{ // check iteration
			Iterator<E> ti = trie.iterator();
			Iterator<E> ci = copy.iterator();
			while (ti.hasNext() && ci.hasNext()) {
				E te = ti.next();
				E ce = ci.next();
				assertEquals(te, ce);
			}
			if (ti.hasNext() || ci.hasNext()) fail("iterators have mismatched length");
		}
		
		if (indexed) { // check indexing
			Iterator<E> it = trie.iterator();
			IndexedTrie<E> dupe = (IndexedTrie<E>) copy;
			int size = dupe.size();
			for (int i = 0; i < size; i++) {
				E ce = it.next();
				E de = dupe.get(i);
				assertEquals(ce, de);
			}
			assertFalse(it.hasNext());
		}
	}
	
	abstract protected TrieNodeSource getNodeSource();
	
	@Test
	public void testIndexes() {
		IndexedTrie<String> trie = Tries.strings(UTF8).nodeSource(getNodeSource()).indexed().newTrie();
		String s = "abcdedfgh";
		for (int i = s.length(); i >= 0; i--) {
			String t = s.substring(0,  i);
			trie.add(t);
			assertEquals(t, trie.get(0));
		}
		assertEquals(s.length() + 1, trie.size());
		for (int i = 0; i <= s.length(); i++) {
			String t = s.substring(0, i);
			describe("GETTING " + i + " AND EXPECTING " + t);
			assertEquals(t, trie.get(i));
		}
	}

	@Test
	public void testDoubleInsertion() {
		IndexedTrie<String> trie = Tries.strings(UTF8).nodeSource(getNodeSource()).indexed().newTrie();
		trie.add("acxxx");
		dump("ADDED acxxx", trie);
		trie.add("abc");
		dump("ADDED abc", trie);
		assertTrue(trie.contains("acxxx"));
		assertTrue(trie.contains("abc"));
		assertEquals("abc", trie.get(0));
		assertEquals("acxxx", trie.get(1));
	}

	@Test
	public void testStrings() {
		testStrings(true);
		testStrings(false);
	}

	private void testStrings(boolean indexed) {
		Tries<String> tries = Tries.strings(UTF8).nodeSource(getNodeSource());
		Trie<String> trie;
		IndexedTrie<String> itrie;
		if (indexed) {
			itrie = tries.indexed().newTrie();
			trie = itrie;
		} else {
			itrie = null;
			trie = tries.newTrie();
		}
		assertFalse(trie.contains("Moon"));
		describe("ADDING MOON");
		assertTrue(trie.add("Moon"));
		describe("MOON ADDED");
		assertTrue(trie.contains("Moon"));
		assertFalse(trie.contains("Moo"));
		describe("ADDING MOO");
		assertTrue(trie.add("Moo"));
		describe("MOO ADDED");
		assertTrue(trie.contains("Moo"));
		describe("ADDING MOODY");
		assertTrue(trie.add("Moody"));
		describe("MOODY ADDED");
		assertTrue(trie.contains("Moody"));

		// plain iterator
		Iterator<String> i = trie.iterator();
		assertTrue(i.hasNext());
		assertEquals("Moo", i.next());
		assertTrue(i.hasNext());
		assertEquals("Moody", i.next());
		assertTrue(i.hasNext());
		assertEquals("Moon", i.next());
		assertFalse(i.hasNext());

		// iterator from first element
		i = trie.iterator("Moo");
		assertTrue(i.hasNext());
		assertEquals("Moo", i.next());
		assertTrue(i.hasNext());
		assertEquals("Moody", i.next());
		assertTrue(i.hasNext());
		assertEquals("Moon", i.next());
		assertFalse(i.hasNext());
		
		// iterator from middle element
		i = trie.iterator("Moody");
		assertTrue(i.hasNext());
		assertEquals("Moody", i.next());
		assertTrue(i.hasNext());
		assertEquals("Moon", i.next());
		assertFalse(i.hasNext());
		
		// iterator from last element
		i = trie.iterator("Moon");
		assertTrue(i.hasNext());
		assertEquals("Moon", i.next());
		assertFalse(i.hasNext());
		
		// iterator from beyond last element
		i = trie.iterator("Noooo");
		assertFalse(i.hasNext());
		
		// iterator from previous to first element
		i = trie.iterator("Ahhh");
		assertTrue(i.hasNext());
		assertEquals("Moo", i.next());
		
		// iterator from between elements
		i = trie.iterator("Mooch");
		assertTrue(i.hasNext());
		assertEquals("Moody", i.next());
		
		describe("REMOVING MOODY");
		assertTrue(trie.remove("Moody"));
		describe("MOODY REMOVED");
		assertFalse(trie.contains("Moody"));
		assertTrue(trie.contains("Moo"));
		assertTrue(trie.contains("Moon"));
		assertEquals(2, trie.size());

		describe("REMOVING MOO");
		assertTrue(trie.remove("Moo"));
		describe("MOO REMOVED");
		assertFalse(trie.contains("Moo"));
		assertTrue(trie.contains("Moon"));
		assertEquals(1, trie.size());

		dump("BEFORE", trie);
		describe("REMOVING MOON");
		assertTrue(trie.remove("Moon"));
		describe("MOON REMOVED");
		dump("AFTER", trie);
		assertFalse(trie.contains("Moon"));
		assertEquals(0, trie.size());
		
		describe("EXPECTED EMPTY");
		
		Trie<String> asciiTrie = Tries.strings(ASCII).nodeSource(getNodeSource()).newTrie();
		try {
			asciiTrie.add("\u00a9");
			fail();
		} catch (IllegalArgumentException e) {
			/* expected */
		}
		
		trie.addAll(Arrays.asList("Once", "upon", "a", "time"));
		assertEquals(4, trie.size());
		trie.clear();
		assertTrue(trie.isEmpty());
		assertFalse(trie.iterator().hasNext());
	}

	@Test
	public void testRandomizedLongs() {
		testRandomizedLongs(true);
		testRandomizedLongs(false);
	}

	private void testRandomizedLongs(boolean indexed) {
		long[] values = new long[10000];
		Random r = new Random(0L);
		for (int i = 0; i < values.length; i++) {
			// we need abs because we want to sort
			long value;
			do {
				value = Math.abs(r.nextLong());
			} while (value < 0);
			values[i] = value;
		}
		Tries<Long> tries = Tries.serial(Long.class, (v,s) -> s.writeLong(v), s -> s.readLong()).nodeSource(getNodeSource());
		Trie<Long> trie;
		IndexedTrie<Long> itrie;
		if (indexed) {
			itrie = tries.indexed().newTrie();
			trie = itrie;
		} else {
			itrie = null;
			trie = tries.newTrie();
		}

		
		// handy debug version
//		Stores.longs(values).asList().forEach(value -> {
//			describe("ADDING " + Long.toHexString(value) + " " + value);
//			trie.add(value);
//			check(trie);
//		});

		trie.addAll(Stores.longs(values).asList());

		// advanced checking of indexing
//		for (int i = 0; i < values.length; i++) {
//			long value = values[i];
//			describe("ADDING " + Long.toHexString(value) + " " + value + " (" + i + ")");
//			trie.add(value);
//			describe("ADDING COMPLETE");
//			assertEquals(i + 1, trie.size());
//			long[] vs = Arrays.copyOf(values, i + 1);
//			Arrays.sort(vs);
//			for (int j = 0; j <= i; j++) {
//				Long v;
//				try {
//					v = trie.get(j);
//				} catch (NullPointerException e) {
//					throw new RuntimeException("Missing value at " + j + " of " + trie.size() + ": expected " + vs[j], e);
//				}
//				assertEquals(vs[j], v.longValue());
//			}
//		}

		for (long value : values) {
			assertTrue(trie.contains(value));
		}

		checkTrieOrder(trie);

		for (int i = 0; i < 10000; i++) {
			long value = r.nextLong();
			boolean contained = Arrays.binarySearch(values, value) >= 0;
			Assert.assertEquals(contained, trie.contains(value));
		}

		Arrays.sort(values);
		Iterator<Long> it = trie.iterator();
		for (int i = 0; i < 10000; i++) {
			assertTrue(it.hasNext());
			assertEquals(values[i], it.next().longValue());
		}
		assertFalse(it.hasNext());

		if (itrie != null) {
			for (int i = 0; i < 1000; i++) {
				assertEquals(values[i], itrie.get(i).longValue());
				assertEquals(i, itrie.indexOf(values[i]));
			}
		}

		{
			Set<Long> set = new HashSet<>(Stores.longs(values).asList());
			int count = 0;
			for (Long value : trie) {
				assertTrue(set.contains(value));
				count++;
			}
			assertEquals(set.size(), count);
		}
		
		Collections.shuffle(Stores.longs(values).asList(), r);
		Set<Long> watch = new HashSet<Long>();
		for (int i = 0; i < 10000; i++) {
			long value = values[i];
			boolean unseen = watch.add(value);
			if (!unseen) describe("DUPLICATE VALUE : " + watch);
			boolean contained = trie.contains(value);
			if (!contained) {
				int count = 0;
				for (Long v : trie) {
					if (v == value) throw new IllegalStateException();
					count++;
				}
				describe("COUNT: " + count);
			}
			assertEquals("Trie containing " + value, unseen, contained);
			assertEquals(contained, trie.remove(value) );
			assertFalse(trie.contains(value));
			assertTrue(trie.size() + " meets or exceeds " + (10000 - i), trie.size() < 10000 - i);
		}
		assertEquals(0, trie.size());

		for (int i = 0; i < 10000; i++) {
			trie.add(values[i]);
		}

		int count = 0;
		long previous = -1L;
		for (Iterator<Long> i = trie.iterator(); i.hasNext(); ) {
			long value = i.next();
//			describe("COUNT " + count + " HEX: " + Long.toHexString(value));
//			if (!trie.contains(value)) {
//				long sub = 0L;
//				long sup = -1L;
//				for (Long v : trie) {
//					if (v <= value && v > sub) {
//						sub = v;
//					} else {
//						sup = v; break;
//					}
//				}
//				describe("SUB: " + Long.toHexString(sub) + " < " + value + " < " + Long.toHexString(sup));
//			}
			assertTrue(trie.contains(value));
			count ++;
			assertTrue(value > previous);
			if ((count % 10) == 0) {
				i.remove();
				assertFalse(trie.contains(value));
			}
			previous = value;
		}
		assertEquals(10000, count);
		assertEquals(9000, trie.size());
		long preSize = trie.storageSizeInBytes();
		trie.compactStorage();
		long postSize = trie.storageSizeInBytes();
		assertTrue(postSize <= preSize);
	}
	
	@Test
	public void testByteOrder() {
		Trie<String> trie = Tries.strings(ASCII).byteOrder((a,b) -> Integer.compare(-(a&0xff), -(b&0xff))).nodeSource(getNodeSource()).newTrie();
		assertFalse(trie.first().isPresent());
		assertFalse(trie.last().isPresent());
		trie.add("Apple");
		dump("ADDED APPLE", trie);
		trie.add("Ape");
		dump("ADDED APE", trie);
		trie.add("Baboon");
		dump("ADDED BABOON", trie);
		trie.add("Cartwheel");
		dump("ADDED CARTWHEEL", trie);
		Iterator<String> i = trie.iterator();
		assertEquals("Cartwheel", i.next());
		assertEquals("Baboon", i.next());
		assertEquals("Apple", i.next());
		assertEquals("Ape", i.next());
		assertFalse(i.hasNext());
		assertEquals("Cartwheel", trie.first().get());
		assertEquals("Ape", trie.last().get());
		checkTrieOrder(trie);
	}

	@Test
	public void testLiveIterator() {
		IndexedTrie<String> trie = Tries.strings(UTF8).nodeSource(getNodeSource()).indexed().newTrie();
		assertTrue(trie.add("One"));
		Iterator<String> i = trie.iterator();
		assertEquals ("One", i.next());
		assertFalse  (i.hasNext());
		assertTrue   (trie.add("Two"));
		assertTrue   (i.hasNext());
		assertEquals ("Two", i.next());
		assertFalse  (i.hasNext());
		assertTrue   (trie.add("Three"));
		assertFalse  (i.hasNext());

		i = trie.iterator();
		assertTrue   (i.hasNext());
		assertEquals ("One", i.next());
		assertTrue   (trie.remove("Three"));
		assertEquals ("Two", i.next());
		assertFalse  (i.hasNext());

		checkTrieOrder(trie);
	}
	
	@Test
	public void testCombinations() {
		Random r = new Random(0L);
		for (int i = 0; i < 1000; i++) {
			String[] strs = new String[4];
			for (int j = 0; j < strs.length; j++) {
				strs[j] = randStr(r, 8);
			}
			describe("STRINGS: " + i + ":" + Arrays.asList(strs));
			IndexedTrie<String> trie = Tries.strings(UTF8).nodeSource(getNodeSource()).indexed().newTrie();
			trie.addAll(Arrays.asList(strs));
			for (String str : strs) {
				dump("REMOVING " + str, trie);
				trie.remove(str);
			}
		}
	}
	
	private static String randStr(Random r, int maxLen) {
		int len = r.nextInt(maxLen);
		StringBuilder sb = new StringBuilder(len);
		for (int i = 0; i < len; i++) {
			sb.append((char)(97 + r.nextInt(3)));
		}
		return sb.toString();
	}

	@Test
	public void testSubTries() {
		testSubTries(true);
		testSubTries(false);
	}

	private void testSubTries(boolean indexed) {
		Tries<String> tries = Tries.strings(UTF8).nodeSource(getNodeSource());
		Trie<String> trie;
		IndexedTrie<String> itrie;
		if (indexed) {
			itrie = tries.indexed().newTrie();
			trie = itrie;
		} else {
			itrie = null;
			trie = tries.newTrie();
		}
		trie.add("Cat");
		trie.add("Hot");
		trie.add("Puppy");
		Trie<String> sub = trie.subTrie("Hot");
		assertFalse(sub.isEmpty());
		assertTrue(sub.add("Hotdog"));
		assertTrue(sub.add("Hotrod"));
		assertEquals("Cat", trie.first().get());
		assertEquals("Puppy", trie.last().get());
		assertEquals("Hot", sub.first().get());
		assertEquals("Hotrod", sub.last().get());
		assertEquals(3, sub.size());
		assertEquals(1, sub.subTrie("Hotd").size());
		assertTrue(sub.contains("Hot"));
		assertTrue(sub.remove("Hot"));
		assertFalse(sub.contains("Hot"));
		assertEquals(2, sub.size());
		Iterator<String> it = sub.iterator();
		assertTrue(it.hasNext());
		assertEquals("Hotdog", it.next());
		assertTrue(it.hasNext());
		assertEquals("Hotrod", it.next());
		assertFalse(it.hasNext());
		assertTrue(sub.contains("Hotdog"));
		assertTrue(trie.remove("Hotdog"));
		assertFalse(sub.contains("Hotdog"));
		assertTrue(sub.remove("Hotrod"));
		assertFalse(trie.contains("Hotrod"));
		assertTrue(sub.isEmpty());
		assertFalse(trie.isEmpty());
		assertEquals(0, sub.size());
		assertFalse(sub.iterator().hasNext());
		sub.add("Hot diggity!");
		assertFalse(sub.isEmpty());
		assertEquals(3, trie.size());
		trie.add("Hot mess");
		if (itrie != null) {
			IndexedTrie<String> isub = (IndexedTrie<String>) sub;
			assertEquals("Hot diggity!", isub.get(0));
			assertEquals("Hot mess", isub.get(1));
			assertEquals(0, isub.indexOf("Hot diggity!"));
			assertEquals(1, isub.indexOf("Hot mess"));
			assertEquals(-1, isub.indexOf("Hot apple pie"));
			assertEquals(-2, isub.indexOf("Hot grits"));
			assertEquals(-3, isub.indexOf("Hot potato"));
		}

		checkIAE(() -> sub.add("Got"));

		checkTrieOrder(trie);
	}

	@Test
	public void testAsList() {
		IndexedTrie<String> trie = Tries.strings(UTF8).nodeSource(getNodeSource()).indexed().newTrie();
		List<String> list = trie.asList();
		assertEquals(0, list.size());
		assertTrue(list.isEmpty());
		assertFalse(list.iterator().hasNext());
		trie.add("Adult");
		assertEquals(1, list.size());
		assertFalse(list.isEmpty());
		assertTrue(list.iterator().hasNext());
		trie.add("Baby");
		trie.add("Child");
		assertEquals(3, list.size());
		assertEquals(0, list.indexOf("Adult"));
		assertEquals(1, list.indexOf("Baby"));
		assertEquals(2, list.indexOf("Child"));
		assertEquals("Adult", list.get(0));
		assertEquals("Baby", list.get(1));
		assertEquals("Child", list.get(2));
		assertFalse(list.listIterator(3).hasNext());
		ListIterator<String> it = list.listIterator(2);
		assertTrue(it.hasNext());
		assertEquals("Child", it.next());
		assertFalse(it.hasNext());
		assertTrue(it.hasPrevious());
		assertEquals("Child", it.previous());
		assertEquals("Child", it.next());
		assertEquals(3, it.nextIndex());
		assertEquals("Child", it.previous());
		assertEquals("Baby", it.previous());
		assertEquals("Adult", it.previous());
		assertFalse(it.hasPrevious());
		assertEquals(-1, it.previousIndex());
		assertEquals("Adult", it.next());
		assertEquals("Baby", it.next());
		it.remove();
		assertEquals("Child", it.next());
		it.remove();
		assertEquals("Adult", it.previous());
		trie.clear();
		assertFalse(it.hasNext());
		assertFalse(it.hasPrevious());
	}
	
	@Test
	public void testAsSet() {
		Trie<String> trie = Tries.strings(UTF8).nodeSource(getNodeSource()).indexed().newTrie();
		Set<String> set = trie.asSet();
		assertTrue(set.isEmpty());
		set.add("Scott");
		assertFalse(set.isEmpty());
		assertEquals(1, set.size());
		assertTrue(trie.contains("Scott"));
		trie.add("Ramona");
		dump("Scott and Ramona", trie);
		assertTrue(set.contains("Ramona"));
		Set<String> subset = trie.subTrie("Scott").asSet();
		assertEquals(1, subset.size());
		assertFalse(subset.contains("Ramona"));
		assertTrue(subset.add("Scott Pilgrim"));
		dump("Scott Pilgrim", trie);
		assertFalse(subset.add("Ramona Flowers"));
		dump("Before Iter", trie);
		Iterator<String> it = subset.iterator();
		assertTrue(it.hasNext());
		assertEquals("Scott", it.next());
		it.remove();
		assertTrue(it.hasNext());
		assertEquals("Scott Pilgrim", it.next());
		it.remove();
		assertFalse(it.hasNext());
		assertTrue(subset.isEmpty());
	}
	
	@Test
	public void testCaseInsensitive() {
		IndexedTrie<String> trie = Tries.strings(ASCII).nodeSource(getNodeSource()).byteOrder((a, b) -> {
			int ca = Character.toUpperCase(a & 0xff);
			int cb = Character.toUpperCase(b & 0xff);
			return ca - cb;
		}).indexed().newTrie();
		trie.add("Aberdeen");
		assertTrue(trie.contains("Aberdeen"));
		assertTrue(trie.contains("ABERDEEN"));
		assertTrue(trie.contains("aberdeen"));
		trie.add("abergavenny");
		assertTrue(trie.contains("Aberdeen"));
		assertTrue(trie.contains("Abergavenny"));
		trie.add("Birmingham");
		Iterator<String> it = trie.iterator();
		assertTrue(it.next().equalsIgnoreCase("Aberdeen"));
		assertTrue(it.next().equalsIgnoreCase("Abergavenny"));
		assertTrue(it.next().equalsIgnoreCase("Birmingham"));
		assertTrue(trie.subTrie("ABER").first().get().equalsIgnoreCase("Aberdeen"));
		assertTrue(trie.subTrie("ABER").last().get().equalsIgnoreCase("Abergavenny"));
	}

	@Test
	public void testLongPrefix() {
		Trie<String> trie = Tries.strings(UTF8).nodeSource(getNodeSource()).indexed().newTrie();
		trie.subTrie("Some very long prefix which is almost certain to exceed the default capacity").iterator();
	}
	
	@Test
	public void testMutability() {
		Trie<String> trie = Tries.strings(UTF8).nodeSource(getNodeSource()).newTrie();
		trie.add("Moo");

		Trie<String> iv = trie.immutableView();
		assertTrue(iv.contains("Moo"));
		checkImm(() -> iv.remove("Moo"));
		dump("BEFORE REMOVE", trie);
		assertTrue(trie.remove("Moo"));
		dump("AFTER REMOVE", trie);
		assertTrue(trie.isEmpty());
		assertFalse(iv.contains("Moo"));
		checkImm(() -> iv.add("Moo"));

		Trie<String> mc = trie.mutableCopy();
		dump("BEFORE RE-ADD", trie);
		assertTrue(trie.add("Moo"));
		dump("AFTER RE_ADD", trie);
		assertFalse(mc.contains("Moo"));
		mc.add("Quack");
		assertFalse(trie.contains("Quack"));
		
		Trie<String> ic = trie.immutableCopy();
		assertTrue(ic.contains("Moo"));
		assertFalse(ic.contains("Quack"));
		checkImm(() -> ic.add("Quack"));
	}
	
	@Test
	@Ignore
	public void commonWords() throws IOException {
		List<String> words = readWords();
		Trie<String> trie = Tries.strings(ASCII).newTrie();
		trie.addAll(words);
		trie.compactStorage();
		System.out.println(trie.size() + " words require " + trie.storageSizeInBytes() + " bytes");
	}

	private <E> void checkTrieOrder(Trie<E> trie) {
		Comparator<E> c = trie.comparator();
		E previous = null;
		for (E element : trie) {
			if (previous != null) {
				assertTrue(c.compare(element, previous) > 0);
				assertTrue(c.compare(previous, element) < 0);
				assertEquals(0, c.compare(element, element));
			}
			previous = element;
		}
	}

	@Test
	public void testCopy() {
		testCopy(TrieNodeSource.forSpeed());
		testCopy(TrieNodeSource.forCompactness());
		testCopy(TrieNodeSource.forCompactLookups());
	}

	private void testCopy(TrieNodeSource source) {
		List<String> strs = asList("One", "Once", "Onto", "Ontology", "Two");
		Tries<String> tries = Tries.strings(ASCII).byteOrder(ByteOrder.UNSIGNED).nodeSource(source);
		Trie<String> trie = tries.newTrie();
		trie.addAll(strs);
		IndexedTrie<String> indexed = tries.indexed().newTrie();
		indexed.addAll(strs);
		IndexedTrie<String> reversed = tries.byteOrder(ByteOrder.REVERSE_UNSIGNED).indexed().copyTrie(trie);
		reversed.addAll(strs);
		tries.byteOrder(ByteOrder.UNSIGNED).nodeSource(getNodeSource());

		// test unindexed copy of unindexed
		Trie<String> copy = tries.copyTrie(trie);
		assertTrue(copy.containsAll(strs));

		// test indexed copy of indexed
		IndexedTrie<String> copy2 = tries.indexed().copyTrie(indexed);
		assertTrue(copy2.containsAll(strs));
		for (String str : strs) {
			assertEquals(indexed.indexOf(str), copy2.indexOf(str));
		}

		// test indexed copy of unindexed
		IndexedTrie<String> copy3 = tries.indexed().copyTrie(trie);
		assertTrue(copy3.containsAll(strs));
		for (String str : strs) {
			assertEquals(indexed.indexOf(str), copy3.indexOf(str));
		}

		// test indexed copy of reversed
		IndexedTrie<String> copy4 = tries.indexed().copyTrie(reversed);
		assertTrue(copy4.containsAll(strs));
		for (String str : strs) {
			assertEquals(indexed.indexOf(str), copy4.indexOf(str));
		}
	}

	@Test
	public void testRemoveAtIndex() {
		Function<List<String>, List<String>> arrange = list -> list.stream().sorted().distinct().collect(Collectors.toList());
		IndexedTrie<String> trie = Tries.strings(ASCII).nodeSource(getNodeSource()).indexed().newTrie();
		List<String> words = asList("There was a young lady of Niger who smiled as she rode on a tiger They returned from the ride with the lady inside and the smile on the face of the tiger".split("\\s+"));

		{ // check basic removal
			trie.addAll(words);
			List<String> remaining = arrange.apply(words);
			assertEquals(remaining, trie.asList());

			Random r = new Random(0L);
			while (!remaining.isEmpty()) {
				int index = r.nextInt(remaining.size());
				assertEquals("Mistake removing " + index + " of " + remaining.size(), remaining.get(index), trie.remove(index));
				remaining.remove(index);
				assertEquals(remaining, trie.asList());
			}
		}

		{ // check removal from sub trie
			trie.addAll(words);
			List<String> remaining = arrange.apply(words);
			assertEquals(remaining, trie.asList());
			IndexedTrie<String> sub = trie.subTrie("a");
	
			assertEquals(3, sub.size());
			assertEquals("and", sub.remove(1));
			assertEquals("a", sub.remove(0));
			assertEquals("as", sub.remove(0));
			assertTrue(sub.isEmpty());

			remaining.removeIf(s -> s.startsWith("a"));
			assertEquals(remaining, trie.asList());
		}
	}

	@Test
	public void testAsBytes() {
		Tries<String> tries = Tries.strings(UTF8).nodeSource(getNodeSource());
		Trie<String> trie = tries.newTrie();
		Trie<byte[]> bytes = trie.asBytesTrie();
		assertTrue(bytes.isEmpty());
		trie.add("dog");
		assertTrue(bytes.contains(bytes("dog")));

		checkImm(()-> bytes.remove(bytes("dog")));
		
		IndexedTrie<String> indexed = tries.indexed().newTrie();
		IndexedTrie<byte[]> indexedBytes = indexed.asBytesTrie();
		
		indexed.add("cat");
		assertTrue(indexed.contains("cat"));
		assertTrue(indexedBytes.contains(bytes("cat")));

		checkImm(()-> indexedBytes.remove(bytes("cat")));
	}

	@Test
	public void testSubTrieAtPrefix() {
		Tries<String> tries = Tries.strings(UTF8).nodeSource(getNodeSource());
		Trie<String> t = tries.newTrie();
		Trie<String> s = t.subTrieAtPrefix(new byte[] {65});
		assertTrue(s.isEmpty());
		t.add("Pear");
		assertTrue(s.isEmpty());
		t.add("Apple");
		assertFalse(s.isEmpty());
		assertTrue(s.contains("Apple"));
		s.add("Apricot");
		assertTrue(t.contains("Apricot"));
		checkIAE(() -> s.add("Peach"));
		checkIAE(() -> s.subTrieAtPrefix(new byte[] {66}));
		Trie<String> r = s.subTrieAtPrefix(new byte[] {65, 112, 112});
		assertTrue(r.contains("Apple"));
		assertFalse(r.contains("Apricot"));
	}
	
	@Test
	public void testSerialization() {
		testSerialization(false);
		testSerialization(true);
	}

	private void testSerialization(boolean indexed) {
		Tries<String> tries = Tries.strings(ASCII).nodeSource(getNodeSource()).indexed(indexed);

		// test empty
		Trie<String> empty = tries.newTrie();
		checkSerialization(tries, empty, true);

		// test singleton
		Trie<String> singleton = tries.newTrie();
		singleton.add("Cat");
		checkSerialization(tries, singleton, indexed);

		// test pair
		Trie<String> pair = tries.newTrie();
		pair.add("Cat");
		pair.add("Dog");
		checkSerialization(tries, pair, indexed);

		// test large
		Random r = new Random(0L);
		for (int t = 0; t < 50; t++) {
			int size = 5000 + r.nextInt(5000);
			List<String> strs = new ArrayList<>();
			for (int i = 0; i < size; i++) {
				strs.add(Integer.toString(r.nextInt(size)));
			}
	
			Trie<String> large = tries.newTrie();
			large.addAll(strs);
			Set<String> set = new HashSet<>(strs);
			for (int i = 0; i < strs.size(); i += 10) {
				String str = strs.get(i);
				large.remove(str);
				set.remove(str);
			}
			assertEquals(set, large.asSet());
			checkSerialization(tries, large, indexed);
		}
	}

	@Test
	public void testIndexOf() {
		IndexedTrie<String> trie = Tries.strings(ASCII).nodeSource(getNodeSource()).indexed().newTrie();
		assertEquals(-1, trie.indexOf(""));
		assertEquals(-1, trie.indexOf("absent"));
		trie.add("");
		assertEquals(0, trie.indexOf(""));
		trie.add("present");
		assertEquals(1, trie.indexOf("present"));
		assertEquals(-2, trie.indexOf("absent"));
	}

}
