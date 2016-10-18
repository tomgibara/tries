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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import org.junit.Test;

public class ByteOrderTest {

	Comparator<Byte> caseIns = (a, b) -> {
		int ca = Character.toUpperCase(a & 0xff);
		int cb = Character.toUpperCase(b & 0xff);
		return ca - cb;
	};

	List<ByteOrder> orders = Arrays.asList(
			ByteOrder.UNSIGNED,
			ByteOrder.SIGNED,
			ByteOrder.REVERSE_UNSIGNED,
			ByteOrder.REVERSE_SIGNED,
			ByteOrder.from(caseIns)
			);

	@Test
	public void testUnsigned() {
		ByteOrder bo = ByteOrder.UNSIGNED;
		for (int i = 0; i < 256; i++) {
			assertTrue(bo.compare((byte) i, (byte) i) == 0);
		}
		for (int i = 0; i < 255; i++) {
			assertTrue(bo.compare((byte) i, (byte) (i+1)) < 0);
			assertTrue(bo.compare((byte) (i+1), (byte) i) > 0);
		}
	}

	public void testCanonical() {
		assertSame(ByteOrder.SIGNED, ByteOrder.from(Byte::compare));
	}

	@Test
	public void testAll() {
		for (ByteOrder order : orders) {
			testByteOrder(order);
		}
	}

	private void testByteOrder(ByteOrder bo) {
		testComparator(bo);
		testEquality(bo);
		testHashCode(bo);
		testSerialization(bo);
	}

	private void testComparator(ByteOrder bo) {
		Comparator<Byte> comparator = bo.asComparator();
		Random r = new Random(0L);
		byte[] bytes = new byte[2];
		for (int i = 0; i < 1000; i++) {
			r.nextBytes(bytes);
			byte a = bytes[0];
			byte b = bytes[1];
			int c = bo.compare(a, b);
			int cc = comparator.compare(a, b);
			assertEquals(Integer.signum(c), Integer.signum(cc));
		}
	}

	private void testEquality(ByteOrder bo) {
		assertTrue(bo.equals(bo));
		ByteOrder bo2 = ByteOrder.from(bo.asComparator());
		assertTrue(bo.equals(bo2));
		assertTrue(bo2.equals(bo));
		ByteOrder rbo = ByteOrder.from( bo.asComparator().reversed() );
		assertFalse(rbo.equals(bo));
		assertFalse(bo.equals(rbo));
		ByteOrder rrbo = ByteOrder.from( rbo.asComparator().reversed() );
		assertTrue(rrbo.equals(bo));
		assertTrue(bo.equals(rrbo));
	}

	private void testHashCode(ByteOrder bo) {
		for (ByteOrder order : orders) {
			if (order.equals(bo)) assertEquals(order.hashCode(), bo.hashCode());
		}
	}

	private void testSerialization(ByteOrder bo) {
		ByteOrder copy;
		try {
			ByteArrayOutputStream boa = new ByteArrayOutputStream();
			ObjectOutputStream out = new ObjectOutputStream(boa);
			out.writeObject(bo);
			out.close();
			byte[] bytes = boa.toByteArray();
			ByteArrayInputStream bai = new ByteArrayInputStream(bytes);
			ObjectInputStream in = new ObjectInputStream(bai);
			copy = (ByteOrder) in.readObject();
			in.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
		if ( // check canonicalization
				bo == ByteOrder.SIGNED           ||
				bo == ByteOrder.UNSIGNED         ||
				bo == ByteOrder.REVERSE_SIGNED   ||
				bo == ByteOrder.REVERSE_UNSIGNED
				) {
			assertSame(bo, copy);
		} else {
			assertEquals(bo, copy);
		}
	}
}
