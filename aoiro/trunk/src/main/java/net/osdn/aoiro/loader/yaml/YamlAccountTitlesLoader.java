package net.osdn.aoiro.loader.yaml;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.osdn.aoiro.model.AccountTitle;
import net.osdn.aoiro.model.AccountType;
import net.osdn.aoiro.model.Amount;
import net.osdn.aoiro.model.Node;
import net.osdn.util.yaml.Yaml;

/** YAMLファイルから勘定科目をロードします。
 *
 */
public class YamlAccountTitlesLoader {

	/** システムで使用する決算勘定 */
	private static Map<String, AccountTitle> settlementAccountTitleByDisplayName = new HashMap<String, AccountTitle>();
	
	static {
		settlementAccountTitleByDisplayName.put(AccountTitle.INCOME_SUMMARY.getDisplayName(), AccountTitle.INCOME_SUMMARY);
		settlementAccountTitleByDisplayName.put(AccountTitle.BALANCE.getDisplayName(), AccountTitle.BALANCE);
		settlementAccountTitleByDisplayName.put(AccountTitle.RETAINED_EARNINGS.getDisplayName(), AccountTitle.RETAINED_EARNINGS);
		settlementAccountTitleByDisplayName.put(AccountTitle.PRETAX_INCOME.getDisplayName(), AccountTitle.PRETAX_INCOME);
	}
	
	/** 勘定科目リスト */
	private List<AccountTitle> accountTitles = new ArrayList<AccountTitle>();
	
	/** 勘定科目名から勘定科目を取得するためのマップ */
	private Map<String, AccountTitle> accountTitleByDisplayName = new HashMap<String, AccountTitle>();

	/** 損益計算書(P/L)を集計するためのツリーを構成するルートノード */
	private Node<List<AccountTitle>, Amount> plRoot;
	
	/** 貸借対照表(B/S)を集計するためのツリーを構成するルートノード */
	private Node<List<AccountTitle>, Amount[]> bsRoot;
	
	public YamlAccountTitlesLoader(File file) throws IOException {
		Yaml yaml = new Yaml(file);
		Map<String, Object> root = yaml.getMap();

		Object obj;
		
		obj = root.get("仕訳");
		if(obj instanceof Map) {
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>)obj;
			@SuppressWarnings("unchecked")
			List<String> assets = (List<String>)map.get("資産");
			for(String displayName : assets) {
				AccountTitle accountTitle = new AccountTitle(AccountType.Assets, displayName);
				accountTitles.add(accountTitle);
				accountTitleByDisplayName.put(displayName, accountTitle);
			}
			@SuppressWarnings("unchecked")
			List<String> liabilities = (List<String>)map.get("負債");
			for(String displayName : liabilities) {
				AccountTitle accountTitle = new AccountTitle(AccountType.Liabilities, displayName);
				accountTitles.add(accountTitle);
				accountTitleByDisplayName.put(displayName, accountTitle);
			}
			@SuppressWarnings("unchecked")
			List<String> netAssets = (List<String>)map.get("純資産");
			for(String displayName : netAssets) {
				AccountTitle accountTitle = new AccountTitle(AccountType.NetAssets, displayName);
				accountTitles.add(accountTitle);
				accountTitleByDisplayName.put(displayName, accountTitle);
			}
			@SuppressWarnings("unchecked")
			List<String> revenue = (List<String>)map.get("収益");
			for(String displayName : revenue) {
				AccountTitle accountTitle = new AccountTitle(AccountType.Revenue, displayName);
				accountTitles.add(accountTitle);
				accountTitleByDisplayName.put(displayName, accountTitle);
			}
			@SuppressWarnings("unchecked")
			List<String> expense = (List<String>)map.get("費用");
			for(String displayName : expense) {
				AccountTitle accountTitle = new AccountTitle(AccountType.Expense, displayName);
				accountTitles.add(accountTitle);
				accountTitleByDisplayName.put(displayName, accountTitle);
			}
		}
		
