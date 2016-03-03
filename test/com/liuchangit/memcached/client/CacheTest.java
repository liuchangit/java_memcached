package com.liuchangit.memcached.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import net.spy.memcached.internal.OperationFuture;

import org.junit.Test;

public class CacheTest extends TestBase {
	
	@Test
	public void testSet() throws Exception {
		String key = "abc";
		int exp = 1000;
		String value = "cache value";
		OperationFuture<Boolean> fu = mcc.set(key, exp, value);
		assertTrue(fu.get(1, TimeUnit.SECONDS));
		
		String value2 = "cache value2";
		OperationFuture<Boolean> fu2 = mcc.set(key, exp, value2);
		assertTrue(fu2.get(1, TimeUnit.SECONDS));
		
		mcc.delete(key);
	}
	
	@Test
	public void testGet() throws Exception {
		String key = "abc";
		Object value = mcc.get(key);
		assertNull(value);
		
		int exp = 1000;
		String val = "cache value";
		OperationFuture<Boolean> fu = mcc.set(key, exp, val);
		fu.get();
		
		value = mcc.get(key);
		assertEquals(val, value);
		
		String val2 = "cache value2";
		fu = mcc.set(key, exp, val2);
		fu.get();
		value = mcc.get(key);
		assertEquals(val2, value);
		
		mcc.delete(key);
	}
	
	@Test
	public void testDelete() throws Exception {
		String key = "abc";
		OperationFuture<Boolean> fu = mcc.delete(key);
		assertFalse(fu.get());
		
		int exp = 1000;
		String value = "cache value";
		fu = mcc.set(key, exp, value);
		fu.get();
		
		fu = mcc.delete(key);
		assertTrue(fu.get());
	}
	
	@Test
	public void testBinary() throws Exception {
		String key = "binkey";
		Object value = mcc.get(key);
		assertNull(value);
		
		OperationFuture<Boolean> fu = mcc.delete(key);
		assertFalse(fu.get());
		
		byte[] binValue = "binary cache value".getBytes();
		fu = mcc.set(key, 1000, binValue);
		fu.get();
		
		byte[] value2 = (byte[])mcc.get(key);
		assertTrue(Arrays.equals(binValue, value2));
		
		OperationFuture<Boolean> fu2 = mcc.delete(key);
		assertTrue(fu2.get());
	}
	
	@Test
	public void testString() throws Exception {
		String key = "strkey";
		Object value = mcc.get(key);
		assertNull(value);
		
		OperationFuture<Boolean> fu = mcc.delete(key);
		assertFalse(fu.get());
		
		String strValue = "string cache value";
		fu = mcc.set(key, 1000, strValue);
		fu.get();
		
		Object value2 = mcc.get(key);
		assertEquals(strValue, value2);
		
		OperationFuture<Boolean> fu2 = mcc.delete(key);
		assertTrue(fu2.get());
	}
	
	@Test
	public void testPojo() throws Exception {
		String key = "objkey";
		Object value = mcc.get(key);
		assertNull(value);
		
		OperationFuture<Boolean> fu = mcc.delete(key);
		assertFalse(fu.get());
		
		Pojo obj = Pojo.get(123);
		fu = mcc.set(key, 1000, obj);
		fu.get();
		
		Pojo value2 = (Pojo)mcc.get(key);
		assertTrue(obj.equals(value2));
		
		OperationFuture<Boolean> fu2 = mcc.delete(key);
		assertTrue(fu2.get());
	}
	
	@Test
	public void testExpire() throws Exception {
		String key = "expirekey";
		String value = "short live cache value";
		int expire = 3;	//3s
		OperationFuture<Boolean> fu = mcc.set(key, expire, value);
		fu.get();
		
		Object value2 = mcc.get(key);
		assertEquals(value, value2);
		
		Thread.sleep(5*1000);	//sleep 5s
		value2 = mcc.get(key);
		assertNull(value2);
		
		mcc.delete(key);
	}
}
