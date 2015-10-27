package com.tomgibara.tries;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.tomgibara.storage.Stores;

public class TrieTest {

	static final Charset UTF8 = Charset.forName("UTF8");
	static final Charset ASCII = Charset.forName("ASCII");

	private static void dump(Trie<?> trie) {
		((Trie<?>) trie).dump();
	}

	private static void dump(String title, Trie<?> trie) {
		System.out.println(title);
		dump(trie);
	}

	private static void check(Trie<?> trie) {
		((Trie<?>) trie).check();
	}

	@Test
	public void testIndexes() {
		IndexedTrie<String> trie = Tries.builderForStrings(UTF8).newIndexedTrie();
		String s = "abcdedfgh";
		for (int i = s.length(); i >= 0; i--) {
			String t = s.substring(0,  i);
			trie.add(t);
			assertEquals(t, trie.get(0));
		}
		assertEquals(s.length() + 1, trie.size());
		for (int i = 0; i <= s.length(); i++) {
			String t = s.substring(0, i);
			System.out.println("GETTING " + i + " AND EXPECTING " + t);
			assertEquals(t, trie.get(i));
		}
	}

	@Test
	public void testDoubleInsertion() {
		IndexedTrie<String> trie = Tries.builderForStrings(UTF8).newIndexedTrie();
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
		Tries<String> tries = Tries.builderForStrings(UTF8);
		Trie<String> trie;
		IndexedTrie<String> itrie;
		if (indexed) {
			itrie = tries.newIndexedTrie();
			trie = itrie;
		} else {
			itrie = null;
			trie = tries.newTrie();
		}
		assertFalse(trie.contains("Moon"));
		System.out.println("ADDING MOON");
		assertTrue(trie.add("Moon"));
		System.out.println("MOON ADDED");
		assertTrue(trie.contains("Moon"));
		assertFalse(trie.contains("Moo"));
		System.out.println("ADDING MOO");
		assertTrue(trie.add("Moo"));
		System.out.println("MOO ADDED");
		assertTrue(trie.contains("Moo"));
		System.out.println("ADDING MOODY");
		assertTrue(trie.add("Moody"));
		System.out.println("MOODY ADDED");
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
		
		System.out.println("REMOVING MOODY");
		assertTrue(trie.remove("Moody"));
		System.out.println("MOODY REMOVED");
		assertFalse(trie.contains("Moody"));
		assertTrue(trie.contains("Moo"));
		assertTrue(trie.contains("Moon"));
		assertEquals(2, trie.size());

		System.out.println("REMOVING MOO");
		assertTrue(trie.remove("Moo"));
		System.out.println("MOO REMOVED");
		assertFalse(trie.contains("Moo"));
		assertTrue(trie.contains("Moon"));
		assertEquals(1, trie.size());

		dump("BEFORE", trie);
		System.out.println("REMOVING MOON");
		assertTrue(trie.remove("Moon"));
		System.out.println("MOON REMOVED");
		dump("AFTER", trie);
		assertFalse(trie.contains("Moon"));
		assertEquals(0, trie.size());
		
		System.out.println("EXPECTED EMPTY");
		
		Trie<String> asciiTrie = Tries.builderForStrings(ASCII).newTrie();
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
		Tries<Long> tries = Tries.builder(Long.class, (v,s) -> s.writeLong(v), s -> s.readLong());
		Trie<Long> trie;
		IndexedTrie<Long> itrie;
		if (indexed) {
			itrie = tries.newIndexedTrie();
			trie = itrie;
		} else {
			itrie = null;
			trie = tries.newTrie();
		}

		
		// handy debug version
//		Stores.longs(values).asList().forEach(value -> {
//			System.out.println("ADDING " + Long.toHexString(value) + " " + value);
//			trie.add(value);
//			check(trie);
//		});

		trie.addAll(Stores.longs(values).asList());

		// advanced checking of indexing
//		for (int i = 0; i < values.length; i++) {
//			long value = values[i];
//			System.out.println("ADDING " + Long.toHexString(value) + " " + value + " (" + i + ")");
//			trie.add(value);
//			System.out.println("ADDING COMPLETE");
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
			if (!unseen) System.out.println("DUPLICATE VALUE : " + watch);
			boolean contained = trie.contains(value);
			if (!contained) {
				int count = 0;
				for (Long v : trie) {
					if (v == value) throw new IllegalStateException();
					count++;
				}
				System.out.println(count);
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
//			System.out.println("COUNT " + count + " HEX: " + Long.toHexString(value));
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
//				System.out.println("SUB: " + Long.toHexString(sub) + " < " + value + " < " + Long.toHexString(sup));
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
		Trie<String> trie = Tries.builderForStrings(ASCII).byteOrder((a,b) -> Integer.compare(-(a&0xff), -(b&0xff))).newTrie();
		assertFalse(trie.first().isPresent());
		assertFalse(trie.last().isPresent());
		trie.add("Apple");
		System.out.println("ADDED APPLE");
		dump(trie);
		trie.add("Ape");
		System.out.println("ADDED APE");
		dump(trie);
		trie.add("Baboon");
		System.out.println("ADDED BABOON");
		dump(trie);
		System.out.println("ADDED CARTWHEEL");
		trie.add("Cartwheel");
		dump(trie);
		Iterator<String> i = trie.iterator();
		assertEquals("Cartwheel", i.next());
		assertEquals("Baboon", i.next());
		assertEquals("Apple", i.next());
		assertEquals("Ape", i.next());
		assertFalse(i.hasNext());
		assertEquals("Cartwheel", trie.first().get());
		assertEquals("Ape", trie.last().get());
	}

	@Test
	public void testLiveIterator() {
		IndexedTrie<String> trie = Tries.builderForStrings(UTF8).newIndexedTrie();
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
	}
	
	@Test
	public void testCombinations() {
		Random r = new Random(0L);
		for (int i = 0; i < 1000; i++) {
			String[] strs = new String[4];
			for (int j = 0; j < strs.length; j++) {
				strs[j] = randStr(r, 8);
			}
			System.out.println(":::::::::::: " + i + ":" + Arrays.asList(strs));
			IndexedTrie<String> trie = Tries.builderForStrings(UTF8).newIndexedTrie();
			trie.addAll(Arrays.asList(strs));
			for (String str : strs) {
				System.out.println(":::::::::::: " + str);
				dump(trie);
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
		Tries<String> tries = Tries.builderForStrings(UTF8);
		Trie<String> trie;
		IndexedTrie<String> itrie;
		if (indexed) {
			itrie = tries.newIndexedTrie();
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

		try {
			sub.add("Got");
			fail();
		} catch (IllegalArgumentException e) {
			/* expected */
		}
	}

	@Test
	public void testAsList() {
		IndexedTrie<String> trie = Tries.builderForStrings(UTF8).newIndexedTrie();
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
	
}
