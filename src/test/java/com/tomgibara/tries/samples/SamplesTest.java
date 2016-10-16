package com.tomgibara.tries.samples;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Set;

import org.junit.Test;

import com.tomgibara.fundament.Bijection;
import com.tomgibara.streams.StreamBytes;
import com.tomgibara.streams.Streams;
import com.tomgibara.streams.WriteStream;
import com.tomgibara.tries.IndexedTrie;
import com.tomgibara.tries.Trie;
import com.tomgibara.tries.Tries;
import com.tomgibara.tries.nodes.TrieNodeSource;

public class SamplesTest {

	private static final Charset UTF8 = Charset.forName("UTF8");

	private final WriteStream stream = Streams.bytes().writeStream();
	private final Set<URI> uris = Collections.singleton(URI.create("http://www.example.com"));

	@Test
	public void testBasic() {

		// creating a trie of strings
		Trie<String> strs
			= Tries.serialStrings(UTF8).newTrie();

		//populate a trie
		strs.add("vodka");
		strs.add("Kahlúa");
		strs.add("cream");

		// interrogate trie
		strs.contains("vodka");    // true
		strs.first();              // "Kahlúa"
		strs.last();               // "vodka"
		strs.iterator();           // (supports removal)

		// other properties
		strs.isEmpty();            // false
		strs.isMutable();          // true
		strs.size();               // 3
		strs.comparator();         // (iteration order)
		strs.storageSizeInBytes(); // (approximate)

		// global operations
		strs.compactStorage();     // optimize trie
		strs.clear();              // remove all elements
		strs.writeTo(stream);      // persist the trie

		// views
		strs.subTrie("vod");       // live sub-trie
		strs.immutableView();      // live read-only view
		strs.asSet();              // live view as a set
		strs.asBytesTrie();        // read-only view of bytes

		//reset
		{
			strs.add("vodka");
			strs.add("Kahlúa");
			strs.add("cream");
		}

		// an indexed trie copy
		IndexedTrie<String> indx
			= Tries.serialStrings(UTF8).indexed().copyTrie(strs);

		// additional index based methods
		indx.get(0);               // "Kahlúa"
		indx.indexOf("cream");     // 1
		indx.remove(1);            // "cream"
		indx.asList();             // (only supports removal)

	}

	@Test
	public void testAdvanced() {
		// create an adapter to avoid writing a dedicated serializer
		Bijection<String, URI> adapter = Bijection.fromFunctions(
				String.class, URI.class,
				URI::create,  URI::toString
				);

		// choose a node source which is compact and fast for lookups
		TrieNodeSource source = Tries.sourceForCompactLookups();

		// create a factory for the tries we want
		Tries<URI> tries = Tries
				.serialStrings(UTF8)   // use string serialization
				.nodeSource(source)    // use the node source we want
				.adaptedWith(adapter); // and adapt it to store URIs

		// (this allocates some temporary storage for this example)
		StreamBytes bytes = Streams.bytes();

		Trie<URI> trie = tries.newTrie();  // create a trie
		trie.addAll(uris);                 // populate it
		trie.compactStorage();             // compact it
		trie.writeTo(bytes.writeStream()); // persist it
		trie = trie.immutableView();       // make it immutable

		// ... use the trie and dispose of it from memory
		//     then later, when we want the trie back, simply ...

		trie = tries.readTrie(bytes.readStream()).immutableView();

		trie.containsAll(uris); // true - the trie has been restored
	}


}
