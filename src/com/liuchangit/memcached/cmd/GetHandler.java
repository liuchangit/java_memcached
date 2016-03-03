package com.liuchangit.memcached.cmd;

import java.io.ByteArrayOutputStream;

import com.liuchangit.memcached.ErrorName;
import com.liuchangit.memcached.cache.MemStore;
import com.liuchangit.memcached.cache.Value;
import com.liuchangit.memcached.util.Loggers;

public class GetHandler implements CommandHandler {
	
	private String[] keys;
	private Value[] values;

	@Override
	public void handle(String[] args, byte[] data) {
		this.keys = args;
		this.values = MemStore.get(args);
	}

	@Override
	public byte[] getResult() {
		ByteArrayOutputStream os = new ByteArrayOutputStream(1024);
		byte[] result;
		try {
			for (int i = 0; i < keys.length; i++) {
				String key = keys[i];
				Value value = values[i];
				if (value != null) {
					int flags = value.getFlags();
					int length = value.getLength();
					StringBuilder tmp = new StringBuilder("VALUE ").append(key).append(" ").append(flags).append(" ").append(length).append("\r\n");
					os.write(tmp.toString().getBytes());
					os.write(value.getData());
					os.write('\r');
					os.write('\n');
				}
			}
			os.write(END);
			result = os.toByteArray();
		} catch (Exception e) {
			e.printStackTrace();
			result = ErrorName.SERVER_ERROR.getBytes(e.getMessage());
			Loggers.CONSOLE.error("error in get handler", e);
		}
		return result;
	}
	
}
