package com.liuchangit.memcached;

public enum ErrorName {
	ERROR, CLIENT_ERROR, SERVER_ERROR;
	
	private byte[] bytes;
	
	private ErrorName() {
		this.bytes = (this.name() + "\r\n").getBytes();
	}
	
	public byte[] getBytes(String msg) {
		if (msg == null || msg.length() == 0) {
			return bytes;
		} else {
			StringBuilder tmp = new StringBuilder(this.name()).append(" ").append(msg).append("\r\n");
			return tmp.toString().getBytes();
		}
	}
}
