package com.liuchangit.memcached.cmd;



public enum Command {
	GET, SET, DELETE;
	
	public boolean isMe(String cmd) {
		if (this == GET && ("get".equals(cmd) || "gets".equals(cmd))) {
			return true;
		} else if (this == SET && "set".equals(cmd)) {
			return true;
		} else if (this == DELETE && "delete".equals(cmd)) {
			return true;
		}
		return false;
	}
}