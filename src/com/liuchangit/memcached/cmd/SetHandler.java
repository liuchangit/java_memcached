package com.liuchangit.memcached.cmd;

import com.liuchangit.memcached.cache.MemStore;

public class SetHandler implements CommandHandler {
	
	private boolean result;

	@Override
	public void handle(String[] args, byte[] data) {
		String key = args[0];
		int flags = Integer.parseInt(args[1]);
		int expire = Integer.parseInt(args[2]);
//		int valueLength = Integer.parseInt(args[3]);
		int kvlen = key.getBytes().length + data.length;
		result = MemStore.set(key, flags, expire, data, kvlen);
	}

	@Override
	public byte[] getResult() {
		return result ? STORED : NOT_STORED;
	}

}
