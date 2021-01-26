package net.osdn.aoiro.loader.yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
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
import net.osdn.aoiro.model.NodeUtil;
import net.osdn.aoiro.report.layout.BalanceSheetLayout;
import net.osdn.aoiro.report.layout.ProfitAndLossLayout;
import net.osdn.aoiro.report.layout.StatementOfChangesInEquityLayout;
import net.osdn.util.io.AutoDetectReader;

import static net.osdn.aoiro.ErrorMessage.error;

/** YAMLファイルから勘定科目をロードします。
 *
 */
public class AccountTitlesLoader {

	private Path path;

	/** 勘定科目セット */
	private Set<AccountTitle> accountTitles;

	/** 損益計算書(P/L)を作成するための構成情報 */
	private ProfitAndLossLayout plLayout = new ProfitAndLossLayout();

	/** 貸借対照表(B/S)を作成するための構成情報 */
	private BalanceSheetLayout bsLayout = new BalanceSheetLayout();

	/** 社員資本等変動計算書を作成するための構成情報 */
	private StatementOfChangesInEquityLayout sceLayout = new StatementOfChangesInEquityLayout();

	/** ビルトインの勘定科目 */
	private Map<String, AccountTitle> builtinAccountTitles = new HashMap<>() {{
		// 損益
		put(AccountTitle.INCOME_SUMMARY.getDisplayName(), AccountTitle.INCOME_SUMMARY);
		// 残高
		put(AccountTitle.BALANCE.getDisplayName(), AccountTitle.BALANCE);
		// 控除前の所得金額
		put(AccountTitle.PRETAX_INCOME.getDisplayName(), AccountTitle.PRETAX_INCOME);
		// 繰越利益剰余金
		put(AccountTitle.RETAINED_EARNINGS.getDisplayName(), AccountTitle.RETAINED_EARNINGS);
	}};

	/** 勘定科目名から勘定科目を取得するためのマップ。
	 * このマップには勘定科目.ymlに定義された勘定科目だけでなくビルトイン決算勘定科目も含まれています。 */
	private Map<String, AccountTitle> accountTitleByDisplayName = new HashMap<>() {{
		for(AccountTitle builtinAccountTitle : builtinAccountTitles.values()) {
			put(builtinAccountTitle.getDisplayName(), builtinAccountTitle);
		}
	}};

	public AccountTitlesLoader(Path path) {
		this.path = path;
	}

