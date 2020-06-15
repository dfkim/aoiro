package net.osdn.aoiro.loader.yaml;

import java.io.IOException;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;

import net.osdn.aoiro.model.AccountTitle;
import net.osdn.aoiro.model.AccountType;
import net.osdn.aoiro.model.Amount;
import net.osdn.aoiro.model.Node;
import net.osdn.util.io.AutoDetectReader;

import static net.osdn.aoiro.ErrorMessage.error;

/** YAMLファイルから勘定科目をロードします。
 *
 */
public class YamlAccountTitlesLoader {

	/** システムで使用する決算勘定 */
	private static Map<String, AccountTitle> settlementAccountTitleByDisplayName = new HashMap<>();
	
	static {
		settlementAccountTitleByDisplayName.put(AccountTitle.INCOME_SUMMARY.getDisplayName(), AccountTitle.INCOME_SUMMARY);
		settlementAccountTitleByDisplayName.put(AccountTitle.BALANCE.getDisplayName(), AccountTitle.BALANCE);
		settlementAccountTitleByDisplayName.put(AccountTitle.RETAINED_EARNINGS.getDisplayName(), AccountTitle.RETAINED_EARNINGS);
		settlementAccountTitleByDisplayName.put(AccountTitle.PRETAX_INCOME.getDisplayName(), AccountTitle.PRETAX_INCOME);
	}
	
	/** 勘定科目セット */
	private Set<AccountTitle> accountTitles = new LinkedHashSet<>();
	
	/** 勘定科目名から勘定科目を取得するためのマップ */
	private Map<String, AccountTitle> accountTitleByDisplayName = new HashMap<>();

	/** 損益計算書(P/L)を集計するためのツリーを構成するルートノード */
	private Node<Entry<List<AccountTitle>, Amount>> plRoot;
	
	/** 貸借対照表(B/S)を集計するためのツリーを構成するルートノード */
	private Node<Entry<List<AccountTitle>, Amount[]>> bsRoot;
	
	/** 社員資本等変動計算書の縦軸(変動事由)を構成するマップ (LinkedHashMapが設定されるため順序が維持されます。 */
	private Map<String, List<String>> ceReasons;
	
	/** 社員資本等変動計算書を集計するためのツリーを構成するルートノード */
	private Node<List<AccountTitle>> ceRoot;


	/** 損益計算書(P/L)で金額の符号を反転して表示する見出しのリスト */
	private Set<String> plSignReversedNames = new HashSet<>();

	/** 貸借対照表(B/S)で金額の符号を反転して表示する見出しのリスト */
	private Set<String> bsSignReversedNames = new HashSet<>();

	/** 損益計算書(P/L)に常に表示する見出しのリスト */
	private Set<String> plAlwaysShownNames = new HashSet<>();
	
	/** 貸借対照表(B/S)に常に表示する見出しのリスト */
	private Set<String> bsAlwaysShownNames = new HashSet<>();

	/** ゼロの場合は損益計算書(P/L)に表示しない見出しのリスト */
	private Set<String> plHiddenNamesIfZero = new HashSet<>();

	/** ゼロの場合は貸借対照表(B/S)に表示しない見出しのリスト */
	private Set<String> bsHiddenNamesIfZero = new HashSet<>();
	
