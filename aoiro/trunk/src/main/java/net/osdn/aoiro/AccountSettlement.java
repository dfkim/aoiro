package net.osdn.aoiro;

import java.io.PrintStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.osdn.aoiro.model.AccountTitle;
import net.osdn.aoiro.model.AccountType;
import net.osdn.aoiro.model.Creditor;
import net.osdn.aoiro.model.Debtor;
import net.osdn.aoiro.model.JournalEntry;
import net.osdn.aoiro.model.ProportionalDivision;

/** 決算
 *
 */
public class AccountSettlement {
	
	/** 個人事業主かどうか */
	private boolean isSoloProprietorship;
	
	/** 決算日 */
	private LocalDate date;
	
	/** 勘定科目セット */
	private Set<AccountTitle> accountTitles;
	
	/** メッセージ出力先 */
	private PrintStream out;
	
	public AccountSettlement(Set<AccountTitle> accountTitles, boolean isSoloproprietorship) {
		this.accountTitles = accountTitles;
		this.isSoloProprietorship = isSoloproprietorship;
	}
	
	public void setPrintStream(PrintStream out) {
		this.out = out;
	}
	
	/** 仕訳リストに決算仕訳を追加します。
	 * 
	 * @param journalEntries 仕訳リスト
	 * @param proportionalDivisions 家事按分リスト
	 */
	public void addClosingEntries(List<JournalEntry> journalEntries, List<ProportionalDivision> proportionalDivisions) {
		if(date == null) {
			date = getClosingDate(journalEntries, isSoloProprietorship);
		}
		if(date == null) {
			throw new IllegalStateException("決算日が指定されていません。");
		}

		// 勘定科目を並べ替えるための順序リストです。
		List<AccountTitle> order = new ArrayList<>(accountTitles);

		//家事按分
		if(proportionalDivisions != null) {
			AccountTitle ownersDrawing = AccountTitle.getByDisplayName(accountTitles, "事業主貸");
			if(ownersDrawing != null) {
				List<Debtor> debtors = new ArrayList<Debtor>();
				List<Creditor> creditors = new ArrayList<Creditor>();
				
				for(ProportionalDivision proportionalDivision : proportionalDivisions) {
					long debtorTotal = 0;
					long creditorTotal = 0;
					for(int i = 0; i < journalEntries.size(); i++) {
						JournalEntry entry = journalEntries.get(i);
						for(Debtor debtor : entry.getDebtors()) {
							if(debtor.getAccountTitle().equals(proportionalDivision.getAccountTitle())) {
								debtorTotal += debtor.getAmount();
							}
						}
						for(Creditor creditor : entry.getCreditors()) {
							if(creditor.getAccountTitle().equals(proportionalDivision.getAccountTitle())) {
								creditorTotal += creditor.getAmount();
							}
						}
					}
					if(debtorTotal > creditorTotal) {
						double total = (debtorTotal - creditorTotal) * (1.0d - proportionalDivision.getBusinessRatio());
						long longTotal = Math.round(total);
						if(longTotal != 0) {
							creditors.add(new Creditor(proportionalDivision.getAccountTitle(), longTotal));
						}
					} else if(creditorTotal > debtorTotal) {
						double total = (creditorTotal - debtorTotal) * (1.0d - proportionalDivision.getBusinessRatio());
						long longTotal = Math.round(total);
						if(longTotal != 0) {
							debtors.add(new Debtor(proportionalDivision.getAccountTitle(), longTotal));
						}
					}
				}
				if(debtors.size() > 0) {
					//ソート
					Collections.sort(debtors, Comparator.comparingInt(o -> order.indexOf(o.getAccountTitle())));

					long creditorTotal = 0;
					for(Debtor debtor : debtors) {
						creditorTotal += debtor.getAmount();
					}
					Creditor creditor = new Creditor(ownersDrawing, creditorTotal);
					JournalEntry entry = new JournalEntry(date, "家事按分", debtors, Arrays.asList(creditor));
					journalEntries.add(entry);
				}
				if(creditors.size() > 0) {
					//ソート
					Collections.sort(creditors, Comparator.comparingInt(o -> order.indexOf(o.getAccountTitle())));

					long debtorTotal = 0;
					for(Creditor creditor : creditors) {
						debtorTotal += creditor.getAmount();
					}
					Debtor debtor = new Debtor(ownersDrawing, debtorTotal);
					JournalEntry entry = new JournalEntry(date, "家事按分", Arrays.asList(debtor), creditors);
					journalEntries.add(entry);
				}
				if(out != null && (debtors.size() > 0 || creditors.size() > 0)) {
					out.println("  家事按分の振替が完了しました。");
				}
			}
		}

		//未払消費税等・未収消費税等
		//  仮受消費税等・仮払消費税等の片方または両方が 0 ではない場合、
		//  仮受消費税等・仮払消費税等が 0 になるように未払消費税等または未収消費税等に振り替えます。
		//  ただし、既に未払消費税等または未収消費税等が仕訳に含まている場合は手動で決算仕訳を入力したものと見なして何もおこないません。
		AccountTitle karibarai = AccountTitle.getByDisplayName(accountTitles, "仮払消費税等");
		AccountTitle kariuke = AccountTitle.getByDisplayName(accountTitles, "仮受消費税等");
		AccountTitle misyuu = AccountTitle.getByDisplayName(accountTitles, "未収消費税等");
		AccountTitle mibarai = AccountTitle.getByDisplayName(accountTitles, "未払消費税等");
		if(karibarai != null && kariuke != null) {
			boolean existsSettlement = false;
			long karibaraiTotal = 0;
			long kariukeTotal = 0;
			for(int i = 0; i < journalEntries.size(); i++) {
				JournalEntry entry = journalEntries.get(i);
				for(Debtor debtor : entry.getDebtors()) {
					if(debtor.getAccountTitle().equals(karibarai)) {
						karibaraiTotal += debtor.getAmount();
					}
					if(debtor.getAccountTitle().equals(kariuke)) {
						kariukeTotal -= debtor.getAmount();
					}
					if(misyuu != null && debtor.getAccountTitle().equals(misyuu)) {
						existsSettlement = true;
					}
					if(mibarai != null && debtor.getAccountTitle().equals(mibarai)) {
						existsSettlement = true;
					}
				}
				for(Creditor creditor : entry.getCreditors()) {
					if(creditor.getAccountTitle().equals(kariuke)) {
						kariukeTotal += creditor.getAmount();
					}
					if(creditor.getAccountTitle().equals(karibarai)) {
						karibaraiTotal -= creditor.getAmount();
					}
					if(mibarai != null && creditor.getAccountTitle().equals(mibarai)) {
						existsSettlement = true;
					}
					if(misyuu != null && creditor.getAccountTitle().equals(misyuu)) {
						existsSettlement = true;
					}
				}
			}
			if(!existsSettlement && (karibaraiTotal != 0 || kariukeTotal != 0)) {
				List<Debtor> debtors = new ArrayList<>();
				List<Creditor> creditors = new ArrayList<>();

				if(kariukeTotal > 0) {
					debtors.add(new Debtor(kariuke, kariukeTotal));
				} else if(kariukeTotal < 0) {
					creditors.add(new Creditor(kariuke, -kariukeTotal));
				}
				if(karibaraiTotal > 0) {
					creditors.add(new Creditor(karibarai, karibaraiTotal));
				} else if(karibaraiTotal < 0) {
					debtors.add(new Debtor(karibarai, -karibaraiTotal));
				}

				if(kariukeTotal - karibaraiTotal >= 0) {
					if(mibarai != null) {
						creditors.add(new Creditor(mibarai, kariukeTotal - karibaraiTotal));
						JournalEntry e = new JournalEntry(date, "未払消費税等への振替", debtors, creditors);
						journalEntries.add(e);
						if(out != null && (debtors.size() > 0 || creditors.size() > 0)) {
							out.println("  消費税等の振替が完了しました。");
						}
					}
				} else {
					if(misyuu != null) {
						debtors.add(new Debtor(misyuu, karibaraiTotal - kariukeTotal));
						JournalEntry e = new JournalEntry(date, "未収消費税等への振替", debtors, creditors);
						journalEntries.add(e);
						if(out != null && (debtors.size() > 0 || creditors.size() > 0)) {
							out.println("  消費税等の振替が完了しました。");
						}
					}
				}
			}
		}

		//収益勘定科目
		Set<AccountTitle> revenueAccountTitles = new LinkedHashSet<>();
		
		//費用勘定科目
		Set<AccountTitle> expenseAccountTitles = new LinkedHashSet<>();
		
		//資産勘定科目
		Set<AccountTitle> assetsAccountTitles = new LinkedHashSet<>();
		
		//負債勘定科目
		Set<AccountTitle> liabilitiesAccountTitles = new LinkedHashSet<>();
		
		//資本（純資産）勘定科目
		Set<AccountTitle> equityAccountTitles = new LinkedHashSet<>();
		
		//使用されている収益勘定科目と費用勘定科目を抽出します。
		for(JournalEntry entry : journalEntries) {
			for(Creditor creditor : entry.getCreditors()) {
				if(creditor.getAccountTitle().getType() == AccountType.Revenue) {
					revenueAccountTitles.add(creditor.getAccountTitle());
				}
				if(creditor.getAccountTitle().getType() == AccountType.Expense) {
					expenseAccountTitles.add(creditor.getAccountTitle());
				}
				if(creditor.getAccountTitle().getType() == AccountType.Assets) {
					assetsAccountTitles.add(creditor.getAccountTitle());
				}
				if(creditor.getAccountTitle().getType() == AccountType.Liabilities) {
					liabilitiesAccountTitles.add(creditor.getAccountTitle());
				}
			}
			for(Debtor debtor : entry.getDebtors()) {
				if(debtor.getAccountTitle().getType() == AccountType.Revenue) {
					revenueAccountTitles.add(debtor.getAccountTitle());
				}
				if(debtor.getAccountTitle().getType() == AccountType.Expense) {
					expenseAccountTitles.add(debtor.getAccountTitle());
				}
				if(debtor.getAccountTitle().getType() == AccountType.Assets) {
					assetsAccountTitles.add(debtor.getAccountTitle());
				}
				if(debtor.getAccountTitle().getType() == AccountType.Liabilities) {
					liabilitiesAccountTitles.add(debtor.getAccountTitle());
				}
			}
		}
		
		//損益
		long incomeSummary = 0;

		//全ての収益勘定残高を損益勘定へ振替します。
		{
			List<Debtor> debtors = new ArrayList<>();
			long debtorsTotal = 0;
			List<Creditor> creditors = new ArrayList<>();
			long creditorsTotal = 0;
			
			for(AccountTitle accountTitle : revenueAccountTitles) {
				long total = 0;
				List<JournalEntry> entries = getJournalEntriesByAccount(journalEntries, accountTitle);
				for(JournalEntry entry : entries) {
					for(Creditor creditor : entry.getCreditors()) {
						if(creditor.getAccountTitle().equals(accountTitle)) {
							total += creditor.getAmount();
						}
					}
					for(Debtor debtor : entry.getDebtors()) {
						if(debtor.getAccountTitle().equals(accountTitle)) {
							total -= debtor.getAmount();
						}
					}
				}
				if(total >= 0) {
					//借方
					Debtor debtor = new Debtor(accountTitle, +total);
					debtors.add(debtor);
					debtorsTotal += (+total);
				} else {
					//貸方
					Creditor creditor = new Creditor(accountTitle, -total);
					creditors.add(creditor);
					creditorsTotal += (-total);
				}
				incomeSummary += total;
			}
			//損益勘定仕訳
			if(debtors.size() > 0) {
				//ソート
				Collections.sort(debtors, Comparator.comparingInt(o -> order.indexOf(o.getAccountTitle())));
				//貸方
				Creditor creditor = new Creditor(AccountTitle.INCOME_SUMMARY, debtorsTotal);
				//仕訳
				JournalEntry entry = new JournalEntry(date, "収益の損益振替", debtors, Arrays.asList(creditor));
				journalEntries.add(entry);
			}
			if(creditors.size() > 0) {
				//ソート
				Collections.sort(creditors, Comparator.comparingInt(o -> order.indexOf(o.getAccountTitle())));
				//借方
				Debtor debtor = new Debtor(AccountTitle.INCOME_SUMMARY, creditorsTotal);
				//仕訳
				JournalEntry entry = new JournalEntry(date, "収益の損益振替", Arrays.asList(debtor), creditors);
				journalEntries.add(entry);
			}
			if(out != null) {
				out.println("  収益の損益振替が完了しました。");
			}
		}
		
		//全ての費用勘定残高を損益勘定へ振替します。
		{
			List<Debtor> debtors = new ArrayList<>();
			long debtorsTotal = 0;
			List<Creditor> creditors = new ArrayList<>();
			long creditorsTotal = 0;
			
			for(AccountTitle accountTitle : expenseAccountTitles) {
				long total = 0;
				List<JournalEntry> entries = getJournalEntriesByAccount(journalEntries, accountTitle);
				for(JournalEntry entry : entries) {
					for(Debtor debtor : entry.getDebtors()) {
						if(debtor.getAccountTitle().equals(accountTitle)) {
							total += debtor.getAmount();
						}
					}
					for(Creditor creditor : entry.getCreditors()) {
						if(creditor.getAccountTitle().equals(accountTitle)) {
							total -= creditor.getAmount();
						}
					}
				}
				if(total >= 0) {
					//貸方
					Creditor creditor = new Creditor(accountTitle, +total);
					creditors.add(creditor);
					creditorsTotal += (+total);
				} else {
					//借方
					Debtor debtor = new Debtor(accountTitle, -total);
					debtors.add(debtor);
					debtorsTotal += (-total);
				}
				incomeSummary -= total;
			}
			//損益勘定仕訳
			if(creditors.size() > 0) {
				//ソート
				Collections.sort(creditors, Comparator.comparingInt(o -> order.indexOf(o.getAccountTitle())));
				//借方
				Debtor debtor = new Debtor(AccountTitle.INCOME_SUMMARY, creditorsTotal);
				//仕訳
				JournalEntry entry = new JournalEntry(date, "費用の損益振替", Arrays.asList(debtor), creditors);
				journalEntries.add(entry);
			}
			if(debtors.size() > 0) {
				//ソート
				Collections.sort(debtors, Comparator.comparingInt(o -> order.indexOf(o.getAccountTitle())));
				//貸方
				Creditor creditor = new Creditor(AccountTitle.INCOME_SUMMARY, debtorsTotal);
				//仕訳
				JournalEntry entry = new JournalEntry(date, "費用の損益振替", debtors, Arrays.asList(creditor));
				journalEntries.add(entry);
			}
			if(out != null) {
				out.println("  費用の損益振替が完了しました。");
			}
		}
		
		//使用されている資産勘定科目と費用勘定科目を抽出します。
		for(JournalEntry entry : journalEntries) {
			for(Creditor creditor : entry.getCreditors()) {
				if(creditor.getAccountTitle().getType() == AccountType.Assets) {
					assetsAccountTitles.add(creditor.getAccountTitle());
				}
				if(creditor.getAccountTitle().getType() == AccountType.Liabilities) {
					liabilitiesAccountTitles.add(creditor.getAccountTitle());
				}
			}
			for(Debtor debtor : entry.getDebtors()) {
				if(debtor.getAccountTitle().getType() == AccountType.Assets) {
					assetsAccountTitles.add(debtor.getAccountTitle());
				}
				if(debtor.getAccountTitle().getType() == AccountType.Liabilities) {
					liabilitiesAccountTitles.add(debtor.getAccountTitle());
				}
			}
		}
		
		
		//損益勘定の差額を資本振替します。
		//貸借対照表の「資本の部」は「純資産の部」に変わりましたが、
		//損益勘定を繰越利益剰余金に振り替えることは「資本振替」といいます。（純資産振替とはいいません）
		{
			if(incomeSummary >= 0) { //損益は0でも振替仕訳を出力します。（収益・費用の仕訳がなくて損益0の場合であっても、です。）
				//借方
				Debtor debtor = new Debtor(AccountTitle.INCOME_SUMMARY, +incomeSummary);
				//貸方
				Creditor creditor;
				if(isSoloProprietorship) {
					creditor = new Creditor(AccountTitle.PRETAX_INCOME, +incomeSummary);
				} else {
					creditor = new Creditor(AccountTitle.RETAINED_EARNINGS, +incomeSummary);
				}
				//仕訳
				JournalEntry entry = new JournalEntry(date, "損益の資本振替", Arrays.asList(debtor), Arrays.asList(creditor));
				journalEntries.add(entry);
			} else if(incomeSummary < 0) {
				//借方
				Debtor debtor;
				if(isSoloProprietorship) {
					debtor = new Debtor(AccountTitle.PRETAX_INCOME, -incomeSummary);
				} else {
					debtor = new Debtor(AccountTitle.RETAINED_EARNINGS, -incomeSummary);
				}
				//貸方
				Creditor creditor = new Creditor(AccountTitle.INCOME_SUMMARY, -incomeSummary);
				//仕訳
				JournalEntry entry = new JournalEntry(date, "損益の資本振替", Arrays.asList(debtor), Arrays.asList(creditor));
				journalEntries.add(entry);
			}
			if(out != null) {
				out.println("  損益の資本振替が完了しました。");
			}
		}

		
		//資産の残高振替
		{
			List<Debtor> debtors = new ArrayList<>();
			long debtorsTotal = 0;
			List<Creditor> creditors = new ArrayList<>();
			long creditorsTotal = 0;
			
			for(AccountTitle accountTitle : assetsAccountTitles) {
				long total = 0;
				List<JournalEntry> entries = getJournalEntriesByAccount(journalEntries, accountTitle);
				for(JournalEntry entry : entries) {
					for(Debtor debtor : entry.getDebtors()) {
						if(debtor.getAccountTitle().equals(accountTitle)) {
							total += debtor.getAmount();
						}
					}
					for(Creditor creditor : entry.getCreditors()) {
						if(creditor.getAccountTitle().equals(accountTitle)) {
							total -= creditor.getAmount();
						}
					}
				}
				if(total >= 0) { //残高は0でも振替仕訳を出力します。(ノーマルバランスの逆貸借で作成します。)
					//貸方
					Creditor creditor = new Creditor(accountTitle, +total);
					creditors.add(creditor);
					creditorsTotal += (+total);
				} else if(total < 0) {
					//借方
					Debtor debtor = new Debtor(accountTitle, -total);
					debtors.add(debtor);
					debtorsTotal += (-total);
				}
			}
			//残高勘定仕訳
			if(creditors.size() > 0) {
				//ソート
				Collections.sort(creditors, Comparator.comparingInt(o -> order.indexOf(o.getAccountTitle())));
				//借方
				Debtor debtor = new Debtor(AccountTitle.BALANCE, creditorsTotal);
				//仕訳
				JournalEntry entry = new JournalEntry(date, "資産の残高振替", Arrays.asList(debtor), creditors);
				journalEntries.add(entry);
			}
			if(debtors.size() > 0) {
				//ソート
				Collections.sort(debtors, Comparator.comparingInt(o -> order.indexOf(o.getAccountTitle())));
				//貸方
				Creditor creditor = new Creditor(AccountTitle.BALANCE, debtorsTotal);
				//仕訳
				JournalEntry entry = new JournalEntry(date, "資産の残高振替", debtors, Arrays.asList(creditor));
				journalEntries.add(entry);
			}
			if(out != null) {
				out.println("  資産の残高振替が完了しました。");
			}
		}
		
		//負債の残高振替
		{
			List<Debtor> debtors = new ArrayList<>();
			long debtorsTotal = 0;
			List<Creditor> creditors = new ArrayList<>();
			long creditorsTotal = 0;
			
			for(AccountTitle accountTitle : liabilitiesAccountTitles) {
				long total = 0;
				List<JournalEntry> entries = getJournalEntriesByAccount(journalEntries, accountTitle);
				for(JournalEntry entry : entries) {
					for(Creditor creditor : entry.getCreditors()) {
						if(creditor.getAccountTitle().equals(accountTitle)) {
							total += creditor.getAmount();
						}
					}
					for(Debtor debtor : entry.getDebtors()) {
						if(debtor.getAccountTitle().equals(accountTitle)) {
							total -= debtor.getAmount();
						}
					}
				}
				if(total >= 0) { //残高は0でも振替仕訳を出力します。(ノーマルバランスの逆貸借で作成します。)
					//借方
					Debtor debtor = new Debtor(accountTitle, +total);
					debtors.add(debtor);
					debtorsTotal += (+total);
				} else if(total < 0) {
					//貸方
					Creditor creditor = new Creditor(accountTitle, -total);
					creditors.add(creditor);
					creditorsTotal += (-total);
				}
			}
			//残高勘定仕訳
			if(debtors.size() > 0) {
				//ソート
				Collections.sort(debtors, Comparator.comparingInt(o -> order.indexOf(o.getAccountTitle())));
				//貸方
				Creditor creditor = new Creditor(AccountTitle.BALANCE, debtorsTotal);
				//仕訳
				JournalEntry entry = new JournalEntry(date, "負債の残高振替", debtors, Arrays.asList(creditor));
				journalEntries.add(entry);
			}
			if(creditors.size() > 0) {
				//ソート
				Collections.sort(creditors, Comparator.comparingInt(o -> order.indexOf(o.getAccountTitle())));
				//借方
				Debtor debtor = new Debtor(AccountTitle.BALANCE, creditorsTotal);
				//仕訳
				JournalEntry entry = new JournalEntry(date, "負債の残高振替", Arrays.asList(debtor), creditors);
				journalEntries.add(entry);
			}
			if(out != null) {
				out.println("  負債の残高振替が完了しました。");
			}
		}
		
		//使用されている資本（純資産）勘定科目を抽出します。
		for(JournalEntry entry : journalEntries) {
			for(Creditor creditor : entry.getCreditors()) {
				if(creditor.getAccountTitle().getType() == AccountType.Equity) {
					equityAccountTitles.add(creditor.getAccountTitle());
				}
			}
			for(Debtor debtor : entry.getDebtors()) {
				if(debtor.getAccountTitle().getType() == AccountType.Equity) {
					equityAccountTitles.add(debtor.getAccountTitle());
				}
			}
		}
		
		//資本（純資産）の残高振替
		{
			List<Debtor> debtors = new ArrayList<>();
			long debtorsTotal = 0;
			List<Creditor> creditors = new ArrayList<>();
			long creditorsTotal = 0;
			
			for(AccountTitle accountTitle : equityAccountTitles) {
				long total = 0;
				List<JournalEntry> entries = getJournalEntriesByAccount(journalEntries, accountTitle);
				for(JournalEntry entry : entries) {
					for(Creditor creditor : entry.getCreditors()) {
						if(creditor.getAccountTitle().equals(accountTitle)) {
							total += creditor.getAmount();
						}
					}
					for(Debtor debtor : entry.getDebtors()) {
						if(debtor.getAccountTitle().equals(accountTitle)) {
							total -= debtor.getAmount();
						}
					}
				}
				if(total >= 0) { //残高は0でも振替仕訳を出力します。(ノーマルバランスの逆貸借で作成します。)
					//借方
					Debtor debtor = new Debtor(accountTitle, +total);
					debtors.add(debtor);
					debtorsTotal += (+total);
				} else if(total < 0) {
					//貸方
					Creditor creditor = new Creditor(accountTitle, -total);
					creditors.add(creditor);
					creditorsTotal += (-total);
				}
			}
			//残高勘定仕訳
			{
				// 個人の場合は資本の残高振替、会社の場合は純資産の残高振替と表示します。
				String description = isSoloProprietorship ? "資本の残高振替" : "純資産の残高振替";
				if(debtors.size() > 0) {
					//ソート
					Collections.sort(debtors, Comparator.comparingInt(o -> order.indexOf(o.getAccountTitle())));
					//貸方
					Creditor creditor = new Creditor(AccountTitle.BALANCE, debtorsTotal);
					//仕訳
					JournalEntry incomeSummaryEntry = new JournalEntry(date, description, debtors, Arrays.asList(creditor));
					journalEntries.add(incomeSummaryEntry);
				}
				if(creditors.size() > 0) {
					//ソート
					Collections.sort(creditors, Comparator.comparingInt(o -> order.indexOf(o.getAccountTitle())));
					//借方
					Debtor debtor = new Debtor(AccountTitle.BALANCE, creditorsTotal);
					//仕訳
					JournalEntry incomeSummaryEntry = new JournalEntry(date, description, Arrays.asList(debtor), creditors);
					journalEntries.add(incomeSummaryEntry);
				}
				if(out != null) {
					out.println("  " + description + "が完了しました。");
				}
			}
		}
	}
	
