package com.tomgibara.tries;

import static com.tomgibara.tries.TrieTest.time;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WordsTest {
	
	private static int reps = 50;
	private static int trials = 151;
	private static List<String> allWords;

	// test harness for profiling
	public static void main(String... args) throws IOException {
		allWords = TrieTest.readWords();

		testSource(PackedTrieNodes2.SOURCE, false);
		testSource(PackedTrieNodes.SOURCE, false);
		testSource(PackedTrieNodes2.SOURCE, false);
		testSource(PackedTrieNodes.SOURCE, false);

		testSource(PackedTrieNodes2.SOURCE, true);
		testSource(PackedTrieNodes.SOURCE, true);
		testSource(PackedTrieNodes2.SOURCE, true);
		testSource(PackedTrieNodes.SOURCE, true);
	}

	private static void testSource(TrieNodeSource source, boolean shuffle) {
		List<String> words = allWords;
		if (shuffle) {
			words = new ArrayList<>(words);
			Collections.shuffle(words);
		}

		String sourceName = source.getClass().getName();
		String message = shuffle ? "Using " + sourceName + " with shuffled words" : "Using " + sourceName;
		System.out.println(message);

		Trie<String> trie = Tries.strings(Charset.forName("ASCII")).nodeSource(source).newTrie();
		for (int count = 0; count < trials; count++) {
			long insertTime = time(() -> trie.addAll(allWords));
			trie.compactStorage();
			long containsTime = time(() -> trie.containsAll(allWords));
			long removesTime = time(() -> trie.removeAll(allWords));
			if ((count % reps) == 0) System.out.println("insert " + insertTime +"  contains " + containsTime + "  removes " + removesTime);
		}
	}
}