	public YamlAccountTitlesLoader(Path path) throws IOException {
		String yaml = AutoDetectReader.readAll(path);
		Object obj;

		try {
			obj = new YamlReader(yaml).read();
		} catch(YamlException e) {
			YamlBeansUtil.Message m = YamlBeansUtil.getMessage(e);
			throw error(" [エラー] " + path + " (" + m.getLine() + "行目, " + m.getColumn() + "桁目)\r\n"
					+ " " + m.getMessage());
		}
		if(obj == null) {
			throw error(" [エラー] " + path + "\r\n 形式に誤りがあります。");
		} else if(!(obj instanceof Map)) {
			throw error(" [エラー] " + path + "\r\n 形式に誤りがあります。");
		}

		@SuppressWarnings("unchecked")
		Map<String, Object> root = (Map<String, Object>)obj;

		obj = root.get("仕訳");
		if(obj == null) {
			throw error(" [エラー] " + path + "\r\n 仕訳が定義されていません。");
		} else if(!(obj instanceof Map)) {
			throw error(" [エラー] " + path + "\r\n 仕訳の形式に誤りがあります。");
		} else {
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>)obj;
			Object obj2;

			obj2 = map.get("資産");
			if(obj2 == null) {
				throw error(" [エラー] " + path + "\r\n 資産が定義されていません。");
			} else if(!(obj2 instanceof List)) {
				throw error(" [エラー] " + path + "\r\n 資産の形式に誤りがあります。");
			}
			@SuppressWarnings("unchecked")
			List<String> assets = (List<String>)obj2;
			for(String displayName : assets) {
				AccountTitle accountTitle = new AccountTitle(AccountType.Assets, displayName);
				accountTitles.add(accountTitle);
				accountTitleByDisplayName.put(displayName, accountTitle);
			}

			obj2 = map.get("負債");
			if(obj2 == null) {
				throw error(" [エラー] " + path + "\r\n 負債が定義されていません。");
			} else if(!(obj2 instanceof List)) {
				throw error(" [エラー] " + path + "\r\n 負債の形式に誤りがあります。");
			}
			@SuppressWarnings("unchecked")
			List<String> liabilities = (List<String>)obj2;
			for(String displayName : liabilities) {
				AccountTitle accountTitle = new AccountTitle(AccountType.Liabilities, displayName);
				accountTitles.add(accountTitle);
				accountTitleByDisplayName.put(displayName, accountTitle);
			}

			obj2 = map.get("純資産");
			if(obj2 == null) {
				obj2 = map.get("資本");
				if(obj2 == null) {
					throw error(" [エラー] " + path + "\r\n 資本・純資産が定義されていません。（どちらかの定義が必要です。）");
				} else if(!(obj2 instanceof List)) {
					throw error(" [エラー] " + path + "\r\n 資本の形式に誤りがあります。");
				}
			} else if(!(obj2 instanceof List)) {
				throw error(" [エラー] " + path + "\r\n 純資産の形式に誤りがあります。");
			}
			@SuppressWarnings("unchecked")
			List<String> netAssets = (List<String>)obj2;
			if(netAssets != null) {
				for(String displayName : netAssets) {
					AccountTitle accountTitle = new AccountTitle(AccountType.NetAssets, displayName);
					accountTitles.add(accountTitle);
					accountTitleByDisplayName.put(displayName, accountTitle);
				}
			}

			obj2 = map.get("収益");
			if(obj2 == null) {
				throw error(" [エラー] " + path + "\r\n 収益が定義されていません。");
			} else if(!(obj2 instanceof List)) {
				throw error(" [エラー] " + path + "\r\n 収益の形式に誤りがあります。");
			}
			@SuppressWarnings("unchecked")
			List<String> revenue = (List<String>)obj2;
			for(String displayName : revenue) {
				AccountTitle accountTitle = new AccountTitle(AccountType.Revenue, displayName);
				accountTitles.add(accountTitle);
				accountTitleByDisplayName.put(displayName, accountTitle);
			}

			obj2 = map.get("費用");
			if(obj2 == null) {
				throw error(" [エラー] " + path + "\r\n 費用が定義されていません。");
			} else if(!(obj2 instanceof List)) {
				throw error(" [エラー] " + path + "\r\n 費用の形式に誤りがあります。");
			}
			@SuppressWarnings("unchecked")
			List<String> expense = (List<String>)obj2;
			for(String displayName : expense) {
				AccountTitle accountTitle = new AccountTitle(AccountType.Expense, displayName);
				accountTitles.add(accountTitle);
				accountTitleByDisplayName.put(displayName, accountTitle);
			}
		}
		
