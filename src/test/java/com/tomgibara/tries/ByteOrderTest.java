package com.tomgibara.tries;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import org.junit.Assert;
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
	public void testAll() {
		for (ByteOrder order : orders) {
			testByteOrder(order);
		}
	}
	
	private void testByteOrder(ByteOrder bo) {
		testComparator(bo);
		testEquality(bo);
		testHashCode(bo);
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
}
