package com.tomgibara.tries;

import java.util.Iterator;

public interface Trie<E> extends Iterable<E> {

	int size();

	//TODO replace with separate Trie/IndexedTrie interfaces
	boolean isIndexed();
	
	boolean add(E e);

	boolean contains(E e);

	E get(int index);

	boolean remove(E e);
	
	long storageSizeInBytes();
	
	void compactStorage();

	default boolean isEmpty() {
		return size() == 0;
	}
	
	default void clear() {
		for (Iterator<E> i = iterator(); i.hasNext(); i.remove()) i.next();
	}
	
	default boolean addAll(Iterator<E> iterator) {
		if (iterator == null) throw new IllegalArgumentException("null iterator");
		boolean mutated = false;
		while (iterator.hasNext()) {
			E e = iterator.next();
			mutated = add(e) || mutated;
		}
		return mutated;
	}

	default boolean addAll(Iterable<E> iterable) {
		if (iterable == null) throw new IllegalArgumentException("null iterable");
		return addAll(iterable.iterator());
	}
	
	default boolean containsAll(Iterator<E> iterator) {
		//TODO could implement optimally by building eq
		if (iterator == null) throw new IllegalArgumentException("null iterator");
		while (iterator.hasNext()) {
			if (!contains(iterator.next())) return false;
		}
		return true;
	}

	default boolean containsAll(Iterable<E> iterable) {
		if (iterable == null) throw new IllegalArgumentException("null iterable");
		return containsAll(iterable.iterator());
	}

	Iterator<E> iterator(E from);

	Trie<E> subTrie(E root);
}
