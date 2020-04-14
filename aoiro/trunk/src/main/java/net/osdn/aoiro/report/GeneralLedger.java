package net.osdn.aoiro.report;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.osdn.aoiro.AccountSettlement;
import net.osdn.aoiro.model.Account;
import net.osdn.aoiro.model.AccountTitle;
import net.osdn.aoiro.model.AccountType;
import net.osdn.aoiro.model.Creditor;
import net.osdn.aoiro.model.Debtor;
import net.osdn.aoiro.model.JournalEntry;
import net.osdn.pdf_brewer.BrewerData;
import net.osdn.pdf_brewer.PdfBrewer;

/** 総勘定元帳
 * 
 */
public class GeneralLedger {

	private static final int ROWS = 50;
	private static final double ROW_HEIGHT = 5.0;
	
	private Set<AccountTitle> accountTitles;
	private List<JournalEntry> entries;
	int financialYear;
	boolean isFromNewYearsDay;
	
	private List<String> pageData = new ArrayList<String>();
	private List<String> printData;
	
	public GeneralLedger(Set<AccountTitle> accountTitles, List<JournalEntry> journalEntries, boolean isSoloProprietorship) throws IOException {
		this.accountTitles = new LinkedHashSet<AccountTitle>(accountTitles);
		this.accountTitles.add(AccountTitle.INCOME_SUMMARY);
		this.accountTitles.add(AccountTitle.RETAINED_EARNINGS);
		this.accountTitles.add(AccountTitle.PRETAX_INCOME);
		this.accountTitles.add(AccountTitle.BALANCE);
		
		this.entries = journalEntries;
		
		InputStream in = getClass().getResourceAsStream("/templates/総勘定元帳.pb");
		BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		String line;
		while((line = r.readLine()) != null) {
			pageData.add(line);
		}
		r.close();
		
		LocalDate closing = AccountSettlement.getClosingDate(entries, isSoloProprietorship);
		if(closing.getMonthValue() == 12 && closing.getDayOfMonth() == 31) {
			//決算日が 12月31日の場合は会計年度の「年」は決算日の「年」と同じになります。
			financialYear = closing.getYear();
			isFromNewYearsDay = true;
		} else {
			//決算日が 12月31日ではない場合は会計年度の「年」は決算日の前年になります。
			financialYear = closing.getYear() - 1;
			isFromNewYearsDay = false;
		}
		
		//仕訳帳と相互にページ番号を印字するために
		//writeToを呼び出してPDFを作成する前にprepareを呼び出しておく必要があります。
		prepare();
	}
	