		obj = root.get("損益計算書");
		if(obj instanceof Map) {
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>)obj;
			Node<List<AccountTitle>, Amount> plRoot = new Node<List<AccountTitle>, Amount>(-1, "損益計算書");
			retrieve(plRoot, map);
			
			this.plRoot = plRoot;

			//dump(0, plRoot);
		}
		
		obj = root.get("貸借対照表");
		if(obj instanceof Map) {
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>)obj;
			Node<List<AccountTitle>, Amount[]> bsRoot = new Node<List<AccountTitle>, Amount[]>(0, "貸借対照表");
			retrieve(bsRoot, map);
			
			this.bsRoot = bsRoot;
			
			//dump(0, bsRoot);
		}
	}
	
	
	private <T> void retrieve(Node<List<AccountTitle>, T> parent, Map<String, Object> map) {
		for(Map.Entry<String, Object> entry : map.entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();

			Node<List<AccountTitle>, T> node = new Node<List<AccountTitle>, T>(parent.getLevel() + 1, key);
			parent.getChildren().add(node);
			
			if(value == null) {
				
			} else if(value instanceof String) {
				String displayName = ((String)value).trim();
				if(displayName.length() > 0) {
					AccountTitle accountTitle = getAccountTitleByDisplayName(displayName);
					if(accountTitle == null) {
						throw new IllegalArgumentException("勘定科目が定義されていません: " + displayName);
					}
					List<AccountTitle> list = new LinkedList<AccountTitle>();
					list.add(accountTitle);
					node.setKey(list);
				}
			} else if(value instanceof Map) {
				@SuppressWarnings("unchecked")
				Map<String, Object> m = (Map<String, Object>)value;
				retrieve(node, m);
			} else if(value instanceof List) {
				List<AccountTitle> list = new LinkedList<AccountTitle>();
				@SuppressWarnings("unchecked")
				List<Object> l = (List<Object>)value;
				for(Object obj : l) {
					String displayName = obj.toString().trim();
					AccountTitle accountTitle = getAccountTitleByDisplayName(displayName);
					if(accountTitle == null) {
						throw new IllegalArgumentException("勘定科目が定義されていません: " + displayName);
					}
					list.add(accountTitle);
				}
				node.setKey(list);
			}
		}
	}
	
	
	public List<AccountTitle> getAccountTitles() {
		return accountTitles;
	}
	
	/** 勘定科目名を指定して勘定科目を取得します。
	 * このメソッドはシステムで自動作成される決算勘定科目も返します。
	 * 
	 * @param displayName 勘定科目名
	 * @return 見つかった勘定科目を返します。見つからなかった場合は null を返します。
	 */
	public AccountTitle getAccountTitleByDisplayName(String displayName) {
		AccountTitle accountTitle = accountTitleByDisplayName.get(displayName);
		if(accountTitle == null) {
			accountTitle = settlementAccountTitleByDisplayName.get(displayName);
		}
		return accountTitle;
	}
	
	/** 損益計算書の勘定科目ツリールートノードを返します。
	 * 
	 * @return 損益計算書のルートノード
	 */
	public Node<List<AccountTitle>, Amount> getProfitAndLossRoot() {
		return plRoot;
	}

	/** 貸借対照表の勘定科目ツリールートノードを返します。
	 * 
	 * @return 貸借対照表のルートノード
	 */
	public Node<List<AccountTitle>, Amount[]> getBalanceSheetRoot() {
		return bsRoot;
	}
	
	/*
	private void dump(int indent, Node<List<AccountTitle>, Amount> node) {
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < indent; i++) {
			sb.append(" - ");
		}
		sb.append(node.getName());
		if(node.getKey() != null) {
			sb.append(": [");
			for(int i = 0; i < node.getKey().size(); i++) {
				sb.append(node.getKey().get(i).getDisplayName());
				if(i + 1 < node.getKey().size()) {
					sb.append(", ");
				}
			}
			sb.append("]");
		}
		System.out.println(sb.toString());
		for(Node<List<AccountTitle>, Amount> child : node.getChildren()) {
			dump(indent + 1, child);
		}
	}
	*/
}
