/*
 * Copyright 2016 Tom Gibara
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
	public TrieSerialization<F> copy() {
		return new AdaptedSerialization<>(serial.copy(), mapping);
	}

	@Override
	public TrieSerialization<F> resetCopy(int capacity) {
		return new AdaptedSerialization<>(serial.resetCopy(), mapping);
	}

	@Override
	public <G> TrieSerialization<G> adapt(Bijection<F, G> adapter) {
		return new AdaptedSerialization<>(serial, adapter.compose(mapping));
	}

}
