package com.tomgibara.tries;

import static org.junit.Assert.assertEquals;

import java.util.Comparator;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

public class ByteOrderTest {

	@Test
	public void testComparator() {
		testComparator(ByteOrder.UNSIGNED);
		testComparator(ByteOrder.SIGNED);
		testComparator(ByteOrder.REVERSE_UNSIGNED);
		testComparator(ByteOrder.REVERSE_SIGNED);
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
	
}
