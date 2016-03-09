package com.tomgibara.tries;

import com.tomgibara.fundament.Bijection;

final class AdaptedSerialization<E,F> implements TrieSerialization<F> {
	
	private final TrieSerialization<E> serial;
	private final Bijection<E, F> mapping;
	
	AdaptedSerialization(TrieSerialization<E> serial, Bijection<E, F> mapping) {
		this.serial = serial;
		this.mapping = mapping;
	}
	
	// byte related methods
	
	@Override
	public byte[] buffer() {
		return serial.buffer();
	}

	@Override
	public int capacity() {
		return serial.capacity();
	}
	
	@Override
	public int length() {
		return serial.length();
	}
	
	@Override
	public void pop() {
		serial.pop();
	}
	
	@Override
	public void push(byte b) {
		serial.push(b);
	}

	@Override
	public void trim(int newLength) {
		serial.trim(newLength);
	}

	// value related methods
	
	@Override
	public F get() {
		return mapping.apply(serial.get());
	}
	
	@Override
	public boolean isSerializable(Object obj) {
		return mapping.isInRange(obj);
	}

	@Override
	public void set(F f) {
		serial.set(mapping.disapply(f));
	}

	// serialization methods

	@Override
	public TrieSerialization<F> resetCopy(int capacity) {
		return new AdaptedSerialization<>(serial.resetCopy(), mapping);
	}

	@Override
	public <G> TrieSerialization<G> adapt(Bijection<F, G> adapter) {
		return new AdaptedSerialization<>(serial, adapter.compose(mapping));
	}

}
