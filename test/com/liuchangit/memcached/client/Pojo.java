package com.liuchangit.memcached.client;

import java.io.Serializable;



public class Pojo implements Serializable {
	private static final long serialVersionUID = -3921315922552270819L;
	
	long id;
	String text;
	double weight;
	
	static Pojo get(long id) {
		Pojo obj = new Pojo();
		obj.id = id;
		obj.text = "this is a pojo cache item";
		obj.weight = 3.1415;
		return obj;
	}
	
	public boolean equals(Pojo other) {
		return other != null && id == other.id && text.equals(other.text) && weight == other.weight;
	}
}
