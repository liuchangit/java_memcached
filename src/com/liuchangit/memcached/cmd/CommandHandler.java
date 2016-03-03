package com.liuchangit.memcached.cmd;


public interface CommandHandler {
	public static final byte[] STORED = "STORED\r\n".getBytes();
	public static final byte[] NOT_STORED = "NOT_STORED\r\n".getBytes();
	public static final byte[] DELETED = "DELETED\r\n".getBytes();
	public static final byte[] NOT_FOUND = "NOT_FOUND\r\n".getBytes();
	public static final byte[] END = "END\r\n".getBytes();
	
	public void handle(String[] args, byte[] data);
	public byte[] getResult();
}
