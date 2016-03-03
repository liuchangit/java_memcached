package com.liuchangit.memcached.util;

import com.liuchangit.comlib.config.Config;
import com.liuchangit.comlib.config.PropertiesConfig;

public class Configs {
	
	private static final Config CONFIG = new PropertiesConfig("config");
	
	public static final boolean DEBUG = CONFIG.getBool("DEBUG");
	public static final int PORT = CONFIG.getInt("PORT");
	public static final int CLIENT_TIMEOUT = CONFIG.getInt("CLIENT_TIMEOUT");
	public static final int THREADS = CONFIG.getInt("THREADS");
	public static final int MAX_KEY_LENGTH = CONFIG.getInt("MAX_KEY_LENGTH");
	public static final int MAX_VALUE_LENGTH = CONFIG.getInt("MAX_VALUE_LENGTH");
	public static final int MAX_MEMSTORE_BYTES = CONFIG.getInt("MAX_MEMSTORE_BYTES");

}
