package net.osdn.aoiro.model;

import java.util.LinkedList;
import java.util.List;

public class Node<K, V> {
	
	private int level;
	private String name;
	private K key;
	private V value;
	private List<Node<K, V>> children = new LinkedList<Node<K, V>>();

	public Node(int level, String name) {
		this(level, name, null, null);
	}
	
	public Node(int level, String name, K key) {
		this(level, name, key, null);
	}
	
	public Node(int level, String name, K key, V value) {
		this.level = level;
		this.name = name;
		this.key = key;
		this.value = value;
	}
	
	public int getLevel() {
		return level;
	}
	
	public void setLevel(int level) {
		this.level = level;
	}
	
	public String getName() {
		return name;
	}
	
	public K getKey() {
		return key;
	}
	
	public void setKey(K key) {
		this.key = key;
	}
	
	public V getValue() {
		return value;
	}
	
	public void setValue(V value) {
		this.value = value;
	}
	
	public List<Node<K, V>> getChildren() {
		return children;
	}
}