	private void read(boolean skipErrors) throws IOException {
		if(accountTitles != null) {
			return;
		}

		accountTitles = new LinkedHashSet<>();

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
				AccountTitle accountTitle = builtinAccountTitles.get(displayName);
				if(accountTitle == null) {
					accountTitle = new AccountTitle(AccountType.Assets, displayName);
				}
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
				AccountTitle accountTitle = builtinAccountTitles.get(displayName);
				if(accountTitle == null) {
					accountTitle = new AccountTitle(AccountType.Liabilities, displayName);
				}
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
			List<String> equity = (List<String>)obj2;
			if(equity != null) {
				for(String displayName : equity) {
					AccountTitle accountTitle = builtinAccountTitles.get(displayName);
					if(accountTitle == null) {
						accountTitle = new AccountTitle(AccountType.Equity, displayName);
					}
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
				AccountTitle accountTitle = builtinAccountTitles.get(displayName);
				if(accountTitle == null) {
					accountTitle = new AccountTitle(AccountType.Revenue, displayName);
				}
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
				AccountTitle accountTitle = builtinAccountTitles.get(displayName);
				if(accountTitle == null) {
					accountTitle = new AccountTitle(AccountType.Expense, displayName);
				}
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
			//Node<Entry<List<AccountTitle>, Amount>> plRoot = new Node<Entry<List<AccountTitle>, Amount>>(-1, "損益計算書");
			plLayout.getRoot().setValue(new AbstractMap.SimpleEntry<List<AccountTitle>, Amount>(new ArrayList<AccountTitle>(), null));
			retrieve(plLayout.getRoot(), map, new NodeCallback<Entry<List<AccountTitle>, Amount>>() {
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
					AccountTitle accountTitle = accountTitleByDisplayName.get(displayName);
					if(accountTitle == null) {
						if(skipErrors) {
							return null;
						}
						throw error(" [エラー] " + path + "\r\n 損益計算書に未定義の勘定科目が指定されています: " + displayName);
					} else if(accountTitle.getType() == AccountType.Assets) {
						if(skipErrors) {
							return null;
						}
						throw error(" [エラー] " + path + "\r\n 損益計算書に資産の勘定科目を指定することはできません: " + displayName);
					} else if(accountTitle.getType() == AccountType.Liabilities) {
						if(skipErrors) {
							return null;
						}
						throw error(" [エラー] " + path + "\r\n 損益計算書に負債の勘定科目を指定することはできません: " + displayName);
					} else if(accountTitle.getType() == AccountType.Equity) {
						if(skipErrors) {
							return null;
						}
						throw error(" [エラー] " + path + "\r\n 損益計算書に資本（純資産）の勘定科目を指定することはできません: " + displayName);
					}
					return accountTitle;
				}
			});
		}

		obj = root.get("損益計算書の表示制御");
		if(obj == null) {
			// 気にしない
		} else if(!(obj instanceof Map)) {
			throw error(" [エラー] " + path + "\r\n 損益計算書の表示制御の形式に誤りがあります。");
		} else {
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>)obj;
			Object obj2;
			obj2 = map.get("符号を反転して表示する見出し");
			if(obj2 == null) {
				// 気にしない
			} else if(!(obj2 instanceof List)) {
				throw error(" [エラー] " + path + "\r\n 損益計算書の表示制御（符号を反転して表示する見出し）の形式に誤りがあります。");
			} else {
				@SuppressWarnings("unchecked")
				List<Object> list = (List<Object>)obj2;
				for(Object obj3 : list) {
					if(obj3 != null) {
						plLayout.getSignReversedNames().add(obj3.toString().trim());
					}
				}
			}
			obj2 = map.get("常に表示する見出し");
			if(obj2 == null) {
				// 気にしない
			} else if(!(obj2 instanceof List)) {
				throw error(" [エラー] " + path + "\r\n 損益計算書の表示制御（常に表示する見出し）の形式に誤りがあります。");
			} else {
				// 見出しごとの祖先（階層の親）を作成します。
				Map<Node<Map.Entry<List<AccountTitle>, Amount>>, List<Node<Map.Entry<List<AccountTitle>, Amount>>>> ancestors = NodeUtil.createAncestors(plLayout.getRoot());

				@SuppressWarnings("unchecked")
				List<Object> list = (List<Object>)obj2;
				for(Object obj3 : list) {
					if(obj3 != null) {
						String name = obj3.toString().trim();
						plLayout.getAlwaysShownNames().add(name);

						// 見出し階層の子ノードが「常に表示する見出し」に設定されている場合、
						// そのすべての親（祖先）も「常に表示する見出し」に設定します。
						for(Node<Map.Entry<List<AccountTitle>, Amount>> node : NodeUtil.findByName(plLayout.getRoot(), name)) {
							for(Node<Map.Entry<List<AccountTitle>, Amount>> ancestor : ancestors.get(node)) {
								plLayout.getAlwaysShownNames().add(ancestor.getName());
							}
						}
					}
				}
			}

			obj2 = map.get("ゼロなら表示しない見出し");
			if(obj2 == null) {
				// 気にしない
			} else if(!(obj2 instanceof List)) {
				throw error(" [エラー] " + path + "\r\n 損益計算書の表示制御（ゼロなら表示しない見出し）の形式に誤りがあります。");
			} else {
				@SuppressWarnings("unchecked")
				List<Object> list = (List<Object>)obj2;
				for(Object obj3 : list) {
					if(obj3 != null) {
						plLayout.getHiddenNamesIfZero().add(obj3.toString().trim());
					}
				}
			}
		}
		
