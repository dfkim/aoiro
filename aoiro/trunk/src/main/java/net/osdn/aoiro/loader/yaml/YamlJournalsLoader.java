package net.osdn.aoiro.loader.yaml;

import java.io.File;
import java.io.IOException;
import java.io.InvalidClassException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.esotericsoftware.yamlbeans.YamlReader;

import net.osdn.aoiro.model.AccountTitle;
import net.osdn.aoiro.model.Creditor;
import net.osdn.aoiro.model.Debtor;
import net.osdn.aoiro.model.JournalEntry;
import net.osdn.util.io.AutoDetectReader;

/** YAMLファイルから仕訳をロードします。
 *
 */
public class YamlJournalsLoader {

	private DateTimeFormatter dateParser = DateTimeFormatter.ofPattern("y-M-d");

	private Map<String, AccountTitle> accountTitleByDisplayName;
	private List<JournalEntry> journals = new ArrayList<JournalEntry>();

	private List<String> warnings;

	public YamlJournalsLoader(File file, Set<AccountTitle> accountTitles) throws IOException {
		this(file, accountTitles, null);
	}

	public YamlJournalsLoader(File file, Set<AccountTitle> accountTitles, List<String> warnings) throws IOException {
		this.warnings = warnings;

		this.accountTitleByDisplayName = new HashMap<String, AccountTitle>();
		for(AccountTitle accountTitle : accountTitles) {
			accountTitleByDisplayName.put(accountTitle.getDisplayName(), accountTitle);
		}
		
		String yaml = AutoDetectReader.readAll(file.toPath());
		@SuppressWarnings("unchecked")
		List<Object> list = (List<Object>)new YamlReader(yaml).read();

		if(list == null) {
			return;
		}

		for(Object obj : list) {
			if(obj instanceof Map) {
				@SuppressWarnings("unchecked")
				Map<String, Object> map = (Map<String, Object>)obj;
				JournalEntry entry = parseJournalEntry(map);
				journals.add(entry);
			}
		}
		if(list.size() != journals.size()) {
			throw new IllegalArgumentException("処理できないデータが見つかりました: 入力データ件数 " + list.size() + ", 処理されたデータ件数 " + journals.size());
		}
	}
	
	/** 仕訳リストを取得します。
	 * 
	 * @return 仕訳リスト
	 */
	public List<JournalEntry> getJournalEntries() {
		return journals;
	}
	
	private JournalEntry parseJournalEntry(Map<String, Object> map) {
		LocalDate date = parseDate(map);
		try {
			String description = parseDescription(map);
			List<Debtor> debtors = parseDebtors(map);
			List<Creditor> creditors = parseCreditors(map);
			
			int debtorsAmount = 0;
			for(Debtor debtor : debtors) {
				debtorsAmount += debtor.getAmount();
			}
			int creditorsAmount = 0;
			for(Creditor creditor : creditors) {
				creditorsAmount += creditor.getAmount();
			}
			if(debtorsAmount != creditorsAmount) {
				String message = "金額が一致していません: 借方金額 " + debtorsAmount + ", 貸方金額 " + creditorsAmount;
				if(warnings != null) {
					warnings.add(message);
				} else {
					throw new IllegalArgumentException(message);
				}
			}
			JournalEntry entry = new JournalEntry(date, description, debtors, creditors);
			return entry;
		} catch(IllegalArgumentException e) {
			if(date == null) {
				throw e;
			} else {
				String message = "[" + DateTimeFormatter.ISO_LOCAL_DATE.format(date) + "] " + e.getMessage();
				throw new IllegalArgumentException(message, e);
			}
		}
	}
	
	private LocalDate parseDate(Map<String, Object> map) {
		LocalDate date = null;
		Object obj = null;
		try {
			obj = map.get("日付");
			String s = obj.toString()
					.replace('/', '-')
					.replace('.', '-')
					.replace('年', '-')
					.replace('月', '-')
					.replace('日', ' ')
					.trim();
			date = LocalDate.from(dateParser.parse(s));
		} catch(Exception e) {
			String message = "不正な日付です: " + obj;
			if(warnings != null) {
				warnings.add(message);
			} else {
				throw new IllegalArgumentException(message, e);
			}
		}
		return date;
	}
	
	private String parseDescription(Map<String, Object> map) {
		String description = "";
		Object obj = null;
		try {
			obj = map.get("摘要");
			description = obj.toString().trim();
		} catch(Exception e) {
			String message = "不正な摘要です: " + obj;
			if(warnings != null) {
				warnings.add(message);
			} else {
				throw new IllegalArgumentException(message, e);
			}
		}
		return description;
	}
	
