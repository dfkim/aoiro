package net.osdn.aoiro.report;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.chrono.JapaneseChronology;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.osdn.aoiro.AccountSettlement;
import net.osdn.aoiro.Util;
import net.osdn.aoiro.cui.Main;
import net.osdn.aoiro.model.AccountTitle;
import net.osdn.aoiro.model.AccountType;
import net.osdn.aoiro.model.Amount;
import net.osdn.aoiro.model.Creditor;
import net.osdn.aoiro.model.Debtor;
import net.osdn.aoiro.model.JournalEntry;
import net.osdn.aoiro.model.Node;
import net.osdn.aoiro.report.layout.BalanceSheetLayout;
import net.osdn.pdf_brewer.BrewerData;
import net.osdn.pdf_brewer.PdfBrewer;

/** 貸借対照表
 * 
 */
public class BalanceSheet {

	public static String MINUS_SIGN = "△";
	private static final int ROWS = 40;
	private static final double ROW_HEIGHT = 6.0;
	
	private BalanceSheetLayout bsLayout;
	private List<JournalEntry> journalEntries;
	private boolean isSoloProprietorship;

	private LocalDate openingDate;
	private LocalDate closingDate;
	private Map<AccountTitle, Amount> openingBalances = new HashMap<AccountTitle, Amount>();
	private Map<AccountTitle, Amount> closingBalances = new HashMap<AccountTitle, Amount>();
	private List<Node<Entry<List<AccountTitle>, Amount[]>>> assetsList;
	private List<Node<Entry<List<AccountTitle>, Amount[]>>> liabilitiesList;
	private List<Node<Entry<List<AccountTitle>, Amount[]>>> equityList;
	
	private List<String> pageData = new ArrayList<String>();
	private List<String> printData;

	private List<String> warnings = new ArrayList<String>();

	public BalanceSheet(BalanceSheetLayout bsLayout, List<JournalEntry> journalEntries, boolean isSoloProprietorship) throws IOException {
		this.bsLayout = bsLayout;
		this.journalEntries = journalEntries;
		this.isSoloProprietorship = isSoloProprietorship;

		this.openingDate = AccountSettlement.getOpeningDate(journalEntries, isSoloProprietorship);
		this.closingDate = AccountSettlement.getClosingDate(journalEntries, isSoloProprietorship);
		
		InputStream in = getClass().getResourceAsStream("/templates/貸借対照表.pb");
		BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		String line;
		while((line = r.readLine()) != null) {
			pageData.add(line);
		}
		r.close();
		
		//期首残高(元入金)と期末残高の算出
		for(JournalEntry entry : journalEntries) {
			//期首残高(元入金)
			if(entry.isOpening()) {
				for(Debtor debtor : entry.getDebtors()) {
					Amount amount = openingBalances.get(debtor.getAccountTitle());
					if(amount == null) {
						amount = new Amount(debtor.getAccountTitle().getType().getNormalBalance(), 0);
						openingBalances.put(debtor.getAccountTitle(), amount);
					}
					if(debtor.getAccountTitle().getType().getNormalBalance() == Debtor.class) {
						amount.increase(debtor.getAmount());
					} else {
						amount.decrease(debtor.getAmount());
					}
				}
				for(Creditor creditor : entry.getCreditors()) {
					Amount amount = openingBalances.get(creditor.getAccountTitle());
					if(amount == null) {
						amount = new Amount(creditor.getAccountTitle().getType().getNormalBalance(), 0);
						openingBalances.put(creditor.getAccountTitle(), amount);
					}
					if(creditor.getAccountTitle().getType().getNormalBalance() == Creditor.class) {
						amount.increase(creditor.getAmount());
					} else {
						amount.decrease(creditor.getAmount());
					}
				}
			}
			//期末残高
			if(entry.isBalance()) {
				for(Debtor debtor : entry.getDebtors()) {
					if(!debtor.getAccountTitle().equals(AccountTitle.BALANCE)) {
						Amount amount = closingBalances.get(debtor.getAccountTitle());
						if(amount == null) {
							amount = new Amount(debtor.getAccountTitle().getType().getNormalBalance(), 0);
							closingBalances.put(debtor.getAccountTitle(), amount);
						}
						if(debtor.getAccountTitle().getType().getNormalBalance() != Debtor.class) {
							amount.increase(debtor.getAmount());
						} else {
							amount.decrease(debtor.getAmount());
						}
					}
				}
				for(Creditor creditor : entry.getCreditors()) {
					if(!creditor.getAccountTitle().equals(AccountTitle.BALANCE)) {
						Amount amount = closingBalances.get(creditor.getAccountTitle());
						if(amount == null) {
							amount = new Amount(creditor.getAccountTitle().getType().getNormalBalance(), 0);
							closingBalances.put(creditor.getAccountTitle(), amount);
						}
						if(creditor.getAccountTitle().getType().getNormalBalance() != Creditor.class) {
							amount.increase(creditor.getAmount());
						} else {
							amount.decrease(creditor.getAmount());
						}
					}
				}
			}
		}
		
		/*
		for(Entry<AccountTitle, Amount> e : openingBalances.entrySet()) {
			AccountTitle at = e.getKey();
			Amount a = e.getValue();
			System.out.println("#OPEN# " + at + ", " + a);
			
		}
		*/
		/*
		for(Entry<AccountTitle, Amount> e : closingBalances.entrySet()) {
			AccountTitle at = e.getKey();
			Amount a = e.getValue();
			System.out.println("#CLOSE# " + at + ", " + a);
		}
		*/
		
		//再帰集計
		retrieve(bsLayout.getRoot(), journalEntries);
		//dump(bsRoot);
		
		//リスト
		for(Node<Entry<List<AccountTitle>, Amount[]>> child : bsLayout.getRoot().getChildren()) {
			if(child.getName().equals("資産")) {
				assetsList = getList(child);
			} else if(child.getName().equals("負債")) {
				liabilitiesList = getList(child);
			} else if(child.getName().equals("資本") || child.getName().equals("純資産")) {
				equityList = getList(child);
			}
		}
		if(assetsList == null) {
			assetsList = new ArrayList<Node<Entry<List<AccountTitle>, Amount[]>>>();
		}
		if(liabilitiesList == null) {
			liabilitiesList = new ArrayList<Node<Entry<List<AccountTitle>, Amount[]>>>();
		}
		if(equityList == null) {
			equityList = new ArrayList<Node<Entry<List<AccountTitle>, Amount[]>>>();
		}

		//
		// 資産・負債の期首残高がマイナスの場合は警告メッセージを表示します。
		// 通常、資産・負債がマイナスになることはありません。資本（純資産）はマイナスになることがあります。
		//
		for(Entry<AccountTitle, Amount> entry : openingBalances.entrySet()) {
			AccountTitle accountTitle = entry.getKey();
			Amount amount = entry.getValue();
			NumberFormat.getNumberInstance();
			if(amount.getValue() < 0
					&& (accountTitle.getType() == AccountType.Assets || accountTitle.getType() == AccountType.Liabilities)) {
				String msg = " [警告] 貸借対照表の「"
						+ accountTitle.getDisplayName()
						+ "」期首残高がマイナスになっています。("
						+ accountTitle.getDisplayName()
						+ " "
						+ NumberFormat.getNumberInstance().format(amount.getValue())
						+ ")";
				warnings.add(msg);
			}
		}
		//
		// 資産・負債の期末残高がマイナスの場合は警告メッセージを表示します。
		// 通常、資産・負債がマイナスになることはありません。資本（純資産）はマイナスになることがあります。
		//
		for(Entry<AccountTitle, Amount> entry : closingBalances.entrySet()) {
			AccountTitle accountTitle = entry.getKey();
			Amount amount = entry.getValue();
			NumberFormat.getNumberInstance();
			if(amount.getValue() < 0
					&& (accountTitle.getType() == AccountType.Assets || accountTitle.getType() == AccountType.Liabilities)) {
				String msg = " [警告] 貸借対照表の「"
						+ accountTitle.getDisplayName()
						+ "」期末残高がマイナスになっています。("
						+ accountTitle.getDisplayName()
						+ " "
						+ NumberFormat.getNumberInstance().format(amount.getValue())
						+ ")";
				warnings.add(msg);
			}
		}
	}

