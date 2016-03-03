package com.liuchangit.memcached.cache;


public class Value {
	private static final int MAX_EXPIRE_FROM_NOW = 30*86400;
	private int flags;
	private int expireTime;
	private byte[] buff;
	
	public Value(int flags, int expireTime, int len, byte[] buff) {
		this.flags = flags;
		int now = (int)(System.currentTimeMillis()/1000);
		if (expireTime != 0 && expireTime < MAX_EXPIRE_FROM_NOW) {
			expireTime = now + expireTime;
		}
		this.expireTime = expireTime;
		this.buff = buff;
	}
	
	public int getFlags() {
		return flags;
	}
	
	public int getExpireTime() {
		return expireTime;
	}
	
	public int getLength() {
		return buff.length;
	}
	
	public byte[] getData() {
		return buff;
	}
}
