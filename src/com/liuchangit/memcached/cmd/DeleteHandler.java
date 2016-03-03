package com.liuchangit.memcached.cmd;

import com.liuchangit.memcached.cache.MemStore;

public class DeleteHandler implements CommandHandler {

	private boolean result;

	@Override
	public void handle(String[] args, byte[] data) {
		String key = args[0];
		result = MemStore.delete(key);
	}

	@Override
	public byte[] getResult() {
		return result ? DELETED : NOT_FOUND;
	}
	
}