	// PDF出力に使用する資産のリストデータです。
	// 貸借対照表を画面に表示するなどPDF出力とは別の用途で使用するのに役立ちます。
	public List<Node<Entry<List<AccountTitle>, Amount[]>>> getAssetsList() {
		return assetsList;
	}

	// PDF出力に使用する負債のリストデータです。
	// 貸借対照表を画面に表示するなどPDF出力とは別の用途で使用するのに役立ちます。
	public List<Node<Entry<List<AccountTitle>, Amount[]>>> getLiabilitiesList() {
		return liabilitiesList;
	}

	// PDF出力に使用する資本（純資産）のリストデータです。
	// 貸借対照表を画面に表示するなどPDF出力とは別の用途で使用するのに役立ちます。
	public List<Node<Entry<List<AccountTitle>, Amount[]>>> getEquityList() {
		return equityList;
	}

	public List<String> getWarnings() {
		return warnings;
	}

	private Amount[] retrieve(Node<Entry<List<AccountTitle>, Amount[]>> node, List<JournalEntry> journalEntries) {
		Amount openingBalance = null;
		Amount closingBalance = null;
		for(Node<Entry<List<AccountTitle>, Amount[]>> child : node.getChildren()) {
			Amount[] a = retrieve(child, journalEntries);
			if(a[0] != null) {
				if(openingBalance == null) {
					openingBalance = new Amount(a[0].getNormalBalance(), a[0].getValue());
				} else {
					openingBalance.increase(a[0]);
				}
			}
			if(a[1] != null) {
				if(closingBalance == null) {
					closingBalance = new Amount(a[1].getNormalBalance(), a[1].getValue());
				} else {
					closingBalance.increase(a[1]);
				}
			}
		}
		if(node.getValue().getKey() != null) {
			for(AccountTitle accountTitle : node.getValue().getKey()) {
				Amount o = openingBalances.get(accountTitle);
				if(o != null) {
					if(openingBalance == null) {
						openingBalance = new Amount(o.getNormalBalance(), o.getValue());
					} else {
						openingBalance.increase(o);
					}
				}
				Amount c = closingBalances.get(accountTitle);
				if(c != null) {
					if(closingBalance == null) {
						closingBalance = new Amount(c.getNormalBalance(), c.getValue());
					} else {
						closingBalance.increase(c);
					}
				}
			}
		}
		Amount[] amounts = new Amount[2];
		amounts[0] = openingBalance;
		amounts[1] = closingBalance;
		node.getValue().setValue(amounts);
		return amounts;
	}
	
