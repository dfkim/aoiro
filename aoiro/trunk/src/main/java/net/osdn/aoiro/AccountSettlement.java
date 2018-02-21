package net.osdn.aoiro;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
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
	private Date date;
	
	/** 勘定科目リスト */
	private List<AccountTitle> accountTitles;
	
	/** メッセージ出力先 */
	private PrintStream out;
	
	public AccountSettlement(List<AccountTitle> accountTitles, boolean isSoloproprietorship) {
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
		
		//家事按分
		if(proportionalDivisions != null) {
			AccountTitle ownersDrawing = AccountTitle.getByDisplayName(accountTitles, "事業主貸");
			if(ownersDrawing == null) {
				throw new IllegalArgumentException("勘定科目に事業主貸が定義されていないため家事按分ができませんでした。");
			}
			
			List<Debtor> debtors = new ArrayList<Debtor>();
			List<Creditor> creditors = new ArrayList<Creditor>();
			
			for(ProportionalDivision proportionalDivision : proportionalDivisions) {
				int debtorTotal = 0;
				int creditorTotal = 0;
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
					double total = (debtorTotal - creditorTotal) * (1.0 - proportionalDivision.getBusinessRatio());
					if(!(-1.0 < total && total < +1.0)) {
						creditors.add(new Creditor(proportionalDivision.getAccountTitle(), (int)Math.floor(total)));
					}
				} else if(creditorTotal > debtorTotal) {
					double total = (creditorTotal - debtorTotal) * (1.0 - proportionalDivision.getBusinessRatio());
					if(!(-1.0 < total && total < +1.0)) {
						debtors.add(new Debtor(proportionalDivision.getAccountTitle(), (int)Math.floor(total)));
					}
				}
			}
			if(debtors.size() > 0) {
				int creditorTotal = 0;
				for(Debtor debtor : debtors) {
					creditorTotal += debtor.getAmount();
				}
				Creditor creditor = new Creditor(ownersDrawing, creditorTotal);
				JournalEntry entry = new JournalEntry(date, "家事按分", debtors, Arrays.asList(creditor));
				journalEntries.add(entry);
			}
			if(creditors.size() > 0) {
				int debtorTotal = 0;
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
		
		//収益勘定科目
		Set<AccountTitle> revenueAccountTitles = new LinkedHashSet<AccountTitle>();
		
		//費用勘定科目
		Set<AccountTitle> expenseAccountTitles = new LinkedHashSet<AccountTitle>();
		
		//資産勘定科目
		Set<AccountTitle> assetsAccountTitles = new LinkedHashSet<AccountTitle>();
		
		//負債勘定科目
		Set<AccountTitle> liabilitiesAccountTitles = new LinkedHashSet<AccountTitle>();
		
		//資本勘定科目
		Set<AccountTitle> equityAccountTitles = new LinkedHashSet<AccountTitle>();
		
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
		
		//int incomeSummaryCreditorGrandTotal = 0;
		//int incomeSummaryDebtorGrandTotal = 0;
		//利益剰余金
		int retainedEarnings = 0;
		
		//全ての収益勘定残高を損益勘定へ振替します。
		{
			List<Debtor> debtors = new ArrayList<Debtor>();
			int debtorsTotal = 0;
			List<Creditor> creditors = new ArrayList<Creditor>();
			int creditorsTotal = 0;
			
			for(AccountTitle accountTitle : revenueAccountTitles) {
				int total = 0;
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
				retainedEarnings += total;
			}
			//損益勘定仕訳
			if(debtors.size() > 0) {
				//貸方
				Creditor creditor = new Creditor(AccountTitle.INCOME_SUMMARY, debtorsTotal);
				//仕訳
				JournalEntry entry = new JournalEntry(date, "収益の損益振替", debtors, Arrays.asList(creditor));
				journalEntries.add(entry);
			}
			if(creditors.size() > 0) {
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
			List<Debtor> debtors = new ArrayList<Debtor>();
			int debtorsTotal = 0;
			List<Creditor> creditors = new ArrayList<Creditor>();
			int creditorsTotal = 0;
			
			for(AccountTitle accountTitle : expenseAccountTitles) {
				int total = 0;
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
				retainedEarnings -= total;
			}
			//損益勘定仕訳
			if(creditors.size() > 0) {
				//借方
				Debtor debtor = new Debtor(AccountTitle.INCOME_SUMMARY, creditorsTotal);
				//仕訳
				JournalEntry entry = new JournalEntry(date, "費用の損益振替", Arrays.asList(debtor), creditors);
				journalEntries.add(entry);
			}
			if(debtors.size() > 0) {
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
		{
			//FIXME: 個人事業主と法人で資本の勘定科目が違うよ。個人は所得、法人は利益剰余金かな？

			if(retainedEarnings == 0) {
				//損益勘定の差額が 0 のときは振替仕訳を作成しません。
			} else if(retainedEarnings > 0) {
				//借方
				Debtor debtor = new Debtor(AccountTitle.INCOME_SUMMARY, +retainedEarnings);
				//貸方
				Creditor creditor = new Creditor(AccountTitle.RETAINED_EARNINGS, +retainedEarnings);
				//仕訳
				JournalEntry entry = new JournalEntry(date, "損益の資本振替", Arrays.asList(debtor), Arrays.asList(creditor));
				journalEntries.add(entry);
			} else if(retainedEarnings < 0) {
				//借方
				Debtor debtor = new Debtor(AccountTitle.RETAINED_EARNINGS, -retainedEarnings);
				//貸方
				Creditor creditor = new Creditor(AccountTitle.INCOME_SUMMARY, -retainedEarnings);
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
			List<Debtor> debtors = new ArrayList<Debtor>();
			int debtorsTotal = 0;
			List<Creditor> creditors = new ArrayList<Creditor>();
			int creditorsTotal = 0;
			
			for(AccountTitle accountTitle : assetsAccountTitles) {
				int total = 0;
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
				if(total == 0) {
					//
				} else if(total > 0) {
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
				//借方
				Debtor debtor = new Debtor(AccountTitle.BALANCE, creditorsTotal);
				//仕訳
				JournalEntry entry = new JournalEntry(date, "資産の残高振替", Arrays.asList(debtor), creditors);
				journalEntries.add(entry);
			}
			if(debtors.size() > 0) {
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
			List<Debtor> debtors = new ArrayList<Debtor>();
			int debtorsTotal = 0;
			List<Creditor> creditors = new ArrayList<Creditor>();
			int creditorsTotal = 0;
			
			for(AccountTitle accountTitle : liabilitiesAccountTitles) {
				int total = 0;
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
				if(total == 0) {
					//
				} else if(total > 0) {
					//借方
					Debtor debtor = new Debtor(accountTitle, +total);
					debtors.add(debtor);
					debtorsTotal += (+total);
				} else if(total < 0) {
					//借方
					Creditor creditor = new Creditor(accountTitle, -total);
					creditors.add(creditor);
					creditorsTotal += (-total);
				}
			}
			//残高勘定仕訳
			if(debtors.size() > 0) {
				//貸方
				Creditor creditor = new Creditor(AccountTitle.BALANCE, debtorsTotal);
				//仕訳
				JournalEntry entry = new JournalEntry(date, "負債の残高振替", debtors, Arrays.asList(creditor));
				journalEntries.add(entry);
			}
			if(creditors.size() > 0) {
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
		
		//使用されている資本勘定科目を抽出します。
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
		
		//資本の残高振替
		{
			List<Debtor> debtors = new ArrayList<Debtor>();
			int debtorsTotal = 0;
			List<Creditor> creditors = new ArrayList<Creditor>();
			int creditorsTotal = 0;
			
			for(AccountTitle accountTitle : equityAccountTitles) {
				int total = 0;
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
				if(total == 0) {
					//
				} else if(total > 0) {
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
				//貸方
				Creditor creditor = new Creditor(AccountTitle.BALANCE, debtorsTotal);
				//仕訳
				JournalEntry incomeSummaryEntry = new JournalEntry(date, "資本の残高振替", debtors, Arrays.asList(creditor));
				journalEntries.add(incomeSummaryEntry);
			}
			if(creditors.size() > 0) {
				//借方
				Debtor debtor = new Debtor(AccountTitle.BALANCE, creditorsTotal);
				//仕訳
				JournalEntry incomeSummaryEntry = new JournalEntry(date, "資本の残高振替", Arrays.asList(debtor), creditors);
				journalEntries.add(incomeSummaryEntry);
			}
			if(out != null) {
				out.println("  資本の残高振替が完了しました。");
			}
		}
	}
	
	/** 仕訳リストから開始日を求めます。
	 * 
	 * @param journalEntries 仕訳リスト
	 * @return 開始日
	 */
	public static Date getOpeningDate(List<JournalEntry> journalEntries, boolean isSoloProprietorship) {
		Date date = null;
		
		for(JournalEntry entry : journalEntries) {
			if(date == null) {
				date = entry.getDate();
			} else if(entry.getDate().before(date)) {
				date = entry.getDate();
			}
		}
		
		return date;
	}
	
	/** 仕訳リストから決算日を求めます。
	 * 
	 * @param journalEntries 仕訳リスト
	 * @return 決算日
	 */
	public static Date getClosingDate(List<JournalEntry> journalEntries, boolean isSoloProprietorship) {
		Date date = null;
		
		if(isSoloProprietorship) {
			//個人事業主の場合、仕訳から年を求めて、その年の12/31を決算日とします。
			if(journalEntries.size() > 0) {
				JournalEntry entry = journalEntries.get(0);
				Calendar calendar = Calendar.getInstance(Util.getLocale());
				calendar.setTime(entry.getDate());
				calendar.set(Calendar.MONTH, 11);
				calendar.set(Calendar.DAY_OF_MONTH, 31);
				date = calendar.getTime();
			}
		} else {
			//法人の場合は仕訳データ内の最小日付から決算日を求めます。
			Date opening = null;
			Date closing = null;
			for(JournalEntry entry : journalEntries) {
				if(opening == null) {
					opening = entry.getDate();
				} else if(entry.getDate().before(opening)) {
					opening = entry.getDate();
				}
				if(closing == null) {
					closing = entry.getDate();
				} else if(entry.getDate().after(closing)) {
					closing = entry.getDate();
				}
			}
			Calendar calendar = Calendar.getInstance(Util.getLocale());
			calendar.setTime(opening);
			calendar.set(Calendar.DAY_OF_MONTH, 1);
			calendar.add(Calendar.DATE, -1);
			date = calendar.getTime();
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
			for(JournalEntry entry : journalEntries) {
				for(Debtor debtor : entry.getDebtors()) {
					if(accountTitle.equals(debtor.getAccountTitle())) {
						entriesByAccount.add(entry);
					}
				}
				for(Creditor creditor : entry.getCreditors()) {
					if(accountTitle.equals(creditor.getAccountTitle())) {
						entriesByAccount.add(entry);
					}
				}
			}
		}
		return entriesByAccount;
	}
}