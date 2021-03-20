package net.osdn.aoiro.report;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.osdn.aoiro.AccountSettlement;
import net.osdn.aoiro.Util;
import net.osdn.aoiro.model.Account;
import net.osdn.aoiro.model.AccountTitle;
import net.osdn.aoiro.model.AccountType;
import net.osdn.aoiro.model.Creditor;
import net.osdn.aoiro.model.Debtor;
import net.osdn.aoiro.model.JournalEntry;
import net.osdn.pdf_brewer.BrewerData;
import net.osdn.pdf_brewer.FontLoader;
import net.osdn.pdf_brewer.PdfBrewer;

/** 総勘定元帳
 * 
 */
public class GeneralLedger {

	private static final int ROWS = 50;
	private static final double ROW_HEIGHT = 5.0;
	
	private Set<AccountTitle> accountTitles;
	private List<JournalEntry> entries;
	private boolean isSoloProprietorship;
	private boolean showMonthlyTotal;

	private LocalDate openingDate;
	int financialYear;
	boolean isFromNewYearsDay;
	
	private List<String> pageData = new ArrayList<>();
	private List<String> printData;
	private FontLoader fontLoader;
	private boolean bindingMarginEnabled = true;
	private boolean pageNumberEnabled = true;

	public GeneralLedger(Set<AccountTitle> accountTitles, List<JournalEntry> journalEntries, boolean isSoloProprietorship, boolean showMonthlyTotal) throws IOException {
		this.accountTitles = new LinkedHashSet<>(accountTitles);

		// ビルトインの勘定科目（損益・控除前の所得金額・繰越利益剰余金・残高）を総勘定元帳のページ後半にまとめるために
		// ビルトインの勘定科目は一度、取り除いて(remove)、追加(add) することで LinkedHashSet の後に再配置します。
		this.accountTitles.remove(AccountTitle.INCOME_SUMMARY);
		this.accountTitles.add(AccountTitle.INCOME_SUMMARY);

		this.accountTitles.remove(AccountTitle.RETAINED_EARNINGS);
		this.accountTitles.add(AccountTitle.RETAINED_EARNINGS);

		this.accountTitles.remove(AccountTitle.PRETAX_INCOME);
		this.accountTitles.add(AccountTitle.PRETAX_INCOME);

		this.accountTitles.remove(AccountTitle.BALANCE);
		this.accountTitles.add(AccountTitle.BALANCE);


		this.entries = journalEntries;
		this.isSoloProprietorship = isSoloProprietorship;
		this.showMonthlyTotal = showMonthlyTotal;

		this.openingDate = AccountSettlement.getOpeningDate(journalEntries, isSoloProprietorship);
		
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

	public List<JournalEntry> getJournalEntries() {
		return entries;
	}
	
	protected void prepare() {
		int pageNumber = 0;
		AccountTitle currentAccountTitle = null;
		
		printData = new ArrayList<String>();
		if(bindingMarginEnabled) {
			printData.add("\\media A4");

			//穴あけパンチの位置を合わせるための中心線を先頭ページのみ印字します。
			printData.add("\\line-style thin dot");
			printData.add("\\line 0 148.5 5 148.5");
		} else {
			// 綴じ代なしの場合は15mm分だけ横幅を短くして195mmとします。(A4本来の横幅は210mm)
			// 穴あけパンチ用の中心線も出力しません。
			printData.add("\\media 195 297");
		}

		for(AccountTitle accountTitle : accountTitles) {
			int restOfRows = 0;
			int currentRow = 0;
			String sign = "";
			long debtorTotal = 0;
			long creditorTotal = 0;
			long accountTitleTotal = 0;
			long monthlyDebtorTotal = 0;
			long monthlyCreditorTotal = 0;

			List<JournalEntry> entries = getJournalEntriesByAccount(accountTitle);
			for(int j = 0; j < entries.size(); j++) {
				JournalEntry entry = entries.get(j);
				int month = entry.getDate().getMonthValue();
				int day = entry.getDate().getDayOfMonth();

				int monthlyTotalMonth = -1;
				boolean isLastEntryInMonth = false;
				if(showMonthlyTotal) {
					// 開始仕訳は1月計に含めないようにするために0月として扱います。
					monthlyTotalMonth = entry.isOpening(isSoloProprietorship, openingDate) ? 0 : month;
					if(!entry.isClosing()) {
						if(j + 1 == entries.size()) {
							isLastEntryInMonth = true;
						} else if(j + 1 < entries.size()) {
							JournalEntry nextEntry = entries.get(j + 1);
							int nextEntryMonth = nextEntry.isOpening(isSoloProprietorship, openingDate) ? 0 : nextEntry.getDate().getMonthValue();
							if(monthlyTotalMonth != nextEntryMonth) {
								isLastEntryInMonth = true;
							} else if(nextEntry.isClosing()) {
								isLastEntryInMonth = true;
							}
						}
					}
				}

				//この勘定科目を含む勘定のリストを取得します。
				List<Account> accounts = getAccountsByAccountTitle(entry, accountTitle);
				
				for(int k = 0; k < accounts.size(); k++) {
					Account account = accounts.get(k);
					//この勘定を含む相手勘定のリストを取得します。
					List<Account> counterpartAccounts = getCounterpartAccounts(entry, account);
					for(int l = 0; l < counterpartAccounts.size(); l++) {
						Account counterpartAccount = counterpartAccounts.get(l);

						//月計に加算します。
						if(showMonthlyTotal) {
							if(account instanceof Debtor) {
								monthlyDebtorTotal += counterpartAccount.getAmount();
							}
							if(account instanceof Creditor) {
								monthlyCreditorTotal += counterpartAccount.getAmount();
							}
						}

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
							newPage(++pageNumber, account.getAccountTitle().getDisplayName());
							restOfRows = ROWS;
							currentRow = 0;
							currentAccountTitle = account.getAccountTitle();
							
							//勘定科目が変わった場合を除いて改ページが発生した場合、前頁繰越を印字します。
							if(j != 0 || k != 0 || l != 0) {
								carryForwardFromPreviousPage(currentRow, sign, accountTitleTotal);
								currentRow++;
								restOfRows--;
							}
						}

						//仕訳帳に記載する総勘定元帳ページ(元丁)を設定します。
						if(account.getLedgerPageNumber() <= 0) {
							account.setLedgerPageNumber(pageNumber);
						}

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
						if(!isSoloProprietorship && entry.isOpening(isSoloProprietorship, openingDate)) {
							//法人かつ開始仕訳の場合、摘要のみを印字します。
							printData.add("\t\t\\text " + entry.getDescription());
						} else {
							printData.add("\t\t\\text " + counterpartAccount.getAccountTitle().getDisplayName());
							//摘要欄に勘定科目だけではなく仕訳摘要も印字します。ただし、締切仕訳や仕訳摘要と勘定科目が同じ場合は印字しません。
							if(!entry.isClosing()
									&& !entry.getDescription().isBlank()
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
							//資産、費用の場合は増加、負債、資本（純資産）、収益の場合は減少
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
							//負債、資本（純資産）、収益の場合は増加、資産、費用の場合は減少
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
							carryForwardToNextPage(currentRow + 1, sign, accountTitleTotal);
						}

						currentRow += rowsRequired;
						restOfRows -= rowsRequired;

						//月計印字有効 + 月末最後の仕訳 + 相手勘定科目の末尾 のときに月計を印字します。
						if(showMonthlyTotal && isLastEntryInMonth && (l + 1) == counterpartAccounts.size()) {
							int emptyRows = (ROWS - 1) - currentRow - (isLastInAccountTitle ? 0 : 1);
							if(emptyRows < 0) {
								emptyRows = 0;
							}
							if(emptyRows > 2 || isCarriedForward) {
								//月計の前の仕訳で次頁繰越(isCarriedForward)が発生していた場合は紙面に余裕があるので空白行を2行にします。
								emptyRows = 2;
							}
							rowsRequired = 1 + emptyRows;

							if(!isLastInAccountTitle && (currentRow + emptyRows + 1 == ROWS - 1)) {
								rowsRequired++;
								isCarriedForward = true;
							} else {
								isCarriedForward = false;
							}

							//印字に必要な行数が残り行数を超えているときに改ページします。
							if(rowsRequired > restOfRows) {
								newPage(++pageNumber, account.getAccountTitle().getDisplayName());
								restOfRows = ROWS;
								currentRow = 0;

								//勘定科目が変わった場合を除いて改ページが発生した場合、前頁繰越を印字します。
								if(j != 0 || k != 0 || l != 0) {
									carryForwardFromPreviousPage(currentRow, sign, accountTitleTotal);
									currentRow++;
									restOfRows--;
								}
							}

							printData.add("\t\t\\box " + String.format("16 %.2f 49 %.2f", currentRow * ROW_HEIGHT, ROW_HEIGHT));
							printData.add("\t\t\\font serif 10 bold");
							printData.add("\t\t\\align center right");
							if(monthlyTotalMonth == 0) {
								printData.add("\t\t\\text 前期繰越計");
							} else {
								printData.add("\t\t\\text " + month + "月計");
							}

							printData.add("\t\t\\box " + String.format("0 %.2f -0 %.2f", currentRow * ROW_HEIGHT, ROW_HEIGHT + 0.5));
							printData.add("\t\t\\line-style medium solid");
							//合計線
							printData.add("\t\t\\line 75.325 0 134.675 0");
							//借方合計
							printData.add("\t\t\\box " + String.format("75 %.2f 25 %.2f", currentRow * ROW_HEIGHT, ROW_HEIGHT));
							printData.add("\t\t\\font serif 10.5 bold");
							printData.add("\t\t\\align center right");
							printData.add("\t\t\\text " + String.format("%,d", monthlyDebtorTotal));
							//貸方合計
							printData.add("\t\t\\box " + String.format("105 %.2f 25 %.2f", currentRow * ROW_HEIGHT, ROW_HEIGHT));
							printData.add("\t\t\\font serif 10.5 bold");
							printData.add("\t\t\\align center right");
							printData.add("\t\t\\text " + String.format("%,d", monthlyCreditorTotal));

							monthlyDebtorTotal = 0;
							monthlyCreditorTotal = 0;

							if(isCarriedForward) {
								carryForwardToNextPage(currentRow + emptyRows + 1, sign, accountTitleTotal);
							}

							currentRow += rowsRequired;
							restOfRows -= rowsRequired;
						}

					}
				}
			}
			
			//期末締切線
			if(entries.size() > 0 && entries.get(entries.size() - 1).isClosing()) {
				printData.add("\t\t\\box " + String.format("0 %.2f -0 %.2f", currentRow * ROW_HEIGHT, ROW_HEIGHT + 0.5));
				printData.add("\t\t\\line-style medium solid");
				//合計線
				printData.add("\t\t\\line 75.325 0 134.675 0");
				//締切線
				printData.add("\t\t\\line 0.125 -0.5 15.675 -0.5");
				printData.add("\t\t\\line 0.125 -0.0 15.675 -0.0");
				printData.add("\t\t\\line 75.325 -0.5 134.675 -0.5");
				printData.add("\t\t\\line 75.325 -0.0 134.675 -0.0");
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

	private void newPage(int pageNumber, String accountTitleDisplayName) {
		if(pageNumber >= 2) {
			printData.add("\\new-page");
		}
		if(!bindingMarginEnabled) {
			//綴じ代なし
			printData.add("\\box 10 0 -10 -10");

			//テンプレート
			printData.addAll(pageData);

			if(pageNumberEnabled) {
				if(pageNumber % 2 == 1) {
					//ページ番号(奇数ページ)
					printData.add("\t\\box 0 0 -3 22");
					printData.add("\t\\font serif 10.5");
					printData.add("\t\\align bottom right");
					printData.add("\t\\text " + pageNumber);
				} else {
					//ページ番号(偶数ページ)
					printData.add("\t\\box 3 0 10 22");
					printData.add("\t\\font serif 10.5");
					printData.add("\t\\align bottom left");
					printData.add("\t\\text " + pageNumber);
				}
			}
		} else if(pageNumber % 2 == 1) {
			//綴じ代(奇数ページ)
			printData.add("\\box 15 0 0 0");
			printData.add("\\line-style thin dot");
			printData.add("\\line 0 0 0 -0");
			printData.add("\\box 25 0 -10 -10");

			//テンプレート
			printData.addAll(pageData);

			if(pageNumberEnabled) {
				//ページ番号(奇数ページ)
				printData.add("\t\\box 0 0 -3 22");
				printData.add("\t\\font serif 10.5");
				printData.add("\t\\align bottom right");
				printData.add("\t\\text " + pageNumber);
			}
		} else {
			//綴じ代(偶数ページ)
			printData.add("\\box 0 0 -15 0");
			printData.add("\\line-style thin dot");
			printData.add("\\line -0 0 -0 -0");
			printData.add("\\box 10 0 -25 -10");

			//テンプレート
			printData.addAll(pageData);

			if(pageNumberEnabled) {
				//ページ番号(偶数ページ)
				printData.add("\t\\box 3 0 10 22");
				printData.add("\t\\font serif 10.5");
				printData.add("\t\\align bottom left");
				printData.add("\t\\text " + pageNumber);
			}
		}
		//勘定科目
		printData.add("\t\\box 0 16 -0 9");
		printData.add("\t\\font serif 14");
		printData.add("\t\\align center");
		printData.add("\t\\text " + accountTitleDisplayName);

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
	}

	private void carryForwardFromPreviousPage(int currentRow, String sign, long accountTitleTotal) {
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
	}

	private void carryForwardToNextPage(int row, String sign, long accountTitleTotal) {
		printData.add("\t\t\\box " + String.format("16 %.2f 49 %.2f", row * ROW_HEIGHT, ROW_HEIGHT));
		printData.add("\t\t\\font serif 10");
		printData.add("\t\t\\align center right");
		printData.add("\t\t\\text 次頁繰越");
		//仮または貸
		printData.add("\t\t\\box " + String.format("135 %.2f 8 %.2f", row * ROW_HEIGHT, ROW_HEIGHT));
		printData.add("\t\t\\font serif 10");
		printData.add("\t\t\\align center");
		printData.add("\t\t\\text " + sign);
		//残高
		printData.add("\t\t\\box " + String.format("143 %.2f 27 %.2f", row * ROW_HEIGHT, ROW_HEIGHT));
		printData.add("\t\t\\font serif 10");
		printData.add("\t\t\\align center right");
		printData.add("\t\t\\text " + String.format("%,d", Math.abs(accountTitleTotal)));
	}

	/** 指定した仕訳と勘定科目から勘定リストを取得します。
	 * 
	 * @param entry 仕訳
	 * @param accountTitle 勘定科目
	 * @return 勘定リスト
	 */
	public List<Account> getAccountsByAccountTitle(JournalEntry entry, AccountTitle accountTitle) {
		List<Account> accounts = new ArrayList<>();
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
		List<Account> counterpartAccounts = new ArrayList<>(); //相手勘定科目

		// 相手勘定が1件で決算勘定の場合は必ず相手勘定科目を出力します。
		boolean isCounterpartClosingAccount = false;
		if(account instanceof Debtor) {
			if(entry.getCreditors().size() == 1 && entry.getCreditors().get(0).getAccountTitle().isClosing()) {
				isCounterpartClosingAccount = true;
			}
		} else if(account instanceof Creditor) {
			if(entry.getDebtors().size() == 1 && entry.getDebtors().get(0).getAccountTitle().isClosing()) {
				isCounterpartClosingAccount = true;
			}
		}
		if(isCounterpartClosingAccount) {
			if(account instanceof Debtor) {
				counterpartAccounts.add(new Creditor(entry.getCreditors().get(0).getAccountTitle(), account.getAmount()));
			} else if(account instanceof Creditor) {
				counterpartAccounts.add(new Debtor(entry.getDebtors().get(0).getAccountTitle(), account.getAmount()));
			}
		} else if(account.getAccountTitle().getDisplayName().equals("元入金")) {
			// 元入金の場合は相手勘定科目を「諸口」とせずに、相手勘定科目を個別に出力します。
			// ただし、元入金側の勘定科目が2件以上ある場合は金額を算出できないので「諸口」とします。
			// 元入金側の勘定科目が1件の場合は、元入金の金額と相手勘定科目の合計金額が一致しますが、
			// 元入金側の勘定科目が2件以上ある場合は元入金の金額と相手勘定科目の合計金額は一致しないためです。
			if(account instanceof Debtor) {
				if(entry.getDebtors().size() == 1) {
					counterpartAccounts.addAll(entry.getCreditors());
				} else {
					counterpartAccounts.add(new Creditor(AccountTitle.SUNDRIES, account.getAmount()));
				}
			} else if(account instanceof Creditor) {
				if(entry.getCreditors().size() == 1) {
					counterpartAccounts.addAll(entry.getDebtors());
				} else {
					counterpartAccounts.add(new Debtor(AccountTitle.SUNDRIES, account.getAmount()));
				}
			}
		} else if(account.getAccountTitle().isClosing()) {
			//決算勘定の場合は相手勘定科目を諸口としてまとめずにすべて出力します。
			//ただし、決算勘定側の勘定科目が2件以上ある場合は相手勘定科目を「諸口」とします。
			//理由は元入金と同様に金額の不一致を避けるためです。
			if(account instanceof Debtor) {
				if(entry.getDebtors().size() == 1) { //自勘定が1件
					counterpartAccounts.addAll(entry.getCreditors());
				} else {
					counterpartAccounts.add(new Creditor(AccountTitle.SUNDRIES, account.getAmount()));
				}
			} else if(account instanceof Creditor) {
				if(entry.getCreditors().size() == 1) { //自勘定が1件
					counterpartAccounts.addAll(entry.getDebtors());
				} else {
					counterpartAccounts.add(new Debtor(AccountTitle.SUNDRIES, account.getAmount()));
				}
			}
		} else {
			//決算勘定でない場合は相手勘定科目が複数ある場合は諸口としてまとめます。
			if(account instanceof Debtor) {
				if(entry.getCreditors().size() == 1) { //相手勘定が1件
					counterpartAccounts.add(new Creditor(entry.getCreditors().get(0).getAccountTitle(), account.getAmount()));
				} else {
					counterpartAccounts.add(new Creditor(AccountTitle.SUNDRIES, account.getAmount()));
				}
			} else if(account instanceof Creditor) {
				if(entry.getDebtors().size() == 1) { //相手勘定が1件
					counterpartAccounts.add(new Debtor(entry.getDebtors().get(0).getAccountTitle(), account.getAmount()));
				} else {
					counterpartAccounts.add(new Debtor(AccountTitle.SUNDRIES, account.getAmount()));
				}
			}
		}
		return counterpartAccounts;
	}

	public void setFontLoader(FontLoader fontLoader) {
		this.fontLoader = fontLoader;
	}

	public void setBindingMarginEnabled(boolean enabled) {
		this.bindingMarginEnabled = enabled;
	}

	public void setPageNumberEnabled(boolean enabled) {
		this.pageNumberEnabled = enabled;
	}

	public void writeTo(Path path) throws IOException {
		prepare();

		PdfBrewer brewer;
		if(fontLoader != null) {
			brewer = new PdfBrewer(fontLoader);
		} else {
			brewer = new PdfBrewer();
		}
		brewer.setCreator(Util.getPdfCreator());
		BrewerData pb = new BrewerData(printData, brewer.getFontLoader());
		brewer.setTitle("総勘定元帳");
		brewer.process(pb);
		brewer.save(path);
		brewer.close();
	}

	public void writeTo(OutputStream out) throws IOException {
		prepare();

		PdfBrewer brewer;
		if(fontLoader != null) {
			brewer = new PdfBrewer(fontLoader);
		} else {
			brewer = new PdfBrewer();
		}
		brewer.setCreator(Util.getPdfCreator());
		BrewerData pb = new BrewerData(printData, brewer.getFontLoader());
		brewer.setTitle("総勘定元帳");
		brewer.process(pb);
		brewer.save(out);
		brewer.close();
	}

	/** 指定した勘定科目を含む仕訳データを取得します。
	 * 
	 * @param accountTitle 勘定科目
	 * @return 指定した勘定科目を含む仕訳データのリスト
	 */
	protected List<JournalEntry> getJournalEntriesByAccount(AccountTitle accountTitle) {
		List<JournalEntry> entriesByAccount = new ArrayList<JournalEntry>();
		if(accountTitle != null) {
			NEXT_JOURNAL_ENTRY:
			for(JournalEntry entry : this.entries) {
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