	protected List<Node<Entry<List<AccountTitle>, Amount[]>>> getList(Node<Entry<List<AccountTitle>, Amount[]>> node) {
		List<Node<Entry<List<AccountTitle>, Amount[]>>> list = new ArrayList<Node<Entry<List<AccountTitle>, Amount[]>>>();
		list.add(node);
		for(Node<Entry<List<AccountTitle>, Amount[]>> child : node.getChildren()) {
			list.addAll(getList(child));
		}
		return list;
	}
	
	protected void prepare() {
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("GGGG y 年 M 月 d 日").withChronology(JapaneseChronology.INSTANCE);
		String openingDate = dtf.format(this.openingDate).replace(" 1 年", "元年");
		String openingMonth = Integer.toString(this.openingDate.getMonthValue());
		String openingDay = Integer.toString(this.openingDate.getDayOfMonth());
		String closingDate = dtf.format(this.closingDate).replace(" 1 年", "元年");
		String closingMonth = Integer.toString(this.closingDate.getMonthValue());
		String closingDay = Integer.toString(this.closingDate.getDayOfMonth());

		printData = new ArrayList<String>();
		printData.add("\\media A4");

		//穴あけパンチの位置を合わせるための中心線を先頭ページのみ印字します。
		printData.add("\\line-style thin dot");
		printData.add("\\line 0 148.5 5 148.5");

		printData.add("\\box 15 0 0 0");
		printData.add("\\line-style thin dot");
		printData.add("\\line 0 0 0 -0");
		printData.add("\\box 25 0 -10 -10");
		printData.addAll(pageData);

		//日付
		printData.add("\t\\box 0 16 -0 7");
		printData.add("\t\\font serif 10");
		printData.add("\t\\align center");
		printData.add("\t\\text " + openingDate + " ～ " + closingDate);
		printData.add("\t\\box 0 31 -0 6");
		printData.add("\t\\align center right");
		printData.add("\t\t\\box 34.5 0 6 -0");
		printData.add("\t\t\\text " + openingMonth);
		printData.add("\t\t\\box 42.2 0 6 -0");
		printData.add("\t\t\\text " + openingDay);
		printData.add("\t\t\\box 122.0 0 6 -0");
		printData.add("\t\t\\text " + openingMonth);
		printData.add("\t\t\\box 129.7 0 6 -0");
		printData.add("\t\t\\text " + openingDay);
		printData.add("\t\t\\box 60.5 0 6 -0");
		printData.add("\t\t\\text " + closingMonth);
		printData.add("\t\t\\box 68.2 0 6 -0");
		printData.add("\t\t\\text " + closingDay);
		printData.add("\t\t\\box 148.0 0 6 -0");
		printData.add("\t\t\\text " + closingMonth);
		printData.add("\t\t\\box 155.7 0 6 -0");
		printData.add("\t\t\\text " + closingDay);

		//負債・資本の部のタイトル変更
		//2006年の新会社法で貸借対照表の「資本の部」が「純資産の部」に変更になりました。
		//しかし、国税庁が用意している個人の確定申告用の決算書（貸借対照表）では「資本の部」という表示のままとなっています。
		//国税庁が用意している決算書（貸借対照表）への転記しやすさを考え、
		//aoiroでは会社の場合は「純資産の部」、個人事業主の場合は「資本の部」と表記を切り替えるようにしています。
		printData.add("\t\\box 0 25 -0 6");
		printData.add("\t\t\\font sans-serif 9");
		printData.add("\t\t\\align center");
		printData.add("\t\t\\box 87.5 0 87.5 -0");
		if(isSoloProprietorship) {
			printData.add("\t\t\\text 負　債　・　資　本　の　部");
		} else {
			printData.add("\t\t\\text 負　債　・　純　資　産　の　部");
		}

		// 貸借対照表に出現するもっとも大きな金額を求めます。後の工程で最大金額の桁数に応じて表示位置を調整します。
		long maxAmount = 0;
		if(assetsList.size() > 0) {
			Node<Entry<List<AccountTitle>, Amount[]>> node = assetsList.get(0);
			Amount openingAmount = node.getValue().getValue()[0];
			Amount closingAmount = node.getValue().getValue()[1];
			if(openingAmount != null) {
				long v = Math.abs(openingAmount.getValue());
				if(v > maxAmount) {
					maxAmount = v;
				}
			}
			if(closingAmount != null) {
				long v = Math.abs(closingAmount.getValue());
				if(v > maxAmount) {
					maxAmount = v;
				}
			}
		}
		if(liabilitiesList.size() > 0) {
			Node<Entry<List<AccountTitle>, Amount[]>> node = liabilitiesList.get(0);
			Amount openingAmount = node.getValue().getValue()[0];
			Amount closingAmount = node.getValue().getValue()[1];
			if(openingAmount != null) {
				long v = Math.abs(openingAmount.getValue());
				if(v > maxAmount) {
					maxAmount = v;
				}
			}
			if(closingAmount != null) {
				long v = Math.abs(closingAmount.getValue());
				if(v > maxAmount) {
					maxAmount = v;
				}
			}
		}
		if(equityList.size() > 0) {
			Node<Entry<List<AccountTitle>, Amount[]>> node = liabilitiesList.get(0);
			Amount openingAmount = node.getValue().getValue()[0];
			Amount closingAmount = node.getValue().getValue()[1];
			if(openingAmount != null) {
				long v = Math.abs(openingAmount.getValue());
				if(v > maxAmount) {
					maxAmount = v;
				}
			}
			if(closingAmount != null) {
				long v = Math.abs(closingAmount.getValue());
				if(v > maxAmount) {
					maxAmount = v;
				}
			}
		}

		//印字領域の設定
		int assetsRows = 1;
		for(int i = 1; i < assetsList.size(); i++) {
			Node<Entry<List<AccountTitle>, Amount[]>> node = assetsList.get(i);
			String displayName = node.getName();
			Amount openingAmount = node.getValue().getValue()[0];
			Amount closingAmount = node.getValue().getValue()[1];
			if(openingAmount != null) {
				long v = Math.abs(openingAmount.getValue());
				if(v > maxAmount) {
					maxAmount = v;
				}
			}
			if(closingAmount != null) {
				long v = Math.abs(closingAmount.getValue());
				if(v > maxAmount) {
					maxAmount = v;
				}
			}
			//対象の仕訳が存在しない科目は印字をスキップします。（ただし、常に表示する見出しに含まれていない場合に限る。）
			if(openingAmount == null && closingAmount == null && !bsLayout.isAlwaysShown(displayName)) {
				continue;
			}
			//期首・期末どちらもゼロで表示しない見出しに含まれている場合、印字をスキップします。
			if((openingAmount == null || openingAmount.getValue() == 0)
					&& (closingAmount == null || closingAmount.getValue() == 0) && bsLayout.isHidden(displayName)) {
				continue;
			}
			assetsRows++;
		}
		int liabilitiesRows = 1;
		for(int i = 1; i < liabilitiesList.size(); i++) {
			Node<Entry<List<AccountTitle>, Amount[]>> node = liabilitiesList.get(i);
			String displayName = node.getName();
			Amount openingAmount = node.getValue().getValue()[0];
			Amount closingAmount = node.getValue().getValue()[1];
			if(openingAmount != null) {
				long v = Math.abs(openingAmount.getValue());
				if(v > maxAmount) {
					maxAmount = v;
				}
			}
			if(closingAmount != null) {
				long v = Math.abs(closingAmount.getValue());
				if(v > maxAmount) {
					maxAmount = v;
				}
			}
			//対象の仕訳が存在しない科目は印字をスキップします。（ただし、常に表示する見出しに含まれていない場合に限る。）
			if(openingAmount == null && closingAmount == null && !bsLayout.isAlwaysShown(displayName)) {
				continue;
			}
			//期首・期末どちらもゼロで表示しない見出しに含まれている場合、印字をスキップします。
			if((openingAmount == null || openingAmount.getValue() == 0)
					&& (closingAmount == null || closingAmount.getValue() == 0)	&& bsLayout.isHidden(displayName)) {
				continue;
			}
			liabilitiesRows++;
		}
		int equityRows = 1;
		for(int i = 1; i < equityList.size(); i++) {
			Node<Entry<List<AccountTitle>, Amount[]>> node = equityList.get(i);
			String displayName = node.getName();
			Amount openingAmount = node.getValue().getValue()[0];
			Amount closingAmount = node.getValue().getValue()[1];
			if(openingAmount != null) {
				long v = Math.abs(openingAmount.getValue());
				if(v > maxAmount) {
					maxAmount = v;
				}
			}
			if(closingAmount != null) {
				long v = Math.abs(closingAmount.getValue());
				if(v > maxAmount) {
					maxAmount = v;
				}
			}
			//対象の仕訳が存在しない科目は印字をスキップします。（ただし、常に表示する見出しに含まれていない場合に限る。）
			if(openingAmount == null && closingAmount == null && !bsLayout.isAlwaysShown(displayName)) {
				continue;
			}
			//期首・期末どちらもゼロで表示しない見出しに含まれている場合、印字をスキップします。
			if((openingAmount == null || openingAmount.getValue() == 0)
					&& (closingAmount == null || closingAmount.getValue() == 0) && bsLayout.isHidden(displayName)) {
				continue;
			}
			equityRows++;
		}

		// 貸借対照表の金額印字幅。通常は22mm。最大金額が 99999999999（11桁）を超える場合は26mm、
		// 9999999999（10桁）を超える場合は25mm、999999999（9桁）を超える場合は24mm にします。
		double amountPrintWidth = 22;
		if(maxAmount > 99999999999L) {
			amountPrintWidth = 26;
		} else if(maxAmount > 9999999999L) {
			amountPrintWidth = 25;
		} else if(maxAmount > 999999999L) {
			amountPrintWidth = 24;
		}

		int rows = Math.max(assetsRows, liabilitiesRows + equityRows);
		printData.add("\t\\box 0 0 -0 -0");
		printData.add("\t\\line-style thin solid");
		printData.add("\t\\line " + String.format("87.3 %.2f 87.3 %.2f", 25.2, 37.0 + rows * ROW_HEIGHT));
		printData.add("\t\\line " + String.format("87.7 %.2f 87.7 %.2f", 25.2, 37.0 + rows * ROW_HEIGHT));
		printData.add("\t\\line " + String.format("35.5 %.2f 35.5 %.2f", 31.0, 37.0 + rows * ROW_HEIGHT));
		printData.add("\t\\line " + String.format("61.5 %.2f 61.5 %.2f", 31.0, 37.0 + rows * ROW_HEIGHT));
		printData.add("\t\\line " + String.format("123  %.2f 123  %.2f", 31.0, 37.0 + rows * ROW_HEIGHT));
		printData.add("\t\\line " + String.format("149  %.2f 149  %.2f", 31.0, 37.0 + rows * ROW_HEIGHT));

		printData.add("\t\\box 0 37 -0 -0");
		printData.add("\t\\font serif 10");
		printData.add("\t\\line-style thin dot");

		double y = 0.0;
		for(int i = 1; i < rows; i++) {
			printData.add("\t\\line " + String.format("0 %.2f -0 %.2f", y, y));
			y += ROW_HEIGHT;
		}
		printData.add("\t\\line-style thin solid");
		printData.add("\t\\line " + String.format("0 %.2f -0 %.2f", y, y));
		y += ROW_HEIGHT;
		printData.add("\t\\line " + String.format("0 %.2f -0 %.2f", y, y));
		printData.add("\t\\line " + String.format("0 %.2f -0 %.2f", y + 0.4, y + 0.4));

		//資産
		y = 0.0;
		for(int i = 1; i < assetsList.size(); i++) {
			Node<Entry<List<AccountTitle>, Amount[]>> node = assetsList.get(i);
			String displayName = node.getName();
			Amount openingAmount = node.getValue().getValue()[0];
			Amount closingAmount = node.getValue().getValue()[1];
			int sign = bsLayout.isSignReversed(node.getName()) ? -1 : 1;

			// 事業主貸の期首欄には斜線を引きます。
			if("事業主貸".equals(node.getName())) {
				printData.add("\t\t\\box " + String.format("35.5 %.2f 26 %.2f", y, ROW_HEIGHT));
				printData.add("\t\t\\line-style thin dot");
				printData.add("\t\t\\line -0 0.15 0 -0.15");
			}
			//対象の仕訳が存在しない科目は印字をスキップします。（ただし、常に表示する見出しに含まれていない場合に限る。）
			if(openingAmount == null && closingAmount == null && !bsLayout.isAlwaysShown(displayName)) {
				continue;
			}
			//期首・期末どちらもゼロで表示しない見出しに含まれている場合、印字をスキップします。
			if((openingAmount == null || openingAmount.getValue() == 0)
					&& (closingAmount == null || closingAmount.getValue() == 0) && bsLayout.isHidden(displayName)) {
				continue;
			}
			printData.add("\t\t\\box " + String.format("2 %.2f 35.5 %.2f", y, ROW_HEIGHT));
			printData.add("\t\t\\align center left");
			printData.add("\t\t\\text " + displayName);
			printData.add("\t\t\\align center right");
			if(openingAmount != null) {
				printData.add("\t\t\\box " + String.format("35.5 %.2f %.2f %.2f", y, amountPrintWidth, ROW_HEIGHT));
				printData.add("\t\t\\text " + formatMoney(sign * openingAmount.getValue()));
			}
			if(closingAmount != null) {
				printData.add("\t\t\\box " + String.format("61.5 %.2f %.2f %.2f", y, amountPrintWidth, ROW_HEIGHT));
				printData.add("\t\t\\text " + formatMoney(sign * closingAmount.getValue()));
			}
			y += ROW_HEIGHT;
		}
		//負債
		y = 0.0;
		for(int i = 1; i < liabilitiesList.size(); i++) {
			Node<Entry<List<AccountTitle>, Amount[]>> node = liabilitiesList.get(i);
			String displayName = node.getName();
			Amount openingAmount = node.getValue().getValue()[0];
			Amount closingAmount = node.getValue().getValue()[1];
			int sign = bsLayout.isSignReversed(node.getName()) ? -1 : 1;
			
			//対象の仕訳が存在しない科目は印字をスキップします。（ただし、常に表示する見出しに含まれていない場合に限る。）
			if(openingAmount == null && closingAmount == null && !bsLayout.isAlwaysShown(displayName)) {
				continue;
			}
			//期首・期末どちらもゼロで表示しない見出しに含まれている場合、印字をスキップします。
			if((openingAmount == null || openingAmount.getValue() == 0)
					&& (closingAmount == null || closingAmount.getValue() == 0) && bsLayout.isHidden(displayName)) {
				continue;
			}
			printData.add("\t\t\\box " + String.format("89.5 %.2f 35.5 %.2f", y, ROW_HEIGHT));
			printData.add("\t\t\\align center left");
			printData.add("\t\t\\text " + displayName);
			printData.add("\t\t\\align center right");
			if(openingAmount != null) {
				printData.add("\t\t\\box " + String.format("123 %.2f %.2f %.2f", y, amountPrintWidth, ROW_HEIGHT));
				printData.add("\t\t\\text " + formatMoney(sign * openingAmount.getValue()));
			}
			if(closingAmount != null) {
				printData.add("\t\t\\box " + String.format("149 %.2f %.2f %.2f", y, amountPrintWidth, ROW_HEIGHT));
				printData.add("\t\t\\text " + formatMoney(sign * closingAmount.getValue()));
			}
			y += ROW_HEIGHT;
		}
		//資本（純資産）
		y = (rows - equityList.size()) * ROW_HEIGHT;
		printData.add("\t\\line " + String.format("87.7 %.2f -0 %.2f", y, y));
		for(int i = 1; i < equityList.size(); i++) {
			Node<Entry<List<AccountTitle>, Amount[]>> node = equityList.get(i);
			String displayName = node.getName();
			Amount openingAmount = node.getValue().getValue()[0];
			Amount closingAmount = node.getValue().getValue()[1];
			int sign = bsLayout.isSignReversed(node.getName()) ? -1 : 1;

			// 事業主借および控除前の所得金額の期首欄には斜線を引きます。
			if("事業主借".equals(node.getName()) || "控除前の所得金額".equals(node.getName())) {
				printData.add("\t\t\\box " + String.format("123 %.2f 26 %.2f", y, ROW_HEIGHT));
				printData.add("\t\t\\line-style thin dot");
				printData.add("\t\t\\line -0 0.15 0 -0.15");
			}
			//対象の仕訳が存在しない科目は印字をスキップします。（ただし、常に表示する見出しに含まれていない場合に限る。）
			if(openingAmount == null && closingAmount == null && !bsLayout.isAlwaysShown(displayName)) {
				continue;
			}
			//期首・期末どちらもゼロで表示しない見出しに含まれている場合、印字をスキップします。
			if((openingAmount == null || openingAmount.getValue() == 0)
					&& (closingAmount == null || closingAmount.getValue() == 0) && bsLayout.isHidden(displayName)) {
				continue;
			}
			printData.add("\t\t\\box " + String.format("89.5 %.2f 35.5 %.2f", y, ROW_HEIGHT));
			printData.add("\t\t\\align center left");
			printData.add("\t\t\\text " + displayName);
			printData.add("\t\t\\align center right");
			if(openingAmount != null) {
				printData.add("\t\t\\box " + String.format("123 %.2f %.2f %.2f", y, amountPrintWidth, ROW_HEIGHT));
				printData.add("\t\t\\text " + formatMoney(sign * openingAmount.getValue()));
			}
			if(closingAmount != null) {
				printData.add("\t\t\\box " + String.format("149 %.2f %.2f %.2f", y, amountPrintWidth, ROW_HEIGHT));
				printData.add("\t\t\\text " + formatMoney(sign * closingAmount.getValue()));
			}
			y += ROW_HEIGHT;
		}

		//合計
		y = (rows - 1) * ROW_HEIGHT;
		if(assetsList.size() > 0) {
			//合計(資産)
			Node<Entry<List<AccountTitle>, Amount[]>> node = assetsList.get(0);
			String displayName = "合計";
			Amount openingAmount = node.getValue().getValue()[0];
			Amount closingAmount = node.getValue().getValue()[1];
			printData.add("\t\t\\font serif 10 bold");
			printData.add("\t\t\\box " + String.format("2 %.2f 35.5 %.2f", y, ROW_HEIGHT));
			printData.add("\t\t\\align center left");
			printData.add("\t\t\\text " + displayName);
			printData.add("\t\t\\align center right");
			if(openingAmount != null) {
				printData.add("\t\t\\box " + String.format("35.5 %.2f %.2f %.2f", y, amountPrintWidth, ROW_HEIGHT));
				printData.add("\t\t\\text " + formatMoney(openingAmount.getValue()));
			}
			if(closingAmount != null) {
				printData.add("\t\t\\box " + String.format("61.5 %.2f %.2f %.2f", y, amountPrintWidth, ROW_HEIGHT));
				printData.add("\t\t\\text " + formatMoney(closingAmount.getValue()));
			}
		}
		if(liabilitiesList.size() > 0 || equityList.size() > 0) {
			//合計（負債、資本）
			String displayName = "合計";
			int openingAmount = 0;
			int closingAmount = 0;
			if(liabilitiesList.size() > 0) {
				Amount o = liabilitiesList.get(0).getValue().getValue()[0];
				if(o != null) {
					openingAmount += o.getValue();
				}
				Amount c = liabilitiesList.get(0).getValue().getValue()[1];
				if(c != null) {
					closingAmount += c.getValue();
				}
			}
			if(equityList.size() > 0) {
				Amount o = equityList.get(0).getValue().getValue()[0];
				if(o != null) {
					openingAmount += o.getValue();
				}
				Amount c = equityList.get(0).getValue().getValue()[1];
				if(c != null) {
					closingAmount += c.getValue();
				}
			}
			printData.add("\t\t\\font serif 10 bold");
			printData.add("\t\t\\box " + String.format("89.5 %.2f 35.5 %.2f", y, ROW_HEIGHT));
			printData.add("\t\t\\align center left");
			printData.add("\t\t\\text " + displayName);
			printData.add("\t\t\\align center right");
			printData.add("\t\t\\box " + String.format("123 %.2f %.2f %.2f", y, amountPrintWidth, ROW_HEIGHT));
			printData.add("\t\t\\text " + formatMoney(openingAmount));
			printData.add("\t\t\\box " + String.format("149 %.2f %.2f %.2f", y, amountPrintWidth, ROW_HEIGHT));
			printData.add("\t\t\\text " + formatMoney(closingAmount));
		}
	}