	protected void prepare() {
		int pageNumber = 0;
		AccountTitle currentAccountTitle = null;
		
		printData = new ArrayList<String>();
		printData.add("\\media A4");
		
		//穴あけパンチの位置を合わせるための中心線を先頭ページのみ印字します。
		printData.add("\\line-style thin dot");
		printData.add("\\line 0 148.5 5 148.5");
		
		for(AccountTitle accountTitle : accountTitles) {
			int restOfRows = 0;
			int currentRow = 0;
			String sign = "";
			int debtorTotal = 0;
			int creditorTotal = 0;
			int accountTitleTotal = 0;
			List<JournalEntry> entries = getJournalEntriesByAccount(accountTitle);
			for(int j = 0; j < entries.size(); j++) {
				JournalEntry entry = entries.get(j);
				int month = entry.getDate().getMonthValue();
				int day = entry.getDate().getDayOfMonth();
				//この勘定科目を含む勘定のリストを取得します。
				List<Account> accounts = getAccountsByAccountTitle(entry, accountTitle);
				
				for(int k = 0; k < accounts.size(); k++) {
					Account account = accounts.get(k);
					//この勘定を含む相手勘定のリストを取得します。
					List<Account> counterpartAccounts = getCounterpartAccounts(entry, account);
					for(int l = 0; l < counterpartAccounts.size(); l++) {
						Account counterpartAccount = counterpartAccounts.get(l);

						//この仕訳の後で改ページが必要かどうか
						boolean isCarriedForward = false;
						
						//勘定科目内の最後の印字データかどうか
						boolean isLastInAccountTitle =
								(l == counterpartAccounts.size() - 1) &&
								(k == accounts.size() -1) &&
								(j == entries.size() - 1);
						
						//仕訳の印字に必要な行数を求めます。
						int rowsRequired = 1;
						if(!isLastInAccountTitle && (currentRow + 1 == ROWS - 1)) {
							rowsRequired++;
							isCarriedForward = true;
						}
						
						//印字に必要な行数が残り行数を超えているか、または、勘定科目が変わったときに改ページします。
						if(rowsRequired > restOfRows || !accountTitle.equals(currentAccountTitle)) {
							if(++pageNumber >= 2) {
								printData.add("\\new-page");
							}
							if(pageNumber % 2 == 1) {
								//綴じ代(奇数ページ)
								printData.add("\\box 15 0 0 0");
								printData.add("\\line-style thin dot");
								printData.add("\\line 0 0 0 -0");
								printData.add("\\box 25 0 -10 -10");
								
								//テンプレート
								printData.addAll(pageData);
								
								//ページ番号(奇数ページ)
								printData.add("\t\\box 0 0 -3 22");
								printData.add("\t\\font serif 10.5");
								printData.add("\t\\align bottom right");
								printData.add("\t\\text " + pageNumber);
							} else {
								//綴じ代(偶数ページ)
								printData.add("\\box 0 0 -15 0");
								printData.add("\\line-style thin dot");
								printData.add("\\line -0 0 -0 -0");
								printData.add("\\box 10 0 -25 -10");
								
								//テンプレート
								printData.addAll(pageData);
								
								//ページ番号(偶数ページ)
								printData.add("\t\\box 3 0 10 22");
								printData.add("\t\\font serif 10.5");
								printData.add("\t\\align bottom left");
								printData.add("\t\\text " + pageNumber);
							}
							//勘定科目
							printData.add("\t\\box 0 16 -0 9");
							printData.add("\t\\font serif 14");
							printData.add("\t\\align center");
							printData.add("\t\\text " + account.getAccountTitle().getDisplayName());
							currentAccountTitle = account.getAccountTitle();
							
							//年
							if(isFromNewYearsDay) {
								printData.add("\t\\box 0 25 14.5 6");
								printData.add("\t\\align center right");
								printData.add("\t\\font serif 8");
								printData.add("\t\\text 年");
								printData.add("\t\\box 0 25 10.5 6");
								printData.add("\t\\font serif 10");
								printData.add("\t\\align center right");
								printData.add("\t\\text " + financialYear);
							} else {
								printData.add("\t\\box 0 25 14.7 6");
								printData.add("\t\\align center right");
								printData.add("\t\\font serif 8");
								printData.add("\t\\text 年度");
								printData.add("\t\\box 0 25 8.6 6");
								printData.add("\t\\font serif 10");
								printData.add("\t\\align center right");
								printData.add("\t\\text " + financialYear);
							}
							
							//明細印字領域
							printData.add("\t\\box 0 37 -0 -0");
							
							restOfRows = ROWS;
							currentRow = 0;
							
							//勘定科目が変わった場合を除いて改ページが発生した場合、前頁繰越を印字します。
							if(j != 0 || k != 0 || l != 0) {
								//前頁繰越
								printData.add("\t\t\\box " + String.format("16 %.2f 49 %.2f", currentRow * ROW_HEIGHT, ROW_HEIGHT));
								printData.add("\t\t\\font serif 10");
								printData.add("\t\t\\align center right");
								printData.add("\t\t\\text 前頁繰越");
								//借または貸
								printData.add("\t\t\\box " + String.format("135 %.2f 8 %.2f", currentRow * ROW_HEIGHT, ROW_HEIGHT));
								printData.add("\t\t\\font serif 10");
								printData.add("\t\t\\align center");
								printData.add("\t\t\\text " + sign);
								//残高
								printData.add("\t\t\\box " + String.format("143 %.2f 27 %.2f", currentRow * ROW_HEIGHT, ROW_HEIGHT));
								printData.add("\t\t\\font serif 10");
								printData.add("\t\t\\align center right");
								printData.add("\t\t\\text " + String.format("%,d", Math.abs(accountTitleTotal)));
								
								currentRow++;
								restOfRows--;
							}
						}

						//仕訳帳に記載する総勘定元帳ページ(元丁)を設定します。
						if(account.getLedgerPageNumber() <= 0) {
							account.setLedgerPageNumber(pageNumber);
						}
						//counterpartAccount.setLedgerPageNumber(pageNumber);
						
						
						//日付
						printData.add("\t\t\\box " + String.format("0 %.2f -0 %.2f", currentRow * ROW_HEIGHT, ROW_HEIGHT));
						printData.add("\t\t\\font serif 10");
						printData.add("\t\t\t\\box " + String.format("0 0 6 %.2f", ROW_HEIGHT));
						printData.add("\t\t\t\\align center right");
						printData.add("\t\t\t\\text " + month);
						printData.add("\t\t\t\\box " + String.format("8 0 6.2 %.2f", ROW_HEIGHT));
						printData.add("\t\t\t\\align center right");
						printData.add("\t\t\t\\text " + day);
						
						//摘要
						printData.add("\t\t\\box " + String.format("17.5 %.2f 49.5 %.2f", currentRow * ROW_HEIGHT, ROW_HEIGHT));
						printData.add("\t\t\\font serif 9");
						printData.add("\t\t\\align center left");
						if(entry.isOpening() && entry.getDescription().equals("前期繰越")) {
							//開始仕訳の摘要が「前期繰越」となっている場合は相手勘定科目ではなく摘要を印字します。
							printData.add("\t\t\\text " + entry.getDescription());
						} else {
							printData.add("\t\t\\text " + counterpartAccount.getAccountTitle().getDisplayName());
							//摘要欄に勘定科目だけではなく仕訳摘要も印字します。ただし、締切仕訳や仕訳摘要と勘定科目が同じ場合は印字しません。
							if(!entry.isClosing()
									&& !entry.getDescription().equals(account.getAccountTitle().getDisplayName())
									&& !entry.getDescription().equals(counterpartAccount.getAccountTitle().getDisplayName())) {
								printData.add("\t\t\\font serif 6");
								printData.add("\t\t\\text  / " + entry.getDescription());
							}
						}
						
						//仕丁
						if(account.getJournalPageNumber() >= 1) {
							printData.add("\t\t\\box " + String.format("67 %.2f 8 %.2f", currentRow * ROW_HEIGHT, ROW_HEIGHT));
							printData.add("\t\t\\font serif 10");
							printData.add("\t\t\\align center");
							printData.add("\t\t\\text " + account.getJournalPageNumber());
						}
						
						//借方
						if(account instanceof Debtor) {
							printData.add("\t\t\\box " + String.format("75 %.2f 25 %.2f", currentRow * ROW_HEIGHT, ROW_HEIGHT));
							printData.add("\t\t\\font serif 10");
							printData.add("\t\t\\align center right");
							printData.add("\t\t\\text " + String.format("%,d", counterpartAccount.getAmount()));
							//資産、費用の場合は増加、負債、純資産、収益の場合は減少
							if(account.getAccountTitle().getType().getNormalBalance() == Debtor.class) {
								accountTitleTotal += counterpartAccount.getAmount();
							} else {
								accountTitleTotal -= counterpartAccount.getAmount();
							}
							debtorTotal += counterpartAccount.getAmount();
						}
						
						//貸方
						if(account instanceof Creditor) {
							printData.add("\t\t\\box " + String.format("105 %.2f 25 %.2f", currentRow * ROW_HEIGHT, ROW_HEIGHT));
							printData.add("\t\t\\font serif 10");
							printData.add("\t\t\\align center right");
							printData.add("\t\t\\text " + String.format("%,d", counterpartAccount.getAmount()));
							//負債、純資産、収益の場合は増加、資産、費用の場合は減少
							if(account.getAccountTitle().getType().getNormalBalance() == Creditor.class) {
								accountTitleTotal += counterpartAccount.getAmount();
							} else {
								accountTitleTotal -= counterpartAccount.getAmount();
							}
							creditorTotal += counterpartAccount.getAmount();
						}
						
						//借または貸
						AccountType type = account.getAccountTitle().getType();
						if(type.getNormalBalance() == Debtor.class) {
							sign = (accountTitleTotal >= 0) ? "借" : "貸";
						}
						if(type.getNormalBalance() == Creditor.class) {
							sign = (accountTitleTotal >= 0) ? "貸" : "借";
						}
						printData.add("\t\t\\box " + String.format("135 %.2f 8 %.2f", currentRow * ROW_HEIGHT, ROW_HEIGHT));
						printData.add("\t\t\\font serif 10");
						printData.add("\t\t\\align center");
						printData.add("\t\t\\text " + sign);
						
						//残高
						printData.add("\t\t\\box " + String.format("143 %.2f 27 %.2f", currentRow * ROW_HEIGHT, ROW_HEIGHT));
						printData.add("\t\t\\font serif 10");
						printData.add("\t\t\\align center right");
						printData.add("\t\t\\text " + String.format("%,d", Math.abs(accountTitleTotal)));

						if(isCarriedForward) {
							printData.add("\t\t\\box " + String.format("16 %.2f 49 %.2f", (currentRow + 1) * ROW_HEIGHT, ROW_HEIGHT));
							printData.add("\t\t\\font serif 10");
							printData.add("\t\t\\align center right");
							printData.add("\t\t\\text 次頁繰越");
							//仮または貸
							printData.add("\t\t\\box " + String.format("135 %.2f 8 %.2f", (currentRow + 1) * ROW_HEIGHT, ROW_HEIGHT));
							printData.add("\t\t\\font serif 10");
							printData.add("\t\t\\align center");
							printData.add("\t\t\\text " + sign);
							//残高
							printData.add("\t\t\\box " + String.format("143 %.2f 27 %.2f", (currentRow + 1) * ROW_HEIGHT, ROW_HEIGHT));
							printData.add("\t\t\\font serif 10");
							printData.add("\t\t\\align center right");
							printData.add("\t\t\\text " + String.format("%,d", Math.abs(accountTitleTotal)));
						}
						
						currentRow += rowsRequired;
						restOfRows -= rowsRequired;
					}
				}
			}
			
			//期末締切線
			if(entries.size() > 0 && entries.get(entries.size() - 1).isClosing()) {
				printData.add("\t\t\\box " + String.format("0 %.2f -0 %.2f", currentRow * ROW_HEIGHT, ROW_HEIGHT + 0.4));
				printData.add("\t\t\\line-style medium solid");
				//合計線
				printData.add("\t\t\\line 75.2 0 134.8 0");
				//締切線
				printData.add("\t\t\\line 0 -0.4 15.8 -0.4");
				printData.add("\t\t\\line 0 -0.0 15.8 -0.0");
				printData.add("\t\t\\line 75.2 -0.4 134.8 -0.4");
				printData.add("\t\t\\line 75.2 -0.0 134.8 -0.0");
				//借方合計
				printData.add("\t\t\\box " + String.format("75 %.2f 25 %.2f", currentRow * ROW_HEIGHT, ROW_HEIGHT));
				printData.add("\t\t\\font serif 10");
				printData.add("\t\t\\align center right");
				printData.add("\t\t\\text " + String.format("%,d", debtorTotal));
				//貸方合計
				printData.add("\t\t\\box " + String.format("105 %.2f 25 %.2f", currentRow * ROW_HEIGHT, ROW_HEIGHT));
				printData.add("\t\t\\font serif 10");
				printData.add("\t\t\\align center right");
				printData.add("\t\t\\text " + String.format("%,d", creditorTotal));
			}
		}
	}
	
	
	/** 指定した仕訳と勘定科目から勘定リストを取得します。
	 * 
	 * @param entry 仕訳
	 * @param accountTitle 勘定科目
	 * @return 勘定リスト
	 */
	public List<Account> getAccountsByAccountTitle(JournalEntry entry, AccountTitle accountTitle) {
		List<Account> accounts = new ArrayList<Account>();
		for(int k = 0; k < entry.getDebtors().size(); k++) {
			Debtor debtor = entry.getDebtors().get(k);
			if(debtor.getAccountTitle().equals(accountTitle)) {
				accounts.add(debtor);
			}
		}
		for(int k = 0; k < entry.getCreditors().size(); k++) {
			Creditor creditor = entry.getCreditors().get(k);
			if(creditor.getAccountTitle().equals(accountTitle)) {
				accounts.add(creditor);
			}
		}
		return accounts;
	}

	
	/** 指定した仕訳と勘定から相手勘定リストを取得します。
	 * 
	 * @param entry 仕訳
	 * @param account 勘定
	 * @return 相手勘定リスト
	 */
	public List<Account> getCounterpartAccounts(JournalEntry entry, Account account) {
		List<Account> counterpartAccounts = new ArrayList<Account>(); //相手勘定科目

		if(account.getAccountTitle().getDisplayName().equals("元入金")) {
			if(account instanceof Debtor) {
				if(entry.getCreditors().size() == 1) {
					counterpartAccounts.add(new Creditor(entry.getCreditors().get(0).getAccountTitle(), account.getAmount()));
				} else {
					counterpartAccounts.addAll(entry.getCreditors());
				}
			} else if(account instanceof Creditor) {
				if(entry.getDebtors().size() == 1) {
					counterpartAccounts.add(new Debtor(entry.getDebtors().get(0).getAccountTitle(), account.getAmount()));
				} else {
					counterpartAccounts.addAll(entry.getDebtors());
				}
			}
		} else if(account.getAccountTitle().isClosing()) {
			//決算勘定の場合は相手勘定科目を諸口としてまとめずにすべて出力します。
			if(account instanceof Debtor) {
				if(entry.getCreditors().size() == 1) {
					counterpartAccounts.add(new Creditor(entry.getCreditors().get(0).getAccountTitle(), account.getAmount()));
				} else {
					counterpartAccounts.addAll(entry.getCreditors());
				}
			} else if(account instanceof Creditor) {
				if(entry.getDebtors().size() == 1) {
					counterpartAccounts.add(new Debtor(entry.getDebtors().get(0).getAccountTitle(), account.getAmount()));
				} else {
					counterpartAccounts.addAll(entry.getDebtors());
				}
			}
		} else {
			//決算勘定でない場合は相手勘定科目が複数ある場合は諸口としてまとめます。
			if(account instanceof Debtor) {
				if(entry.getCreditors().size() == 1) {
					counterpartAccounts.add(new Creditor(entry.getCreditors().get(0).getAccountTitle(), account.getAmount()));
				} else {
					counterpartAccounts.add(new Creditor(AccountTitle.SUNDRIES, account.getAmount()));
				}
			} else if(account instanceof Creditor) {
				if(entry.getDebtors().size() == 1) {
					counterpartAccounts.add(new Debtor(entry.getDebtors().get(0).getAccountTitle(), account.getAmount()));
				} else {
					counterpartAccounts.add(new Debtor(AccountTitle.SUNDRIES, account.getAmount()));
				}
			}
		}
		return counterpartAccounts;
	}
	
	
	public void writeTo(File file) throws IOException {
		prepare();

		PdfBrewer brewer = new PdfBrewer();
		BrewerData pb = new BrewerData(printData, brewer.getFontLoader());
		brewer.setTitle("総勘定元帳");
		brewer.process(pb);
		brewer.save(file);
	}
	
	/** 指定した勘定科目を含む仕訳データを取得します。
	 * 
	 * @param accountTitle 勘定科目
	 * @return 指定した勘定科目を含む仕訳データのリスト
	 */
	protected List<JournalEntry> getJournalEntriesByAccount(AccountTitle accountTitle) {
		List<JournalEntry> entriesByAccount = new ArrayList<JournalEntry>();
		if(accountTitle != null) {
			for(JournalEntry entry : this.entries) {
				for(Debtor debtor : entry.getDebtors()) {
					if(accountTitle.equals(debtor.getAccountTitle())) {
						if(!entriesByAccount.contains(entry)) {
							entriesByAccount.add(entry);
						}
					}
				}
				for(Creditor creditor : entry.getCreditors()) {
					if(accountTitle.equals(creditor.getAccountTitle())) {
						if(!entriesByAccount.contains(entry)) {
							entriesByAccount.add(entry);
						}
					}
				}
			}
		}
		return entriesByAccount;
	}
}
