package net.osdn.aoiro.report.layout;

import net.osdn.aoiro.loader.yaml.YamlBeansUtil;
import net.osdn.aoiro.model.AccountTitle;
import net.osdn.aoiro.model.Node;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/** 社員資本等変動計算書を作成するための構成情報 */
public class StatementOfChangesInEquityLayout {

	/** 社員資本等変動計算書を集計するためのツリーを構成するルートノード */
	private Node<List<AccountTitle>> root = new Node<>(0, "社員資本等変動計算書");

	/** 社員資本等変動計算書の縦軸(変動事由)を構成するマップ (LinkedHashMapが設定されるため順序が維持されます。 */
	private Map<String, List<String>> reasons = new LinkedHashMap<>();


	/** 社員資本等変動計算書の横軸(勘定科目)のツリールートノードを返します。
	 *
	 * @return 社員資本等変動計算書を集計するためのツリーを構成するルートノード
	 */
	public Node<List<AccountTitle>> getRoot() {
		return root;
	}

	/** 社員資本等変動計算書の縦軸(変動事由)を構成するマップを返します。
	 * このマップの実装クラスはLinkedHashMapであり要素の順序が維持されます。
	 *
	 * @return 社員資本等変動計算書の縦軸(変動事由)を構成するマップ
	 */
	public Map<String, List<String>> getReasons() {
		return reasons;
	}

	public String getYaml() {
		if(getRoot().getChildren().size() == 0) {
			return "";
		}

		StringBuilder out = new StringBuilder();

		out.append("\"社員資本等変動計算書\" :\r\n");

		out.append("  \"変動事由\" :\r\n");
		for(Entry<String, List<String>> entry : getReasons().entrySet()) {
			String name = entry.getKey();
			List<String> list = entry.getValue();
			out.append("    ");
			out.append("\"");
			out.append(YamlBeansUtil.escape(name));
			out.append("\" :");
			if(list != null && list.size() > 0) {
				out.append(" [");
				for(int i = 0; i < list.size(); i++) {
					out.append("\"");
					out.append(YamlBeansUtil.escape(list.get(i)));
					out.append("\"");
					if(i + 1 < list.size()) {
						out.append(", ");
					}
				}
				out.append("]");
			}
			out.append("\r\n");
		}

		for(Node<List<AccountTitle>> child : getRoot().getChildren()) {
			retrieve(out, child);
		}

		out.append("\r\n\r\n");
		return out.toString();
	}

	private void retrieve(StringBuilder out, Node<List<AccountTitle>> node) {
		List<AccountTitle> accountTitles = node.getValue();

		String indent = "  ".repeat(node.getLevel() + 0);
		out.append(indent);
		out.append("\"");
		out.append(YamlBeansUtil.escape(node.getName()));
		out.append("\" :");
		if(accountTitles.size() > 0) {
			out.append(" [");
			for(int i = 0; i < accountTitles.size(); i++) {
				out.append("\"");
				out.append(YamlBeansUtil.escape(accountTitles.get(i).getDisplayName()));
				out.append("\"");
				if(i + 1 < accountTitles.size()) {
					out.append(", ");
				}
			}
			out.append("]");
		}
		out.append("\r\n");

		for(Node<List<AccountTitle>> child : node.getChildren()) {
			retrieve(out, child);
		}
	}
}