		obj = root.get("貸借対照表");
		if(obj == null) {
			throw error(" [エラー] " + path + "\r\n 貸借対照表が定義されていません。");
		} else if(!(obj instanceof Map)) {
			throw error(" [エラー] " + path + "\r\n 貸借対照表の形式に誤りがあります。");
		} else {
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>)obj;
			bsLayout.getRoot().setValue(new AbstractMap.SimpleEntry<List<AccountTitle>, Amount[]>(new ArrayList<AccountTitle>(), null));
			retrieve(bsLayout.getRoot(), map, new NodeCallback<Entry<List<AccountTitle>, Amount[]>>() {
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
					AccountTitle accountTitle = accountTitleByDisplayName.get(displayName);
					if(accountTitle == null) {
						if(skipErrors) {
							return null;
						}
						throw error(" [エラー] " + path + "\r\n 貸借対照表に未定義の勘定科目が指定されています: " + displayName);
					} else if(accountTitle.getType() == AccountType.Revenue) {
						if(skipErrors) {
							return null;
						}
						throw error(" [エラー] " + path + "\r\n 貸借対照表に収益の勘定科目を指定することはできません: " + displayName);
					} else if(accountTitle.getType() == AccountType.Expense) {
						if(skipErrors) {
							return null;
						}
						throw error(" [エラー] " + path + "\r\n 貸借対照表に費用の勘定科目を指定することはできません: " + displayName);
					}
					return accountTitle;
				}
			});
		}

		obj = root.get("貸借対照表の表示制御");
		if(obj == null) {
			// 気にしない
		} else if(!(obj instanceof Map)) {
			throw error(" [エラー] " + path + "\r\n 貸借対照表の表示制御の形式に誤りがあります。");
		} else {
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>)obj;
			Object obj2;
			obj2 = map.get("符号を反転して表示する見出し");
			if(obj2 == null) {
				// 気にしない
			} else if(!(obj2 instanceof List)) {
				throw error(" [エラー] " + path + "\r\n 貸借対照表の表示制御（符号を反転して表示する見出し）の形式に誤りがあります。");
			} else {
				@SuppressWarnings("unchecked")
				List<Object> list = (List<Object>)obj2;
				for(Object obj3 : list) {
					if(obj3 != null) {
						bsLayout.getSignReversedNames().add(obj3.toString().trim());
					}
				}
			}
			obj2 = map.get("常に表示する見出し");
			if(obj2 == null) {
				// 気にしない
			} else if(!(obj2 instanceof List)) {
				throw error(" [エラー] " + path + "\r\n 貸借対照表の表示制御（常に表示する見出し）の形式に誤りがあります。");
			} else {
				@SuppressWarnings("unchecked")
				List<Object> list = (List<Object>)obj2;
				for(Object obj3 : list) {
					if(obj3 != null) {
						bsLayout.getAlwaysShownNames().add(obj3.toString().trim());
					}
				}
			}

			obj2 = map.get("ゼロなら表示しない見出し");
			if(obj2 == null) {
				// 気にしない
			} else if(!(obj2 instanceof List)) {
				throw error(" [エラー] " + path + "\r\n 貸借対照表の表示制御（ゼロなら表示しない見出し）の形式に誤りがあります。");
			} else {
				@SuppressWarnings("unchecked")
				List<Object> list = (List<Object>)obj2;
				for(Object obj3 : list) {
					if(obj3 != null) {
						bsLayout.getHiddenNamesIfZero().add(obj3.toString().trim());
					}
				}
			}
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
				for(Entry<String, List<String>> entry : map2.entrySet()) {
					sceLayout.getReasons().put(entry.getKey(), entry.getValue());
				}
			}
			sceLayout.getRoot().setValue(new ArrayList<AccountTitle>());
			retrieve(sceLayout.getRoot(), map, new NodeCallback<List<AccountTitle>>() {
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
					AccountTitle accountTitle = accountTitleByDisplayName.get(displayName);
					if(accountTitle == null) {
						if(skipErrors) {
							return null;
						}
						throw error(" [エラー] " + path + "\r\n 社員資本等変動計算書に未定義の勘定科目が指定されています: " + displayName);
					}
					return accountTitle;
				}
			});
		}

		//
		// 以下は古い定義形式です。（古い定義形式をサポートするために必要です。）
		//
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
						plLayout.getSignReversedNames().add(o.toString().trim());
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
						bsLayout.getSignReversedNames().add(o.toString().trim());
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
						plLayout.getAlwaysShownNames().add(o.toString().trim());
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
						bsLayout.getAlwaysShownNames().add(o.toString().trim());
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
						plLayout.getHiddenNamesIfZero().add(o.toString().trim());
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
						bsLayout.getHiddenNamesIfZero().add(o.toString().trim());
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
					if(accountTitle != null) {
						List<AccountTitle> list = new LinkedList<AccountTitle>();
						list.add(accountTitle);
						callback.setAccountTitles(node, list);
					}
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
					if(accountTitle != null) {
						list.add(accountTitle);
					}
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

		Set<AccountTitle> plAccountTitles = retrieve(plLayout.getRoot());
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

		Set<AccountTitle> bsAccountTitles = retrieve(bsLayout.getRoot());
		for(AccountTitle accountTitle : accountTitles) {
			if(accountTitle.getType() == AccountType.Assets && !bsAccountTitles.contains(accountTitle)) {
				System.out.println(" [警告] 貸借対照表に「" + accountTitle.getDisplayName() + "」が含まれていません。");
				valid = false;
			}
			if(accountTitle.getType() == AccountType.Liabilities && !bsAccountTitles.contains(accountTitle)) {
				System.out.println(" [警告] 貸借対照表に「" + accountTitle.getDisplayName() + "」が含まれていません。");
				valid = false;
			}
			if(accountTitle.getType() == AccountType.Equity && !bsAccountTitles.contains(accountTitle)) {
				System.out.println(" [警告] 貸借対照表に「" + accountTitle.getDisplayName() + "」が含まれていません。");
				valid = false;
			}
		}

		if(!valid) {
			System.out.println();
		}

		return valid;
	}

	/** 勘定科目のセットを取得します。
	 *
	 * @return 勘定科目のセット
	 * @throws IOException I/Oエラーが発生した場合
	 */
	public Set<AccountTitle> getAccountTitles() throws IOException {
		read(false);
		return accountTitles;
	}

	/** 勘定科目のセットを取得します。
	 *
	 * @param skipErrors
	 * @return 勘定科目のセット
	 * @throws IOException I/Oエラーが発生した場合
	 */
	public Set<AccountTitle> getAccountTitles(boolean skipErrors) throws IOException {
		read(skipErrors);
		return accountTitles;
	}

	/** 損益計算書の構成情報を返します。
	 *
	 * @return 損益計算書の構成情報
	 * @throws IOException I/Oエラーが発生した場合
	 */
	public ProfitAndLossLayout getProfitAndLossLayout() throws IOException {
		read(false);
		return plLayout;
	}

	/** 損益計算書の構成情報を返します。
	 *
	 * @param skipErrors
	 * @return 損益計算書の構成情報
	 * @throws IOException I/Oエラーが発生した場合
	 */
	public ProfitAndLossLayout getProfitAndLossLayout(boolean skipErrors) throws IOException {
		read(skipErrors);
		return plLayout;
	}

	/** 貸借対照表の構成情報を返します。
	 *
	 * @return 貸借対照表の構成情報
	 * @throws IOException I/Oエラーが発生した場合
	 */
	public BalanceSheetLayout getBalanceSheetLayout() throws IOException {
		read(false);
		return bsLayout;
	}

	/** 貸借対照表の構成情報を返します。
	 *
	 * @param skipErrors
	 * @return 貸借対照表の構成情報
	 * @throws IOException I/Oエラーが発生した場合
	 */
	public BalanceSheetLayout getBalanceSheetLayout(boolean skipErrors) throws IOException {
		read(skipErrors);
		return bsLayout;
	}

	/** 社員資本等変動計算書の構成情報を返します。
	 *
	 * @return 社員資本等変動計算書の構成情報
	 * @throws IOException I/Oエラーが発生した場合
	 */
	public StatementOfChangesInEquityLayout getStatementOfChangesInEquityLayout() throws IOException {
		read(false);
		return sceLayout;
	}

	/** 社員資本等変動計算書の構成情報を返します。
	 *
	 * @param skipErrors
	 * @return 社員資本等変動計算書の構成情報
	 * @throws IOException I/Oエラーが発生した場合
	 */
	public StatementOfChangesInEquityLayout getStatementOfChangesInEquityLayout(boolean skipErrors) throws IOException {
		read(skipErrors);
		return sceLayout;
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

	/// save ///

	public static synchronized void write(Path file, Set<AccountTitle> accountTitles, ProfitAndLossLayout plLayout, BalanceSheetLayout bsLayout) throws IOException {
		write(file, accountTitles, plLayout, bsLayout, null);
	}

	public static synchronized void write(Path file, Set<AccountTitle> accountTitles, ProfitAndLossLayout plLayout, BalanceSheetLayout bsLayout, StatementOfChangesInEquityLayout sceLayout) throws IOException {
		StringBuilder sb = new StringBuilder();

		sb.append(getYaml(accountTitles));
		sb.append(plLayout.getYaml(accountTitles));
		sb.append(bsLayout.getYaml(accountTitles));
		if(sceLayout != null) {
			sb.append(sceLayout.getYaml(accountTitles));
		}

		Path tmpFile = null;
		try {
			Path dir = file.getParent();
			if(Files.notExists(dir)) {
				Files.createDirectories(dir);
			}
			tmpFile = dir.resolve("勘定科目.tmp");
			Files.writeString(tmpFile, sb.toString(), StandardCharsets.UTF_8,
					StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING,
					StandardOpenOption.WRITE,
					StandardOpenOption.SYNC);

			try {
				Files.move(tmpFile, file,
						StandardCopyOption.REPLACE_EXISTING,
						StandardCopyOption.ATOMIC_MOVE);
			} catch(AtomicMoveNotSupportedException e) {
				// 特定の環境において同一ドライブ・同一フォルダー内でのファイル移動であっても
				// AtomicMoveNotSupportedException がスローされることがあるようです。
				// AtomicMoveNotSupportedException がスローされた場合、ATOMIC_MOVE なしで移動を試みます。
				Files.move(tmpFile, file,
						StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			if(tmpFile != null) {
				try { Files.deleteIfExists(tmpFile); } catch(Exception ignore) {}
			}
		}
	}

	public static String getYaml(Set<AccountTitle> accountTitles) {
		StringBuilder sb = new StringBuilder();

		List<String> assets = new ArrayList<>();
		List<String> liabilities = new ArrayList<>();
		List<String> equity = new ArrayList<>();
		List<String> revenue = new ArrayList<>();
		List<String> expense = new ArrayList<>();

		for(AccountTitle accountTitle : accountTitles) {
			switch (accountTitle.getType()) {
				case Assets:
					assets.add(accountTitle.getDisplayName());
					break;
				case Liabilities:
					liabilities.add(accountTitle.getDisplayName());
					break;
				case Equity:
					equity.add(accountTitle.getDisplayName());
					break;
				case Revenue:
					revenue.add(accountTitle.getDisplayName());
					break;
				case Expense:
					expense.add(accountTitle.getDisplayName());
			}
		}

		sb.append("\"仕訳\":\r\n");

		sb.append("  \"資産\" : [ ");
		for(int i = 0; i < assets.size(); i++) {
			sb.append("\"");
			sb.append(YamlBeansUtil.escape(assets.get(i)));
			sb.append("\"");
			if(i + 1 < assets.size()) {
				sb.append(", ");
			}
		}
		sb.append(" ]\r\n");

		sb.append("  \"負債\" : [ ");
		for(int i = 0; i < liabilities.size(); i++) {
			sb.append("\"");
			sb.append(YamlBeansUtil.escape(liabilities.get(i)));
			sb.append("\"");
			if(i + 1 < liabilities.size()) {
				sb.append(", ");
			}
		}
		sb.append(" ]\r\n");

		// 個人か法人かを判定します。勘定科目「資本金」を含んでいる場合は法人、そうでなければ個人と判断します。
		// 個人の場合は見出しを「資本」とし、法人の場合は見出しを「純資産」とします。
		boolean isSoloProprietorship = true;
		for(String name : equity) {
			if(name.equals("資本金")) {
				isSoloProprietorship = false;
				break;
			}
		}
		if(isSoloProprietorship) {
			sb.append("  \"資本\" : [ ");
		} else {
			sb.append("  \"純資産\" : [ ");
		}
		for(int i = 0; i < equity.size(); i++) {
			sb.append("\"");
			sb.append(YamlBeansUtil.escape(equity.get(i)));
			sb.append("\"");
			if(i + 1 < equity.size()) {
				sb.append(", ");
			}
		}
		sb.append(" ]\r\n");

		sb.append("  \"収益\" : [ ");
		for(int i = 0; i < revenue.size(); i++) {
			sb.append("\"");
			sb.append(YamlBeansUtil.escape(revenue.get(i)));
			sb.append("\"");
			if(i + 1 < revenue.size()) {
				sb.append(", ");
			}
		}
		sb.append(" ]\r\n");

		sb.append("  \"費用\" : [ ");
		for(int i = 0; i < expense.size(); i++) {
			sb.append("\"");
			sb.append(YamlBeansUtil.escape(expense.get(i)));
			sb.append("\"");
			if(i + 1 < expense.size()) {
				sb.append(", ");
			}
		}
		sb.append(" ]\r\n");

		sb.append("\r\n\r\n");
		return sb.toString();
	}
}