	private List<Debtor> parseDebtors(Map<String, Object> map) {
		List<Debtor> debtors = new ArrayList<Debtor>();
		Object obj = null;
		try {
			obj = map.get("借方");
			if(obj == null) {
				throw new NullPointerException();
			}
			if(obj instanceof List) {
				@SuppressWarnings("unchecked")
				List<Object> list = (List<Object>)obj;
				for(int i = 0; i < list.size(); i++) {
					obj = list.get(i);
					if(obj instanceof Map) {
						@SuppressWarnings("unchecked")
						Map<String, Object> m = (Map<String, Object>)obj;
						Entry<AccountTitle, Long> accountWithAmount = parseAccountWithAmount(m);
						if(accountWithAmount != null) {
							AccountTitle account = accountWithAmount.getKey();
							long amount = accountWithAmount.getValue();
							Debtor debtor = new Debtor(account, amount);
							debtors.add(debtor);
						}
					}
				}
			} else if(obj instanceof Map) {
				@SuppressWarnings("unchecked")
				Map<String, Object> m = (Map<String, Object>)obj;
				Entry<AccountTitle, Long> accountWithAmount = parseAccountWithAmount(m);
				if(accountWithAmount != null) {
					AccountTitle account = accountWithAmount.getKey();
					long amount = accountWithAmount.getValue();
					Debtor debtor = new Debtor(account, amount);
					debtors.add(debtor);
				}
			} else {
				throw new InvalidClassException(obj.getClass().getName());
			}
		} catch(Exception e) {
			String message = "不正な借方です: " + obj;
			if(warnings != null) {
				warnings.add(message);
			} else {
				throw new IllegalArgumentException(message, e);
			}
		}
		return debtors;
	}
	
	private List<Creditor> parseCreditors(Map<String, Object> map) {
		List<Creditor> creditors = new ArrayList<Creditor>();
		Object obj = null;
		try {
			obj = map.get("貸方");
			if(obj == null) {
				throw new NullPointerException();
			}
			if(obj instanceof List) {
				@SuppressWarnings("unchecked")
				List<Object> list = (List<Object>)obj;
				for(int i = 0; i < list.size(); i++) {
					obj = list.get(i);
					if(obj instanceof Map) {
						@SuppressWarnings("unchecked")
						Map<String, Object> m = (Map<String, Object>)obj;
						Entry<AccountTitle, Long> accountWithAmount = parseAccountWithAmount(m);
						if(accountWithAmount != null) {
							AccountTitle account = accountWithAmount.getKey();
							long amount = accountWithAmount.getValue();
							Creditor creditor = new Creditor(account, amount);
							creditors.add(creditor);
						}
					}
				}
			} else if(obj instanceof Map) {
				@SuppressWarnings("unchecked")
				Map<String, Object> m = (Map<String, Object>)obj;
				Entry<AccountTitle, Long> accountWithAmount = parseAccountWithAmount(m);
				if(accountWithAmount != null) {
					AccountTitle account = accountWithAmount.getKey();
					long amount = accountWithAmount.getValue();
					Creditor creditor = new Creditor(account, amount);
					creditors.add(creditor);
				}
			} else {
				throw new InvalidClassException(obj.getClass().getName());
			}
		} catch(Exception e) {
			String message = "不正な貸方です " + obj;
			if(warnings != null) {
				warnings.add(message);
			} else {
				throw new IllegalArgumentException(message, e);
			}
		}
		return creditors;
	}
	
	private Entry<AccountTitle, Long> parseAccountWithAmount(Map<String, Object> map) {
		Object obj = map.get("勘定科目");
		if(obj == null) {
			obj = map.get("科目");
		}
		if(obj == null) {
			throw new IllegalArgumentException("勘定科目が指定されていません");
		}
		String title = obj.toString().trim();
		AccountTitle account = accountTitleByDisplayName.get(title);
		if(account == null) {
			throw new IllegalArgumentException("勘定科目が見つかりません: " + title);
		}
		
		obj = map.get("金額");
		if(obj == null) {
			throw new IllegalArgumentException("金額が指定されていません");
		}
		String s = obj.toString().replace(",", "").trim();
		long amount = Long.parseLong(s);
		Entry<AccountTitle, Long> entry = new SimpleEntry<>(account, amount);
		return entry;
	}
}