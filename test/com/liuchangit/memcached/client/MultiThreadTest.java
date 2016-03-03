package com.liuchangit.memcached.client;

import org.junit.Test;
import static org.junit.Assert.*;

public class MultiThreadTest extends TestBase {

	@Test
	// all threads do the same thing
	public void testCase1() throws Exception {
		
		Thread[] tasks = new Thread[10];
		for (int i = 0; i < tasks.length; i++) {
			tasks[i] = new Thread(new Task(i));
			tasks[i].start();
		}
		for (int i = 0; i < tasks.length; i++) {
			tasks[i].join();
		}
	}

	
	@Test
	// different threads do different things, then the main thread checks the result of them all at end
	public void testCase2() throws Exception {
		
		// start set tasks and wait for they stopping
		Thread[] setTasks = new Thread[10];
		for (int i = 0; i < setTasks.length; i++) {
			setTasks[i] = new Thread(new SetTask(i));
			setTasks[i].start();
		}
		for (int i = 0; i < setTasks.length; i++) {
			setTasks[i].join();
		}

		// start get tasks and wait for they stopping
		Thread[] getTasks = new Thread[10];
		for (int i = 0; i < getTasks.length; i++) {
			getTasks[i] = new Thread(new GetTask(i));
			getTasks[i].start();
		}
		for (int i = 0; i < getTasks.length; i++) {
			getTasks[i].join();
		}

		// start delete tasks and wait for they stopping
		Thread[] delTasks = new Thread[10];
		for (int i = 0; i < delTasks.length; i++) {
			delTasks[i] = new Thread(new DeleteTask(i));
			delTasks[i].start();
		}
		for (int i = 0; i < delTasks.length; i++) {
			delTasks[i].join();
		}
		
		// check result
		for (int id = 0; id < setTasks.length; id++) {
			String strkey = "string" + id;
			String pojokey = "pojo" + id;
			assertNull(mcc.get(strkey));
			assertNull(mcc.get(pojokey));
		}
	}
	

}

class Task extends TestBase implements Runnable {
	int id;
	Task(int id) {
		this.id = id;
	}
	@Override
	public void run() {
		String strkey = "string" + id;
		String str = "value" + id;
		String pojokey = "pojo" + id;
		Pojo pojo = Pojo.get(id);
		
		try {
			setUp();
			// set
			mcc.set(strkey, 1000, str).get();
			mcc.set(pojokey, 1000, pojo).get();
		
			// get
			String strval2 = (String)mcc.get(strkey);
			Pojo pojoval2 = (Pojo)mcc.get(pojokey);
			assertEquals(str, strval2);
			assertTrue(pojo.equals(pojoval2));
			
			// delete
			mcc.delete(strkey).get();
			mcc.delete(pojokey).get();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}


class SetTask extends TestBase implements Runnable {
	int id;
	SetTask(int id) {
		this.id = id;
	}
	@Override
	public void run() {
		String strkey = "string" + id;
		String str = "value" + id;
		String pojokey = "pojo" + id;
		Pojo pojo = Pojo.get(id);
		
		try {
			setUp();
			// set
			mcc.set(strkey, 1000, str).get();
			mcc.set(pojokey, 1000, pojo).get();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

class GetTask extends TestBase implements Runnable {
	int id;
	GetTask(int id) {
		this.id = id;
	}
	@Override
	public void run() {
		String strkey = "string" + id;
		String pojokey = "pojo" + id;
		
		try {
			setUp();
			// get
			String strval2 = (String)mcc.get(strkey);
			Pojo pojoval2 = (Pojo)mcc.get(pojokey);
			assertEquals("value" + id, strval2);
			assertTrue(Pojo.get(id).equals(pojoval2));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

class DeleteTask extends TestBase implements Runnable {
	int id;
	DeleteTask(int id) {
		this.id = id;
	}
	@Override
	public void run() {
		String strkey = "string" + id;
		String pojokey = "pojo" + id;
		try {
			setUp();
			// delete
			mcc.delete(strkey).get();
			mcc.delete(pojokey).get();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}