	public void writeTo(Path path) throws IOException {
		prepare();

		PdfBrewer brewer = new PdfBrewer(Main.fontLoader);
		brewer.setCreator(Util.getPdfCreator());
		BrewerData pb = new BrewerData(printData, brewer.getFontLoader());
		brewer.setTitle("貸借対照表");
		brewer.process(pb);
		brewer.save(path);
		brewer.close();
	}

	/** 次期開始仕訳を作成します。
	 * 
	 * @param path 次期開始仕訳を保存するファイル。nullを指定した場合、ファイル出力はおこないません。
	 * @return 次期開始仕訳のYAML文字列
	 * @throws IOException 
	 */
	public String createOpeningJournalEntries(Path path) throws IOException {
		List<Entry<AccountTitle, Amount>> debtors = new ArrayList<>();
		long debtorsTotal = 0;
		List<Entry<AccountTitle, Amount>> creditors = new ArrayList<>();
		long creditorsTotal = 0;
		
		StringBuilder sb = new StringBuilder();
		String nextOpeningDate = DateTimeFormatter.ISO_LOCAL_DATE.format(this.closingDate.plusDays(1));

		if(isSoloProprietorship) {
			//個人事業主の場合は、事業主貸（資産）、事業主借（負債）、所得金額（資本）が次期の元入金に加算されます。
			//事業主貸、事業主借を除く資産と負債を集計して次期開始仕訳を作成します。
			//相手勘定科目はすべて元入金になります。
			for(Entry<AccountTitle, Amount> e : closingBalances.entrySet()) {
				AccountTitle accountTitle = e.getKey();
				Amount amount = e.getValue();
				if(accountTitle.getDisplayName().equals("事業主貸") || accountTitle.getDisplayName().equals("事業主借")) {
					continue;
				}
				if(accountTitle.getType() == AccountType.Assets) {
					if(amount.getValue() == 0) {
						continue;
					} else if(amount.getValue() > 0) {
						debtors.add(e);
						debtorsTotal += amount.getValue();
					} else {
						creditors.add(e);
						creditorsTotal -= amount.getValue();
					}
				} else if(accountTitle.getType() == AccountType.Liabilities) {
					if(amount.getValue() == 0) {
						continue;
					} else if(amount.getValue() > 0) {
						creditors.add(e);
						creditorsTotal += amount.getValue();
					} else {
						debtors.add(e);
						debtorsTotal -= amount.getValue();
					}
				}
			}
			if(debtors.size() > 0) {
				sb.append("- 日付: " + nextOpeningDate + "\r\n");
				sb.append("  摘要: 元入金\r\n");
				sb.append("  借方: [ ");
				for(int i = 0; i < debtors.size(); i++) {
					AccountTitle accountTitle = debtors.get(i).getKey();
					Amount amount = debtors.get(i).getValue();
					sb.append("{勘定科目: " + accountTitle.getDisplayName() + ", 金額: " + Math.abs(amount.getValue()) + "}");
					if(i + 1 < debtors.size()) {
						sb.append(", ");
					}
				}
				sb.append(" ]\r\n");
				sb.append("  貸方: [ {勘定科目: 元入金, 金額: " + debtorsTotal + "} ]\r\n");
				sb.append("\r\n");
			}
			if(creditors.size() > 0) {
				sb.append("- 日付: " + nextOpeningDate + "\r\n");
				sb.append("  摘要: 元入金\r\n");
				sb.append("  借方: [ {勘定科目: 元入金, 金額: " + creditorsTotal + "} ]\r\n");
				sb.append("  貸方: [ ");
				for(int i = 0; i < creditors.size(); i++) {
					AccountTitle accountTitle = creditors.get(i).getKey();
					Amount amount = creditors.get(i).getValue();
					sb.append("{勘定科目: " + accountTitle.getDisplayName() + ", 金額: " + Math.abs(amount.getValue()) + "}");
					if(i + 1 < creditors.size()) {
						sb.append(", ");
					}
				}
				sb.append(" ]\r\n");
				sb.append("\r\n");
			}
		} else {
			//法人の場合は資産、負債、純資産をすべて繰り越します。
			for(Entry<AccountTitle, Amount> e : closingBalances.entrySet()) {
				AccountTitle accountTitle = e.getKey();
				Amount amount = e.getValue();
				if(accountTitle.getType() == AccountType.Assets) {
					if(amount.getValue() == 0) {
						continue;
					} else if(amount.getValue() > 0) {
						debtors.add(e);
					} else {
						creditors.add(e);
					}
				} else if(accountTitle.getType() == AccountType.Liabilities || accountTitle.getType() == AccountType.Equity) {
					if(amount.getValue() == 0) {
						continue;
					} else if(amount.getValue() > 0) {
						creditors.add(e);
					} else {
						debtors.add(e);
					}
				}
			}
			
			if(debtors.size() > 0 && creditors.size() > 0) {
				sb.append("- 日付: " + nextOpeningDate + "\r\n");
				sb.append("  摘要: 前期繰越\r\n");
				sb.append("  借方: [ ");
				for(int i = 0; i < debtors.size(); i++) {
					AccountTitle accountTitle = debtors.get(i).getKey();
					Amount amount = debtors.get(i).getValue();
					sb.append("{勘定科目: " + accountTitle.getDisplayName() + ", 金額: " + Math.abs(amount.getValue()) + "}");
					if(i + 1 < debtors.size()) {
						sb.append(", ");
					}
				}
				sb.append(" ]\r\n");
				sb.append("  貸方: [ ");
				for(int i = 0; i < creditors.size(); i++) {
					AccountTitle accountTitle = creditors.get(i).getKey();
					Amount amount = creditors.get(i).getValue();
					sb.append("{勘定科目: " + accountTitle.getDisplayName() + ", 金額: " + Math.abs(amount.getValue()) + "}");
					if(i + 1 < creditors.size()) {
						sb.append(", ");
					}
				}
				sb.append(" ]\r\n");
				sb.append("\r\n");
			}
		}
		
		//期末商品棚卸高 を 期首商品棚卸高として開始仕訳に追加します。
		for(JournalEntry entry : journalEntries) {
			if(entry.isClosing()) {
				continue;
			}
			for(Creditor creditor : entry.getCreditors()) {
				if(creditor.getAccountTitle().getDisplayName().equals("期末商品棚卸高")) {
					sb.append("- 日付: " + nextOpeningDate + "\r\n");
					sb.append("  摘要: 前期繰越\r\n");
					sb.append("  借方: [ {勘定科目: 期首商品棚卸高, 金額: " + creditor.getAmount() + "} ]\r\n");
					sb.append("  貸方: [ ");
					for(int i = 0; i < entry.getDebtors().size(); i++) {
						Debtor debtor = entry.getDebtors().get(i);
						sb.append("{勘定科目: " + debtor.getAccountTitle().getDisplayName() + ", 金額: " + debtor.getAmount() + "}");
						if(i + 1 < entry.getDebtors().size()) {
							sb.append(", ");
						}
					}
					sb.append(" ]\r\n");
					sb.append("\r\n");
					break;
				}
			}
			for(Debtor debtor : entry.getDebtors()) {
				if(debtor.getAccountTitle().getDisplayName().equals("期末商品棚卸高")) {
					sb.append("- 日付: " + nextOpeningDate + "\r\n");
					sb.append("  摘要: 前期繰越\r\n");
					sb.append("  借方: [ ");
					for(int i = 0; i < entry.getCreditors().size(); i++) {
						Creditor creditor = entry.getCreditors().get(i);
						sb.append("{勘定科目: " + creditor.getAccountTitle().getDisplayName() + ", 金額: " + creditor.getAmount() + "}");
						if(i + 1 < entry.getCreditors().size()) {
							sb.append(", ");
						}
					}
					sb.append(" ]\r\n");
					sb.append("\r\n");
					break;
				}
			}
		}
		
		
		String s = sb.toString();
		
		if(path != null) {
			try(Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				writer.write(s);
			}
		}
		
		return s;
	}
	
	private static String formatMoney(long amount) {
		if(MINUS_SIGN != null && amount < 0) {
			return MINUS_SIGN + String.format("%,d", -amount);
		}
		return String.format("%,d", amount);
	}
	
	private void dump(Node<Entry<List<AccountTitle>, Amount[]>> node) {
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < node.getLevel(); i++) {
			sb.append(" - ");
		}
		sb.append(node.getName());
		Amount[] amounts = node.getValue().getValue();
		sb.append("{ " + amounts[0] + ", " + amounts[1] + " } ");
		if(node.getValue().getKey() != null) {
			sb.append(": [");
			for(int i = 0; i < node.getValue().getKey().size(); i++) {
				sb.append(node.getValue().getKey().get(i).getDisplayName());
				if(i + 1 < node.getValue().getKey().size()) {
					sb.append(", ");
				}
			}
			sb.append("]");
		}
		System.out.println(sb.toString());
		for(Node<Entry<List<AccountTitle>, Amount[]>> child : node.getChildren()) {
			dump(child);
		}
	}
}
