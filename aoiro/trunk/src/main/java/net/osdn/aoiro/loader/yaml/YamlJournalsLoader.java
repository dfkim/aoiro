package net.osdn.aoiro.loader.yaml;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Date;
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

	private Map<String, AccountTitle> accountTitleByDisplayName;
	private DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
	private List<JournalEntry> journals = new ArrayList<JournalEntry>();
	
	public YamlJournalsLoader(File file, Set<AccountTitle> accountTitles) throws IOException, ParseException {
		this.accountTitleByDisplayName = new HashMap<String, AccountTitle>();
		for(AccountTitle accountTitle : accountTitles) {
			accountTitleByDisplayName.put(accountTitle.getDisplayName(), accountTitle);
		}
		
		String yaml = AutoDetectReader.readAll(file.toPath());
		@SuppressWarnings("unchecked")
		List<Object> list = (List<Object>)new YamlReader(yaml).read();

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
	
	private JournalEntry parseJournalEntry(Map<String, Object> map) throws ParseException {
		Date date = parseDate(map);
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
				throw new IllegalArgumentException("金額が一致していません: 借方金額 " + debtorsAmount + ", 貸方金額 " + creditorsAmount);
			}
			JournalEntry entry = new JournalEntry(date, description, debtors, creditors);
			return entry;
		} catch(IllegalArgumentException e) {
			if(date == null) {
				throw e;
			} else {
				String message = "[" + new SimpleDateFormat("yyyy-MM-dd").format(date) + "] " + e.getMessage();
				throw new IllegalArgumentException(message, e);
			}
		}
	}
	
	private Date parseDate(Map<String, Object> map) throws ParseException {
		Object obj = map.get("日付");
		if(obj == null) {
			throw new IllegalArgumentException("日付が指定されていません");
		}
		String s = obj.toString()
			.replace('/', '-')
			.replace('.', '-')
			.replace('年', '-')
			.replace('月', '-')
			.replace('日', ' ')
			.trim();
		Date date = dateFormat.parse(s);
		return date;
	}
	
	private String parseDescription(Map<String, Object> map) {
		Object obj = map.get("摘要");
		if(obj == null) {
			throw new IllegalArgumentException("摘要が指定されていません");
		}
		String s = obj.toString().trim();
		return s;
	}
	
	private List<Debtor> parseDebtors(Map<String, Object> map) {
		Object obj = map.get("借方");
		if(obj == null) {
			throw new IllegalArgumentException("借方が指定されていません");
		}
		List<Debtor> debtors = new ArrayList<Debtor>();
		if(obj instanceof List) {
			@SuppressWarnings("unchecked")
			List<Object> list = (List<Object>)obj;
			for(int i = 0; i < list.size(); i++) {
				obj = list.get(i);
				if(obj instanceof Map) {
					@SuppressWarnings("unchecked")
					Map<String, Object> m = (Map<String, Object>)obj;
					Entry<AccountTitle, Integer> accountWithAmount = parseAccountWithAmount(m);
					if(accountWithAmount != null) {
						AccountTitle account = accountWithAmount.getKey();
						int amount = accountWithAmount.getValue();
						Debtor debtor = new Debtor(account, amount);
						debtors.add(debtor);
					}
				}
			}
		} else if(obj instanceof Map) {
			@SuppressWarnings("unchecked")
			Map<String, Object> m = (Map<String, Object>)obj;
			Entry<AccountTitle, Integer> accountWithAmount = parseAccountWithAmount(m);
			if(accountWithAmount != null) {
				AccountTitle account = accountWithAmount.getKey();
				int amount = accountWithAmount.getValue();
				Debtor debtor = new Debtor(account, amount);
				debtors.add(debtor);
			}
		} else {
			throw new IllegalArgumentException("借方の型が不正です: " + obj.getClass());
		}
		return debtors;
	}
	
	private List<Creditor> parseCreditors(Map<String, Object> map) {
		Object obj = map.get("貸方");
		if(obj == null) {
			throw new IllegalArgumentException("貸方が指定されていません");
		}
		List<Creditor> creditors = new ArrayList<Creditor>();
		if(obj instanceof List) {
			@SuppressWarnings("unchecked")
			List<Object> list = (List<Object>)obj;
			for(int i = 0; i < list.size(); i++) {
				obj = list.get(i);
				if(obj instanceof Map) {
					@SuppressWarnings("unchecked")
					Map<String, Object> m = (Map<String, Object>)obj;
					Entry<AccountTitle, Integer> accountWithAmount = parseAccountWithAmount(m);
					if(accountWithAmount != null) {
						AccountTitle account = accountWithAmount.getKey();
						int amount = accountWithAmount.getValue();
						Creditor creditor = new Creditor(account, amount);
						creditors.add(creditor);
					}
				}
			}
		} else if(obj instanceof Map) {
			@SuppressWarnings("unchecked")
			Map<String, Object> m = (Map<String, Object>)obj;
			Entry<AccountTitle, Integer> accountWithAmount = parseAccountWithAmount(m);
			if(accountWithAmount != null) {
				AccountTitle account = accountWithAmount.getKey();
				int amount = accountWithAmount.getValue();
				Creditor creditor = new Creditor(account, amount);
				creditors.add(creditor);
			}
		} else {
			throw new IllegalArgumentException("貸方の型が不正です: " + obj.getClass());
		}
		return creditors;
	}
	
	private Entry<AccountTitle, Integer> parseAccountWithAmount(Map<String, Object> map) {
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
		int amount = Integer.parseInt(s);
		Entry<AccountTitle, Integer> entry = new SimpleEntry<AccountTitle, Integer>(account, amount);
		return entry;
	}
}
