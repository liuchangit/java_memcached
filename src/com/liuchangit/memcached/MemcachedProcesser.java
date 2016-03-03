package com.liuchangit.memcached;

import com.liuchangit.memcached.cmd.Command;
import com.liuchangit.memcached.cmd.CommandHandler;
import com.liuchangit.memcached.cmd.DeleteHandler;
import com.liuchangit.memcached.cmd.GetHandler;
import com.liuchangit.memcached.cmd.SetHandler;

public class MemcachedProcesser {
	
	public byte[] process(String cmd, String[] args, byte[] data) {
		byte[] result;
		try {
			CommandHandler handler = getHandler(cmd);
			handler.handle(args, data);
			result = handler.getResult();
			if (result == null) {
				result = ErrorName.SERVER_ERROR.getBytes("unsupported command");
			}
		} catch (Exception e) {
			//SERVER_ERROR
			result = ErrorName.SERVER_ERROR.getBytes(e.getMessage());
			e.printStackTrace();
		}
		return result;
	}
	
	private CommandHandler getHandler(String cmd) throws Exception {
		if (Command.GET.isMe(cmd)) {
			return new GetHandler();
		} else if (Command.SET.isMe(cmd)) {
			return new SetHandler();
		} else if (Command.DELETE.isMe(cmd)) {
			return new DeleteHandler();
		} else {
			throw new Exception("unknown cmd");
		}
	}
}
