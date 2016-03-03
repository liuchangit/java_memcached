package com.liuchangit.memcached.cache;

class LRU {
	static class LinkNode {
		String key;
		int kvlen;
		LinkNode before;
		LinkNode after;
		
		public LinkNode(String key, int kvlen) {
			this.key = key;
			this.kvlen = kvlen;
		}
	}
	
	//double linked list
	private LinkNode head;
	
	public LRU() {
		head = new LinkNode(null, 0);
		head.before = head;
		head.after = head;
	}
	
	public LinkNode find(String key) {
		for (LinkNode node = head.after; node != head; node = node.after) {
			if (node.key.equals(key)) {
				return node;
			}
		}
		return null;
	}
	
	public void insert(LinkNode node) {
		node.after = head.after;
		head.after.before = node;
		head.after = node;
		node.before = head;
	}
	
	public void moveToHead(LinkNode node) {
		node.before.after = node.after;
		node.after.before = node.before;
		insert(node);
	}
	
	public LinkNode removeLast() {
		LinkNode last = head.before;
		if (last != head) {
			remove(last);
		} else {
			last = null;
		}
		return last;
	}
	
	public void remove(LinkNode node) {
		node.before.after = node.after;
		node.after.before = node.before;
		node.after = node.before = null;
	}
}