		obj = root.get("損益計算書");
		if(obj == null) {
			throw error(" [エラー] " + path + "\r\n 損益計算書が定義されていません。");
		} else if(!(obj instanceof Map)) {
			throw error(" [エラー] " + path + "\r\n 損益計算書の形式に誤りがあります。");
		} else {
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>)obj;
			Node<Entry<List<AccountTitle>, Amount>> plRoot = new Node<Entry<List<AccountTitle>, Amount>>(-1, "損益計算書");
			plRoot.setValue(new AbstractMap.SimpleEntry<List<AccountTitle>, Amount>(new ArrayList<AccountTitle>(), null));
			retrieve(plRoot, map, new NodeCallback<Entry<List<AccountTitle>, Amount>>() {
				@Override
				public void initialize(Node<Entry<List<AccountTitle>, Amount>> node) {
					node.setValue(new AbstractMap.SimpleEntry<List<AccountTitle>, Amount>(new ArrayList<AccountTitle>(), null));
				}
				@Override
				public void setAccountTitles(Node<Entry<List<AccountTitle>, Amount>> node, List<AccountTitle> list) {
					node.getValue().getKey().addAll(list);
				}
				@Override
				public AccountTitle findAccountTitle(String displayName) {
					AccountTitle accountTitle = getAccountTitleByDisplayName(displayName);
					if(accountTitle == null) {
						throw error(" [エラー] " + path + "\r\n 損益計算書に未定義の勘定科目が指定されています: " + displayName);
					} else if(accountTitle.getType() == AccountType.Assets) {
						throw error(" [エラー] " + path + "\r\n 損益計算書に資産の勘定科目を指定することはできません: " + displayName);
					} else if(accountTitle.getType() == AccountType.Liabilities) {
						throw error(" [エラー] " + path + "\r\n 損益計算書に負債の勘定科目を指定することはできません: " + displayName);
					} else if(accountTitle.getType() == AccountType.NetAssets) {
						throw error(" [エラー] " + path + "\r\n 損益計算書に資本（純資産）の勘定科目を指定することはできません: " + displayName);
					}
					return accountTitle;
				}
			});
			this.plRoot = plRoot;
		}
		
		obj = root.get("貸借対照表");
		if(obj == null) {
			throw error(" [エラー] " + path + "\r\n 貸借対照表が定義されていません。");
		} else if(!(obj instanceof Map)) {
			throw error(" [エラー] " + path + "\r\n 貸借対照表の形式に誤りがあります。");
		} else {
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>)obj;
			Node<Entry<List<AccountTitle>, Amount[]>> bsRoot = new Node<Entry<List<AccountTitle>, Amount[]>>(0, "貸借対照表");
			bsRoot.setValue(new AbstractMap.SimpleEntry<List<AccountTitle>, Amount[]>(new ArrayList<AccountTitle>(), null));
			retrieve(bsRoot, map, new NodeCallback<Entry<List<AccountTitle>, Amount[]>>() {
				@Override
				public void initialize(Node<Entry<List<AccountTitle>, Amount[]>> node) {
					node.setValue(new AbstractMap.SimpleEntry<List<AccountTitle>, Amount[]>(new ArrayList<AccountTitle>(), null));
				}
				@Override
				public void setAccountTitles(Node<Entry<List<AccountTitle>, Amount[]>> node, List<AccountTitle> list) {
					node.getValue().getKey().addAll(list);
				}

				@Override
				public AccountTitle findAccountTitle(String displayName) {
					AccountTitle accountTitle = getAccountTitleByDisplayName(displayName);
					if(accountTitle == null) {
						throw error(" [エラー] " + path + "\r\n 貸借対照表に未定義の勘定科目が指定されています: " + displayName);
					} else if(accountTitle.getType() == AccountType.Revenue) {
						throw error(" [エラー] " + path + "\r\n 貸借対照表に収益の勘定科目を指定することはできません: " + displayName);
					} else if(accountTitle.getType() == AccountType.Expense) {
						throw error(" [エラー] " + path + "\r\n 貸借対照表に費用の勘定科目を指定することはできません: " + displayName);
					}
					return accountTitle;
				}
			});
			this.bsRoot = bsRoot;
		}
		
		obj = root.get("社員資本等変動計算書");
		if(obj == null) {
			// 社員資本等変動計算書は定義されていないくても構わない。
		} else if(!(obj instanceof Map)) {
			throw error(" [エラー] " + path + "\r\n 社員資本等変動計算書の形式に誤りがあります。");
		} else {
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>)obj;
			Object obj2;

			// 変動事由を取り出してマップから取り除きます。
			// 変動事由は ceReasons に保持して、他部分は ceRoot にぶら下がります。
			obj2 = map.remove("変動事由");
			if(obj2 == null) {
				throw error(" [エラー] " + path + "\r\n 社員資本等変動計算書（変動事由）が定義されていません。");
			} else if(!(obj2 instanceof Map)) {
				throw error(" [エラー] " + path + "\r\n 社員資本等変動計算書（変動事由）の形式に誤りがあります。");
			} else {
				@SuppressWarnings("unchecked")
				Map<String, List<String>> map2 = (Map<String, List<String>>)obj2;
				this.ceReasons = map2;
			}
			Node<List<AccountTitle>> ceRoot = new Node<List<AccountTitle>>(0, "社員資本等変動計算書");
			ceRoot.setValue(new ArrayList<AccountTitle>());
			retrieve(ceRoot, map, new NodeCallback<List<AccountTitle>>() {
				@Override
				public void initialize(Node<List<AccountTitle>> node) {
					node.setValue(new ArrayList<AccountTitle>());
				}
				@Override
				public void setAccountTitles(Node<List<AccountTitle>> node, List<AccountTitle> list) {
					node.getValue().addAll(list);
				}
				@Override
				public AccountTitle findAccountTitle(String displayName) {
					AccountTitle accountTitle = getAccountTitleByDisplayName(displayName);
					if(accountTitle == null) {
						throw error(" [エラー] " + path + "\r\n 社員資本等変動計算書に未定義の勘定科目が指定されています: " + displayName);
					}
					return accountTitle;
				}
			});
			this.ceRoot = ceRoot;
		}

		obj = root.get("符号を反転して表示する見出し");
		if(obj == null) {
			// 符号を反転して表示する見出しは定義はなくても構わない。
		} else if(!(obj instanceof Map)) {
			throw error(" [エラー] " + path + "\r\n 符号を反転して表示する見出しの形式に誤りがあります。");
		} else {
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>)obj;
			Object obj2;

			obj2 = map.get("損益計算書");
			if(obj2 == null) {
				// 気にしない
			} else if(!(obj2 instanceof List)) {
				throw error(" [エラー] " + path + "\r\n 符号を反転して表示する見出し（損益計算書）の形式に誤りがあります。");
			} else {
				@SuppressWarnings("unchecked")
				List<Object> list = (List<Object>)obj2;
				for(Object o : list) {
					if(o != null) {
						plSignReversedNames.add(o.toString().trim());
					}
				}
			}

			obj2 = map.get("貸借対照表");
			if(obj2 == null) {
				// 気にしない
			} else if(!(obj2 instanceof List)) {
				throw error(" [エラー] " + path + "\r\n 符号を反転して表示する見出し（貸借対照表）の形式に誤りがあります。");
			} else {
				@SuppressWarnings("unchecked")
				List<Object> list = (List<Object>)obj2;
				for(Object o : list) {
					if(o != null) {
						bsSignReversedNames.add(o.toString().trim());
					}
				}
			}
		}

		obj = root.get("常に表示する見出し");
		if(obj == null) {
			// 気にしない
		} else if(!(obj instanceof Map)) {
			throw error(" [エラー] " + path + "\r\n 常に表示する見出しの形式に誤りがあります。");
		} else {
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>)obj;
			Object obj2;

			obj2 = map.get("損益計算書");
			if(obj2 == null) {
				// 気にしない
			} else if(!(obj2 instanceof List)) {
				throw error(" [エラー] " + path + "\r\n 常に表示する見出し（損益計算書）の形式に誤りがあります。");
			} else {
				@SuppressWarnings("unchecked")
				List<Object> list = (List<Object>)obj2;
				for(Object o : list) {
					if(o != null) {
						plAlwaysShownNames.add(o.toString().trim());
					}
				}
			}

			obj2 = map.get("貸借対照表");
			if(obj2 == null) {
				// 気にしない
			} else if(!(obj2 instanceof List)) {
				throw error(" [エラー] " + path + "\r\n 常に表示する見出し（貸借対照表）の形式に誤りがあります。");
			} else {
				@SuppressWarnings("unchecked")
				List<Object> list = (List<Object>)obj2;
				for(Object o : list) {
					if(o != null) {
						bsAlwaysShownNames.add(o.toString().trim());
					}
				}
			}
		}

		obj = root.get("ゼロなら表示しない見出し");
		if(obj == null) {
			// 気にしない
		} else if(!(obj instanceof Map)) {
			throw error(" [エラー] " + path + "\r\n ゼロなら表示しない見出しの形式に誤りがあります。");
		} else {
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>)obj;
			Object obj2;

			obj2 = map.get("損益計算書");
			if(obj2 == null) {
				// 気にしない
			} else if(!(obj2 instanceof List)) {
				throw error(" [エラー] " + path + "\r\n ゼロなら表示しない見出し（損益計算書）の形式に誤りがあります。");
			} else {
				@SuppressWarnings("unchecked")
				List<Object> list = (List<Object>)obj2;
				for(Object o : list) {
					if(o != null) {
						plHiddenNamesIfZero.add(o.toString().trim());
					}
				}
			}

			obj2 = map.get("貸借対照表");
			if(obj2 == null) {
				// 気にしない
			} else if(!(obj2 instanceof List)) {
				throw error(" [エラー] " + path + "\r\n ゼロなら表示しない見出し（貸借対照表）の形式に誤りがあります。");
			} else {
				@SuppressWarnings("unchecked")
				List<Object> list = (List<Object>)obj2;
				for(Object o : list) {
					if(o != null) {
						bsHiddenNamesIfZero.add(o.toString().trim());
					}
				}
			}
		}
	}

	private <T> void retrieve(Node<T> parent, Map<String, Object> map, NodeCallback<T> callback) {
		for(Map.Entry<String, Object> entry : map.entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();

			Node<T> node = new Node<T>(parent.getLevel() + 1, key);
			callback.initialize(node);
			parent.getChildren().add(node);
			
			if(value == null) {
				
			} else if(value instanceof String) {
				String displayName = ((String)value).trim();
				if(displayName.length() > 0) {
					AccountTitle accountTitle = callback.findAccountTitle(displayName);
					List<AccountTitle> list = new LinkedList<AccountTitle>();
					list.add(accountTitle);
					callback.setAccountTitles(node, list);
				}
			} else if(value instanceof Map) {
				@SuppressWarnings("unchecked")
				Map<String, Object> m = (Map<String, Object>)value;
				retrieve(node, m, callback);
			} else if(value instanceof List) {
				List<AccountTitle> list = new LinkedList<AccountTitle>();
				@SuppressWarnings("unchecked")
				List<Object> l = (List<Object>)value;
				for(Object obj : l) {
					String displayName = obj.toString().trim();
					AccountTitle accountTitle = callback.findAccountTitle(displayName);
					list.add(accountTitle);
				}
				callback.setAccountTitles(node, list);
			}
		}
	}

	/** 指定したノードの勘定科目を再帰的に収集して返します。
	 *
	 * @param node 損益計算書または貸借対照表のツリーノード
	 * @return 収集した勘定科目のセット
	 */
	private <V> Set<AccountTitle> retrieve(Node<Map.Entry<List<AccountTitle>, V>> node) {
		Set<AccountTitle> accountTitles = new HashSet<AccountTitle>();
		for(AccountTitle accountTitle : node.getValue().getKey()) {
			accountTitles.add(accountTitle);
		}
		for(Node<Map.Entry<List<AccountTitle>, V>> child : node.getChildren()) {
			accountTitles.addAll(retrieve(child));
		}
		return accountTitles;
	}

	public boolean validate() {
		boolean valid = true;

		Set<AccountTitle> plAccountTitles = retrieve(plRoot);
		for(AccountTitle accountTitle : accountTitles) {
			if(accountTitle.getType() == AccountType.Revenue && !plAccountTitles.contains(accountTitle)) {
				System.out.println(" [警告] 損益計算書に「" + accountTitle.getDisplayName() + "」が含まれていません。");
				valid = false;
			}
			if(accountTitle.getType() == AccountType.Expense && !plAccountTitles.contains(accountTitle)) {
				System.out.println(" [警告] 損益計算書に「" + accountTitle.getDisplayName() + "」が含まれていません。");
				valid = false;
			}
		}

		Set<AccountTitle> bsAccountTitles = retrieve(bsRoot);
		for(AccountTitle accountTitle : accountTitles) {
			if(accountTitle.getType() == AccountType.Assets && !bsAccountTitles.contains(accountTitle)) {
				System.out.println(" [警告] 貸借対照表に「" + accountTitle.getDisplayName() + "」が含まれていません。");
				valid = false;
			}
			if(accountTitle.getType() == AccountType.Liabilities && !bsAccountTitles.contains(accountTitle)) {
				System.out.println(" [警告] 貸借対照表に「" + accountTitle.getDisplayName() + "」が含まれていません。");
				valid = false;
			}
			if(accountTitle.getType() == AccountType.NetAssets && !bsAccountTitles.contains(accountTitle)) {
				System.out.println(" [警告] 貸借対照表に「" + accountTitle.getDisplayName() + "」が含まれていません。");
				valid = false;
			}
		}

		if(!valid) {
			System.out.println();
		}

		return valid;
	}
	
	
	public Set<AccountTitle> getAccountTitles() {
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
	public Node<Entry<List<AccountTitle>, Amount>> getProfitAndLossRoot() {
		return plRoot;
	}

	/** 貸借対照表の勘定科目ツリールートノードを返します。
	 * 
	 * @return 貸借対照表のルートノード
	 */
	public Node<Entry<List<AccountTitle>, Amount[]>> getBalanceSheetRoot() {
		return bsRoot;
	}
	
	/** 社員資本等変動計算書の縦軸(変動事由)を構成するマップを返します。
	 * このマップの実装クラスはLinkedHashMapであり要素の順序が維持されます。
	 * 
	 * @return 社員資本等変動計算書の縦軸(変動事由)を構成するマップ
	 */
	public Map<String, List<String>> getStateOfChangesInEquityReasons() {
		return ceReasons;
	}

	/** 社員資本等変動計算書の横軸(勘定科目)のツリールートノードを返します。
	 * 
	 * @return 社員資本等変動計算書を集計するためのツリーを構成するルートノード
	 */
	public Node<List<AccountTitle>> getStateOfChangesInEquityRoot() {
		return ceRoot;
	}

	/** 損益計算書で符号を反転して表示する見出しのセットを返します。
	 * このセットに含まれる見出しは、金額の符号が反転して表示されます。
	 *
	 * @return 損益計算書で符号を反転して表示する見出しのセット
	 */
	public Set<String> getSignReversedNamesForProfitAndLoss() {
		return plSignReversedNames;
	}

	/** 貸借対照表で符号を反転して表示する見出しのセットを返します。
	 * このセットに含まれる見出しは、金額の符号が反転して表示されます。
	 *
	 * @return 貸借対照表で符号を反転して表示する見出しのセット
	 */
	public Set<String> getSignReversedNamesForBalanceSheet() {
		return bsSignReversedNames;
	}

	/** 損益計算書に常に表示する見出しのセットを返します。
	 * このセットに含まれる見出しは、対象となる仕訳が存在しない場合でも常に表示されます。
	 * 
	 * @return 損益計算書に常に表示する見出しのセット
	 */
	public Set<String> getAlwaysShownNamesForProfitAndLoss() {
		return plAlwaysShownNames;
	}

	/** 貸借対照表に常に表示する見出しのセットを返します。
	 * このセットに含まれる見出しは、対象となる仕訳が存在しない場合でも常に表示されます。
	 * 
	 * @return 貸借対照表に常に表示する見出しのセット
	 */
	public Set<String> getAlwaysShownNamesForBalanceSheet() {
		return bsAlwaysShownNames;
	}

	/** ゼロの場合に損益計算書に表示しない見出しのセットを返します。
	 * このセットに含まれる見出しは、合計がゼロのときは表示されません。
	 *
	 * @return ゼロの場合に損益計算書に表示しない見出しセット
	 */
	public Set<String> getHiddenNamesIfZeroForProfitAndLoss() {
		return plHiddenNamesIfZero;
	}

	/** ゼロの場合に貸借対照表に表示しない見出しのセットを返します。
	 * このセットに含まれる見出しは、合計がゼロのときは表示されません。
	 *
	 * @return ゼロの場合に貸借対照表に表示しない見出しのセット
	 */
	public Set<String> getHiddenNamesIfZeroForBalanceSheet() {
		return bsHiddenNamesIfZero;
	}
	
	
	/*
	private <T> void dump(int indent, Node<List<AccountTitle>, T> node) {
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
		for(Node<List<AccountTitle>, T> child : node.getChildren()) {
			dump(indent + 1, child);
		}
	}
	*/
	
	private interface NodeCallback<T> {
		void initialize(Node<T> node);
		void setAccountTitles(Node<T> node, List<AccountTitle> list);
		AccountTitle findAccountTitle(String displayName);
	}
}
