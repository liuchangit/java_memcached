package com.liuchangit.memcached.cache;

import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

import com.liuchangit.memcached.util.Configs;

public class MemStore {
	private static final MemStore instance = new MemStore();
	
	private int totalMemory = 0;
	private LRU lru = new LRU();
	private HashMap<String, Value> hash = new HashMap<String, Value>();
	private ReentrantLock lock = new ReentrantLock();
	
	// public interface //
	
	public static boolean set(String key, int flags, int expire, byte[] value, int kvlen) {
		return instance.internalSet(key, flags, expire, value, kvlen);
	}
	
	public static Value get(String key) {
		return instance.internalGet(key);
	}
	
	public static Value[] get(String[] keys) {
		return instance.internalGet(keys);
	}
	
	public static boolean delete(String key) {
		return instance.internalDelete(key);
	}
	
	
	// internal implementation //
	
	private boolean internalSet(String key, int flags, int expire, byte[] data, int kvlen) {
		LRU.LinkNode node = new LRU.LinkNode(key, kvlen);
		Value value = new Value(flags, expire, data.length, data);
		lock.lock();
		try {
			ensureCapacity(kvlen);
			insert(node, value);
		} finally {
			lock.unlock();
		}
		return true;
	}

	private void ensureCapacity(int kvlen) {
		totalMemory += kvlen;
		while (totalMemory > Configs.MAX_MEMSTORE_BYTES) {
			LRU.LinkNode node = lru.removeLast();
			if (node == null) {
				break;
			}
			hash.remove(node.key);
			totalMemory -= node.kvlen;
		}
	}

	private void insert(LRU.LinkNode node, Value value) {
		lru.insert(node);
		hash.put(node.key, value);
	}
	
	private Value internalGet(String key) {
		lock.lock();
		try {
			Value value = hash.get(key);
			LRU.LinkNode node = lru.find(key);
			if (value != null && value.getExpireTime() != 0 && value.getExpireTime() < System.currentTimeMillis()/1000) {	//expired
				lru.remove(node);
				hash.remove(key);
				totalMemory -= node.kvlen;
				value = null;
			} else if (node != null) {
				lru.moveToHead(node);
			}
			return value;
		} finally {
			lock.unlock();
		}
	}
	
	private Value[] internalGet(String[] keys) {
		Value[] values = new Value[keys.length];
		int i = 0;
		for (String key : keys) {
			Value value = internalGet(key);
			values[i++] = value;
		}
		return values;
	}
	
	private boolean internalDelete(String key) {
		lock.lock();
		try {
			LRU.LinkNode node = lru.find(key);
			if (node == null) {
				return false;
			}
			lru.remove(node);
			Value value = hash.remove(key);
			totalMemory -= node.kvlen;
			return value != null;
		} finally {
			lock.unlock();
		}
	}
}
