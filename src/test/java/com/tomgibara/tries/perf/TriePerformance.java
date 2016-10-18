package com.tomgibara.tries.perf;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.tomgibara.streams.StreamBytes;
import com.tomgibara.streams.Streams;
import com.tomgibara.tries.Trie;
import com.tomgibara.tries.Tries;
import com.tomgibara.tries.WordsTest;
import com.tomgibara.tries.nodes.TrieNodeSource;

public class TriePerformance {

	static int dummy;

	public static final Charset UTF8 = Charset.forName("UTF8");
	private static final int DEFAULT_WARMUPS = 50;
	private static final int DEFAULT_TRIALS = 100;
	private static final String fmt = "%6s %8s %8s %8s % 8.3fms %6.0fKb";

	// 112k words
	static List<String> readWords() {
		List<String> words = new ArrayList<String>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(WordsTest.class.getResourceAsStream("/more_words.txt")))) {
			while (true) {
				String word = reader.readLine();
				if (word == null) break;
				words.add(word);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return words;
	}

	static long timing(Runnable r) {
		long start = System.currentTimeMillis();
		r.run();
		long finish = System.currentTimeMillis();
		return finish - start;
	}

	public static void main(String... args) {
		trial(args);
	}

	public static void trialAllFor(String source) {
		trial("build", "words", source);
		trial("compact", "words", source);
		trial("contains", "words", source);
		trial("iterate", "words", source);
		trial("persist", "words", source);
		trial("restore", "words", source);
	}

	public static void trial(String... args) {
		String tsk = args[0];
		String typ = args[1];
		String src = args[2];
		int trials = args.length > 3 ? Integer.parseInt(args[3]) : DEFAULT_TRIALS;
		int warmups = args.length > 4 ? Integer.parseInt(args[4]) : DEFAULT_WARMUPS;

		final TrieNodeSource source;
		switch (src) {
		case "speed"   : source = Tries.sourceForSpeed(); break;
		case "memory"  : source = Tries.sourceForCompactLookups(); break;
		case "lookup" : source = Tries.sourceForCompactness(); break;
		default: throw new IllegalArgumentException("invalid source: " + src);
		}

		final Setup setup;
		switch (typ) {
		case "words" : setup = new WordsSetup(); break;
		default: throw new IllegalArgumentException("invalid type: " + typ);
		}

		Task task;
		switch (tsk) {
		case "build" : task = new BuildTask(); break;
		case "compact" : task = new CompactTask(); break;
		case "contains" : task = new ContainsTask(); break;
		case "iterate" : task = new IterateTask(); break;
		case "persist" : task = new PersistTask(); break;
		case "restore" : task = new RestoreTask(); break;
		default: throw new IllegalArgumentException("invalid task: " + tsk);
		}

		setup.setSource(source);
		task.setup(setup);

		System.gc();

		// warmup
		long warmupTime = 0L;
		long warmupMemory = 0L;
		for (int i = 0; i < warmups; i++) {
			task.perform();
			warmupTime += task.time();
			warmupMemory += task.memory();
			task.reset();
		}
		System.out.println(String.format(fmt, "warmup", tsk, typ, src, warmupTime/(double)warmups, warmupMemory/1024.0/warmups));

		// trial
		long trialTime = 0L;
		long trialMemory = 0L;
		for (int i = 0; i < trials; i++) {
			task.perform();
			trialTime += task.time();
			trialMemory += task.memory();
		}
		System.out.println(String.format(fmt, "hot", tsk, typ, src, trialTime/(double)trials, trialMemory/1024.0/trials));

		task.close();
		setup.dispose();
	}

	public static void testAddAllStrings(String[] strings) {
		Tries.serialStrings(UTF8);
	}

	interface Setup {

		void setSource(TrieNodeSource source);

		void loadData();

		void createTrie();

		void addData();

		void compact();

		void contains();

		void iterate();

		void persist();

		void restore();

		void clearTrie();

		long memory();

		long storage();

		void clearStorage();

		void dispose();
	}

	static class WordsSetup implements Setup {

		private Tries<String> tries;
		private Trie<String> trie;
		private List<String> words;
		private StreamBytes bytes = Streams.bytes();

		@Override
		public void setSource(TrieNodeSource source) {
			tries = Tries.serialStrings(UTF8).nodeSource(source);
		}

		@Override
		public void loadData() {
			words = readWords();
			Collections.shuffle(words, new Random(0L));
		}

		@Override
		public void createTrie() {
			trie = tries.newTrie();
		}

		@Override
		public void addData() {
			trie.addAll(words);
		}

		@Override
		public void compact() {
			trie.compactStorage();
		}

		@Override
		public void contains() {
			int count = 0;
			for (String word : words) {
				if (trie.contains(word)) count ++;
			}
			dummy = count;
		}

		@Override
		public void iterate() {
			int count = 0;
			for (String str : trie) count++;
			dummy = count;
		}

		@Override
		public void persist() {
			trie.writeTo(bytes.writeStream());
		}

		@Override
		public void restore() {
			tries.readTrie(bytes.readStream());
		}

		@Override
		public void clearStorage() {
			bytes = Streams.bytes(bytes.length());
		}

		@Override
		public void clearTrie() {
			trie.clear();
		}

		@Override
		public long memory() {
			return trie.storageSizeInBytes();
		}

		@Override
		public long storage() {
			return bytes.length();
		}

		@Override
		public void dispose() {
			tries = null;
			trie = null;
			words = null;
		}
	}

	interface Task extends AutoCloseable {

		void setup(Setup setup);

		void perform();

		long time();

		long memory();

		void reset();

		@Override
		default void close() {}
	}

	static abstract class BaseTask implements Task {

		Setup setup;
		long time;
		long memory;

		@Override
		public long time() {
			return time;
		}

		@Override
		public long memory() {
			return memory;
		}

		@Override
		public void reset() {
		}

		@Override
		public void close() {
		}

	}

	static class BuildTask extends BaseTask {

		@Override
		public void setup(Setup setup) {
			this.setup = setup;
			setup.createTrie();
			setup.loadData();
		}

		@Override
		public void perform() {
			time = timing(() -> setup.addData());
			memory = setup.memory();
		}

		@Override
		public void reset() {
			setup.clearTrie();
		}

	}

	static class CompactTask extends BaseTask {

		@Override
		public void setup(Setup setup) {
			this.setup = setup;
			setup.createTrie();
			setup.loadData();
		}

		@Override
		public void perform() {
			setup.addData();
			time = timing(() -> setup.compact());
			memory = setup.memory();
		}

		@Override
		public void reset() {
			setup.clearTrie();
		}

	}

	static class IterateTask extends BaseTask {

		@Override
		public void setup(Setup setup) {
			this.setup = setup;
			setup.createTrie();
			setup.loadData();
			setup.addData();
			setup.compact();
		}

		@Override
		public void perform() {
			time = timing(() -> setup.iterate());
			memory = setup.memory();
		}

	}

	static class ContainsTask extends BaseTask {

		@Override
		public void setup(Setup setup) {
			this.setup = setup;
			setup.createTrie();
			setup.loadData();
			setup.addData();
			setup.compact();
		}

		@Override
		public void perform() {
			time = timing(() -> setup.contains());
			memory = setup.memory();
		}

	}

	static class PersistTask extends BaseTask {

		@Override
		public void setup(Setup setup) {
			this.setup = setup;
			setup.createTrie();
			setup.loadData();
			setup.addData();
			setup.compact();
		}

		@Override
		public void perform() {
			time = timing(() -> setup.persist());
			memory = setup.storage();
		}

		@Override
		public void reset() {
			setup.clearStorage();
		}
	}

	static class RestoreTask extends BaseTask {

		@Override
		public void setup(Setup setup) {
			this.setup = setup;
			setup.createTrie();
			setup.loadData();
			setup.addData();
			setup.compact();
		}

		@Override
		public void perform() {
			setup.persist();
			memory = setup.storage();
			time = timing(() -> setup.restore());
			setup.clearStorage();
		}

	}

}
