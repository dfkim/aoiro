package net.osdn.aoiro.report.layout;

import net.osdn.aoiro.model.AccountTitle;
import net.osdn.aoiro.model.Amount;
import net.osdn.aoiro.model.Node;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/** 貸借対照表(B/S)を作成するための構成情報 */
public class BalanceSheetLayout {

	/** 貸借対照表(B/S)を集計するためのツリーを構成するルートノード */
	private Node<Entry<List<AccountTitle>, Amount[]>> root = new Node<>(0, "貸借対照表");

	/** 貸借対照表(B/S)で金額の符号を反転して表示する見出しのリスト */
	private Set<String> signReversedNames = new LinkedHashSet<>();

	/** 貸借対照表(B/S)に常に表示する見出しのリスト */
	private Set<String> alwaysShownNames = new LinkedHashSet<>();

	/** ゼロの場合は貸借対照表(B/S)に表示しない見出しのリスト */
	private Set<String> hiddenNamesIfZero = new LinkedHashSet<>();

	public Node<Entry<List<AccountTitle>, Amount[]>> getRoot() {
		return root;
	}

	/** 貸借対照表で符号を反転して表示する見出しのセットを返します。
	 * このセットに含まれる見出しは、金額の符号が反転して表示されます。
	 *
	 * @return 貸借対照表で符号を反転して表示する見出しのセット
	 */
	public Set<String> getSignReversedNames() {
		return signReversedNames;
	}

	/** 貸借対照表に常に表示する見出しのセットを返します。
	 * このセットに含まれる見出しは、対象となる仕訳が存在しない場合でも常に表示されます。
	 *
	 * @return 貸借対照表に常に表示する見出しのセット
	 */
	public Set<String> getAlwaysShownNames() {
		return alwaysShownNames;
	}

	/** ゼロの場合に貸借対照表に表示しない見出しのセットを返します。
	 * このセットに含まれる見出しは、合計がゼロのときは表示されません。
	 *
	 * @return ゼロの場合に貸借対照表に表示しない見出しのセット
	 */
	public Set<String> getHiddenNamesIfZero() {
		return hiddenNamesIfZero;
	}

	public boolean isSignReversed(String name) {
		return signReversedNames.contains(name);
	}

	public boolean isAlwaysShown(String name) {
		return alwaysShownNames.contains(name);
	}

	public boolean isHidden(String name) {
		return hiddenNamesIfZero.contains(name);
	}

	public String getYaml() {
		StringBuilder out = new StringBuilder();

		retrieve(out, getRoot());

		out.append("\r\n");
		out.append("貸借対照表の表示制御:\r\n");

		out.append("  符号を反転して表示する見出し: [");
		Iterator<String> it1 = getSignReversedNames().iterator();
		while(it1.hasNext()) {
			out.append(it1.next());
			if(it1.hasNext()) {
				out.append(", ");
			}
		}
		out.append("]\r\n");

		out.append("  常に表示する見出し: [");
		Iterator<String> it2 = getAlwaysShownNames().iterator();
		while(it2.hasNext()) {
			out.append(it2.next());
			if(it2.hasNext()) {
				out.append(", ");
			}
		}
		out.append("]\r\n");

		out.append("  ゼロなら表示しない見出し: [");
		Iterator<String> it3 = getHiddenNamesIfZero().iterator();
		while(it3.hasNext()) {
			out.append(it3.next());
			if(it3.hasNext()) {
				out.append(", ");
			}
		}
		out.append("]\r\n");

		out.append("\r\n\r\n");
		return out.toString();
	}

	private void retrieve(StringBuilder out, Node<Entry<List<AccountTitle>, Amount[]>> node) {
		List<AccountTitle> accountTitles = node.getValue().getKey();

		String indent = "  ".repeat(node.getLevel() + 0);
		out.append(indent);
		out.append(node.getName());
		out.append(":");
		if(accountTitles.size() > 0) {
			out.append(" [");
			for(int i = 0; i < accountTitles.size(); i++) {
				out.append(accountTitles.get(i).getDisplayName());
				if(i + 1 < accountTitles.size()) {
					out.append(", ");
				}
			}
			out.append("]");
		}
		out.append("\r\n");

		for(Node<Entry<List<AccountTitle>, Amount[]>> child : node.getChildren()) {
			retrieve(out, child);
		}
	}
}
