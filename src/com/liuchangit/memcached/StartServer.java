package com.liuchangit.memcached;

import com.liuchangit.memcached.util.Configs;

public class StartServer {
	public static void main(String[] args) throws Exception {
		int port = Configs.PORT;
		int clientTimeout = Configs.CLIENT_TIMEOUT;
		int threads = Configs.THREADS;
		
		MemcachedServer server = new MemcachedServer(port, clientTimeout, threads);
		server.serve();
	}
}
