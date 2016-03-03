package com.liuchangit.memcached.client;

import net.spy.memcached.AddrUtil;
import net.spy.memcached.DefaultConnectionFactory;
import net.spy.memcached.MemcachedClient;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import com.liuchangit.memcached.StartServer;
import com.liuchangit.memcached.util.Configs;

public class TestBase {
	protected static Thread memcached;
	
	protected MemcachedClient mcc;
	
	@BeforeClass
	public static void startMemcached() throws Exception {
		memcached = new Thread() {
			public void run() {
				try {
					StartServer.main(null);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};
		memcached.start();
	}
	
	@AfterClass
	public static void stopMemcached() {
		memcached.stop();
	}
	
	@Before
	public void setUp() throws Exception {
		int port = Configs.PORT;
		String hostport = "127.0.0.1:" + port;
		MemcachedClient mcc1 = new MemcachedClient(new DefaultConnectionFactory(), AddrUtil.getAddresses(hostport));
		mcc = mcc1;
	}

}
