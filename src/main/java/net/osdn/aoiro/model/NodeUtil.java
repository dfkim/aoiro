package net.osdn.aoiro.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class NodeUtil {

	/** 指定したノードの下位階層から指定した名前を持つノードのリストを返します。
	 *
	 * @param root 検索を開始する最上位ノード
	 * @param name 検索対象のノードの名前
	 * @param <T> ノードの型
	 * @return 見つかったノードのリスト。対象となるノードがない場合は空のリストが返されます。
	 */
	public static <T> List<Node<T>> findByName(Node<T> root, String name) {
		List<Node<T>> list = new ArrayList<Node<T>>();

		for(Node<T> child : root.getChildren()) {
			if(child.getName().equals(name)) {
				list.add(child);
			}
			List<Node<T>> subList = findByName(child, name);
			if(subList.size() > 0) {
				list.addAll(subList);
			}
		}

		return list;
	}

	/** 指定したノードの下位階層のすべてのノードに対して祖先ノードのリストを作成します。
	 * 結果はマップで返され、各ノードがキーとなり、値として祖先ノードのリストが格納されます。
	 * 祖先ノードのリストの要素は親ノード、その親ノードのように近い祖先ノードから順に並んでいます。
	 *
	 * @param root 基準となる最上位ノード
	 * @param <T> ノードの型
	 * @return 各ノードをキーとして祖先ノードのリストを保持するマップ
	 */
	public static <T> Map<Node<T>, List<Node<T>>> createAncestors(Node<T> root) {
		Map<Node<T>, List<Node<T>>> map = new HashMap<Node<T>, List<Node<T>>>();
		LinkedList<Node<T>> ancestors = new LinkedList<Node<T>>();
		_createAncestors(root, map, ancestors);
		return map;
	}

	private static <T> void _createAncestors(Node<T> root, Map<Node<T>, List<Node<T>>> map, LinkedList<Node<T>> ancestors) {
		LinkedList<Node<T>> _ancestors = new LinkedList<Node<T>>(ancestors);
		_ancestors.addFirst(root);

		for(Node<T> child : root.getChildren()) {
			map.put(child, _ancestors);
			_createAncestors(child, map, _ancestors);
		}
	}
}
