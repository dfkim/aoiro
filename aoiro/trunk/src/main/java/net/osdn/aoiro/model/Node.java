package net.osdn.aoiro.model;

import java.util.LinkedList;
import java.util.List;

public class Node<T> {
	
	private boolean isSubTotal;
	private int level;
	private String name;
	private T value;
	private List<Node<T>> children = new LinkedList<Node<T>>();

	public Node(int level, String name) {
		this(level, name, null);
	}
	
	public Node(int level, String name, T value) {
		this.level = level;
		this.name = name;
		this.value = value;
	}
	
	public boolean isSubTotal() {
		return isSubTotal;
	}
	
	public void setSubTotal(boolean b) {
		this.isSubTotal = b;
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
	
	public T getValue() {
		return value;
	}
	
	public void setValue(T value) {
		this.value = value;
	}
	
	public List<Node<T>> getChildren() {
		return children;
	}
}
