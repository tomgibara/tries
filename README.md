Tries
=====

A high-quality Java library providing byte-based trie implementations.
[Tries][0] are data structures based on ordered trees that serialize their
elements within their vertices or edges.

Features
--------

* **Mutability control**
  to create immutable views or copies of a trie
* **Pluggable node implementations**
  to control performance/space trade-offs
* **Indexed tries**
  to provide fast index-based lookups
* **Collections APIs**
  to expose tries as sets and lists
* **Byte based**
  to support data other than strings
* **Comprehensive API**
  (see below)

Overview
--------

The main entry-point for the library is the `Tries` class, applications wanting
to create tries, must first create a `Tries` instance by using one of its
`serial` methods to select which types of objects are to be serialized into the
tries. The application may then create a custom instance by chaining calls
(eg. by adapting the serialization, or changing the ordering) before creating
a trie via one of the methods `newTrie()`, `copyTrie()` or `readTrie()`.

Some applications may benefit from providing their own element serialization
instead of relying on those provided by the package. This is done by
implementing the `TrieSerialization` interface, and calling its
`tries()` method.

Applications may also provide their own implementation of the nodes that
store the trie data, instead of using one already provided by this package. To
do so the `TrieNodeSource` interface must be implemented which also entails
implementation of the `TrieNodes`, `TrieNode` and `TrieNodePath` interfaces.
Very few applications will require this level of control, and most will be well
served by at least one of the implementations provided by this library, and
available via the `Trie.sourceFor...()` methods.

All classes are found in the `com.tomgibara.tries` package and in its
`com.tomgibara.tries.nodes` sub-package, with full documentation available
via the javadocs packaged with the release. These are viewable online at:

<http://javadoc.io/doc/com.tomgibara.tries/tries>

Examples
--------

Creating and using a `Trie` of `String`:

```java
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
```

A more advanced scenario where an immutable trie of URIs is persisted to a byte
store:

```java
	// create an adapter to avoid writing a dedicated serializer
	Bijection<String, URI> adapter = Bijection.fromFunctions(
			String.class,        URI.class,
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
```

Performance
-----------

The following table is based on a UTF-8 encoded trie built from a 938KB file
containing a randomized list of 112,430 English words. This is a common scenario
for tries.

 Node type | Operation |  Time(ms) |Memory (KB)
:----------|:----------|----------:|----------:
speed      |build      |       46.7|       6595
 " "       |compact    |        0.0|        " "
 " "       |contains   |       37.8|        " "
 " "       |iterate    |       10.4|        " "
 " "       |persist    |        7.4|        440
 " "       |restore    |        4.7|        " "
lookup     |build      |       85.7|       3264
 " "       |compact    |        9.9|       1803
 " "       |contains   |       50.5|        " "
 " "       |iterate    |       12.4|        " "
 " "       |persist    |       10.0|        440
 " "       |restore    |       20.5|        " "
compact    |build      |       81.5|       3264
 " "       |compact    |        9.9|       1803
 " "       |contains   |       52.2|        " "
 " "       |iterate    |       11.9|        " "
 " "       |persist    |       10.0|        440
 " "       |restore    |       19.9|        " "

All measurements using Java 1.8.0_72 on an Intel Core i7-4712HQ CPU @ 2.30GHz

-----------------------------------------------
####Key:

**Node type** - corresponds to the node source used to support the tries

**Operation** - *build* a trie by adding all elements; *compact* the trie for
                reduced memory usage; perform a *contains* test for every
                element in the trie; *iterate* over every element in the trie;
                *persist* the trie into a byte stream and *restore* it.

**Time**      - the time required to perform the operation in milliseconds.

**Memory**    - the approximate memory occupied by the trie in killobytes, with
                the exception of *persist* and *restore* in which case it is the
                size of the generated byte stream.

-----------------------------------------------

Usage
-----

The tries library will be available from the Maven central repository:

> Group ID:    `com.tomgibara.tries`
> Artifact ID: `tries`
> Version:     `1.0.0`

The Maven dependency being:

    <dependency>
      <groupId>com.tomgibara.tries</groupId>
      <artifactId>tries</artifactId>
      <version>1.0.0</version>
    </dependency>

Release History
---------------

**2016.10.22** Version 1.0.0

Initial release


[0]: https://en.wikipedia.org/wiki/Trie