	/** 仕訳リストから開始日を求めます。
	 * 
	 * @param journalEntries 仕訳リスト
	 * @return 開始日
	 */
	public static LocalDate getOpeningDate(List<JournalEntry> journalEntries, boolean isSoloProprietorship) {
		LocalDate date = null;
		
		for(JournalEntry entry : journalEntries) {
			if(date == null) {
				date = entry.getDate();
			} else {
				LocalDate date2 = entry.getDate();
				if(date2 != null && date2.isBefore(date)) {
					date = date2;
				}
			}
		}
		
		return date;
	}
	
	/** 仕訳リストから決算日を求めます。
	 * 
	 * @param journalEntries 仕訳リスト
	 * @return 決算日
	 */
	public static LocalDate getClosingDate(List<JournalEntry> journalEntries, boolean isSoloProprietorship) {
		LocalDate date = null;
		
		if(isSoloProprietorship) {
			//個人事業主の場合、仕訳から年を求めて、その年の12/31を決算日とします。
			if(journalEntries.size() > 0) {
				for(JournalEntry entry : journalEntries) {
					if(entry.getDate() != null) {
						date = LocalDate.of(entry.getDate().getYear(), 12, 31);
						break;
					}
				}
			}
		} else {
			//法人の場合は仕訳データ内の最小日付から決算日を求めます。
			LocalDate opening = null;
			//LocalDate closing = null;
			for(JournalEntry entry : journalEntries) {
				if(opening == null) {
					opening = entry.getDate();
				} else {
					LocalDate opening2 = entry.getDate();
					if(opening2 != null && opening2.isBefore(opening2)) {
						opening = opening2;
					}
				}
			}
			if(opening != null) {
				// 最小日付の 1年後の前月の末日
				date = opening.plusYears(1).minusMonths(1);
				date = date.withDayOfMonth(date.lengthOfMonth());
			}
		}
		return date;
	}
	
	/** 指定した勘定科目を含む仕訳データを取得します。
	 * 
	 * @param journalEntries 仕訳リスト
	 * @param accountTitle 勘定科目
	 * @return 指定した勘定科目を含む仕訳データのリスト
	 */
	public static List<JournalEntry> getJournalEntriesByAccount(List<JournalEntry> journalEntries, AccountTitle accountTitle) {
		List<JournalEntry> entriesByAccount = new ArrayList<JournalEntry>();
		if(accountTitle != null) {
			NEXT_JOURNAL_ENTRY:
			for(JournalEntry entry : journalEntries) {
				for(Debtor debtor : entry.getDebtors()) {
					if(accountTitle.equals(debtor.getAccountTitle())) {
						entriesByAccount.add(entry);
						continue NEXT_JOURNAL_ENTRY;
					}
				}
				for(Creditor creditor : entry.getCreditors()) {
					if(accountTitle.equals(creditor.getAccountTitle())) {
						entriesByAccount.add(entry);
						continue NEXT_JOURNAL_ENTRY;
					}
				}
			}
		}
		return entriesByAccount;
	}
}
