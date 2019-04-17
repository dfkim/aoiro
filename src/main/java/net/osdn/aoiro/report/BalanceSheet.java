package net.osdn.aoiro.report;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.osdn.aoiro.AccountSettlement;
import net.osdn.aoiro.Util;
import net.osdn.aoiro.model.AccountTitle;
import net.osdn.aoiro.model.AccountType;
import net.osdn.aoiro.model.Amount;
import net.osdn.aoiro.model.Creditor;
import net.osdn.aoiro.model.Debtor;
import net.osdn.aoiro.model.JournalEntry;
import net.osdn.aoiro.model.Node;
import net.osdn.pdf_brewer.BrewerData;
import net.osdn.pdf_brewer.PdfBrewer;

/** 貸借対照表
 * 
 */
public class BalanceSheet {

	public static String MINUS_SIGN = "△";
	private static final int ROWS = 40;
	private static final double ROW_HEIGHT = 6.0;
	
	private Node<Entry<List<AccountTitle>, Amount[]>> bsRoot;
	private List<JournalEntry> journalEntries;
	private boolean isSoloProprietorship;
	private Set<String> alwaysShownNames;
	private Date openingDate;
	private Date closingDate;
	private Map<AccountTitle, Amount> openingBalances = new HashMap<AccountTitle, Amount>();
	private Map<AccountTitle, Amount> closingBalances = new HashMap<AccountTitle, Amount>();
	private List<Node<Entry<List<AccountTitle>, Amount[]>>> assetsList;
	private List<Node<Entry<List<AccountTitle>, Amount[]>>> liabilitiesList;
	private List<Node<Entry<List<AccountTitle>, Amount[]>>> equityList;
	
	private List<String> pageData = new ArrayList<String>();
	private List<String> printData;

	public BalanceSheet(Node<Entry<List<AccountTitle>, Amount[]>> bsRoot, List<JournalEntry> journalEntries, boolean isSoloProprietorship, Set<String> alwaysShownNames) throws IOException {
		this.bsRoot = bsRoot;
		this.journalEntries = journalEntries;
		this.isSoloProprietorship = isSoloProprietorship;
		this.alwaysShownNames = alwaysShownNames != null ? alwaysShownNames : new HashSet<String>();
		
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
		retrieve(bsRoot, journalEntries);
		//dump(bsRoot);
		
		//リスト
		for(Node<Entry<List<AccountTitle>, Amount[]>> child : bsRoot.getChildren()) {
			if(child.getName().equals("資産")) {
				assetsList = getList(child);
			} else if(child.getName().equals("負債")) {
				liabilitiesList = getList(child);
			} else if(child.getName().equals("純資産")) {
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
		DateFormat df = new SimpleDateFormat("GGGG y 年 M 月 d 日", Util.getLocale());
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(this.openingDate);
		String openingDate = df.format(this.openingDate).replace(" 1 年", "元年");
		openingDate = Util.toKanjiEra(openingDate);
		String openingMonth = Integer.toString(calendar.get(Calendar.MONTH) + 1);
		String openingDay = Integer.toString(calendar.get(Calendar.DAY_OF_MONTH));
		calendar.setTime(this.closingDate);
		String closingDate = df.format(this.closingDate).replace(" 1 年", "元年");
		closingDate = Util.toKanjiEra(closingDate);
		String closingMonth = Integer.toString(calendar.get(Calendar.MONTH) + 1);
		String closingDay = Integer.toString(calendar.get(Calendar.DAY_OF_MONTH));
		
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

		//印字領域の設定
		int assetsRows = 1;
		for(int i = 1; i < assetsList.size(); i++) {
			Node<Entry<List<AccountTitle>, Amount[]>> node = assetsList.get(i);
			String displayName = node.getName();
			Amount openingAmount = node.getValue().getValue()[0];
			Amount closingAmount = node.getValue().getValue()[1];
			//対象の仕訳が存在しない科目は印字をスキップします。（ただし、常に表示する見出しに含まれていない場合に限る。）
			if(openingAmount == null && closingAmount == null && !alwaysShownNames.contains(displayName)) {
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
			//対象の仕訳が存在しない科目は印字をスキップします。（ただし、常に表示する見出しに含まれていない場合に限る。）
			if(openingAmount == null && closingAmount == null && !alwaysShownNames.contains(displayName)) {
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
			//対象の仕訳が存在しない科目は印字をスキップします。（ただし、常に表示する見出しに含まれていない場合に限る。）
			if(openingAmount == null && closingAmount == null && !alwaysShownNames.contains(displayName)) {
				continue;
			}
			equityRows++;
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
			
			//対象の仕訳が存在しない科目は印字をスキップします。（ただし、常に表示する見出しに含まれていない場合に限る。）
			if(openingAmount == null && closingAmount == null && !alwaysShownNames.contains(displayName)) {
				continue;
			}
			printData.add("\t\t\\box " + String.format("2 %.2f 35.5 %.2f", y, ROW_HEIGHT));
			printData.add("\t\t\\align center left");
			printData.add("\t\t\\text " + displayName);
			printData.add("\t\t\\align center right");
			if(openingAmount != null) {
				printData.add("\t\t\\box " + String.format("35.5 %.2f 22 %.2f", y, ROW_HEIGHT));
				printData.add("\t\t\\text " + formatMoney(openingAmount.getValue()));
			}
			if(closingAmount != null) {
				printData.add("\t\t\\box " + String.format("61.5 %.2f 22 %.2f", y, ROW_HEIGHT));
				printData.add("\t\t\\text " + formatMoney(closingAmount.getValue()));
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
			
			//対象の仕訳が存在しない科目は印字をスキップします。（ただし、常に表示する見出しに含まれていない場合に限る。）
			if(openingAmount == null && closingAmount == null && !alwaysShownNames.contains(displayName)) {
				continue;
			}
			printData.add("\t\t\\box " + String.format("89.5 %.2f 35.5 %.2f", y, ROW_HEIGHT));
			printData.add("\t\t\\align center left");
			printData.add("\t\t\\text " + displayName);
			printData.add("\t\t\\align center right");
			if(openingAmount != null) {
				printData.add("\t\t\\box " + String.format("123 %.2f 22 %.2f", y, ROW_HEIGHT));
				printData.add("\t\t\\text " + formatMoney(openingAmount.getValue()));
			}
			if(closingAmount != null) {
				printData.add("\t\t\\box " + String.format("149 %.2f 22 %.2f", y, ROW_HEIGHT));
				printData.add("\t\t\\text " + formatMoney(closingAmount.getValue()));
			}
			y += ROW_HEIGHT;
		}
		//純資産(資本)
		y = (rows - equityList.size()) * ROW_HEIGHT;
		printData.add("\t\\line " + String.format("87.7 %.2f -0 %.2f", y, y));
		for(int i = 1; i < equityList.size(); i++) {
			Node<Entry<List<AccountTitle>, Amount[]>> node = equityList.get(i);
			String displayName = node.getName();
			Amount openingAmount = node.getValue().getValue()[0];
			Amount closingAmount = node.getValue().getValue()[1];
			
			//対象の仕訳が存在しない科目は印字をスキップします。（ただし、常に表示する見出しに含まれていない場合に限る。）
			if(openingAmount == null && closingAmount == null && !alwaysShownNames.contains(displayName)) {
				continue;
			}
			printData.add("\t\t\\box " + String.format("89.5 %.2f 35.5 %.2f", y, ROW_HEIGHT));
			printData.add("\t\t\\align center left");
			printData.add("\t\t\\text " + displayName);
			printData.add("\t\t\\align center right");
			if(openingAmount != null) {
				printData.add("\t\t\\box " + String.format("123 %.2f 22 %.2f", y, ROW_HEIGHT));
				printData.add("\t\t\\text " + formatMoney(openingAmount.getValue()));
			}
			if(closingAmount != null) {
				printData.add("\t\t\\box " + String.format("149 %.2f 22 %.2f", y, ROW_HEIGHT));
				printData.add("\t\t\\text " + formatMoney(closingAmount.getValue()));
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
				printData.add("\t\t\\box " + String.format("35.5 %.2f 22 %.2f", y, ROW_HEIGHT));
				printData.add("\t\t\\text " + formatMoney(openingAmount.getValue()));
			}
			if(closingAmount != null) {
				printData.add("\t\t\\box " + String.format("61.5 %.2f 22 %.2f", y, ROW_HEIGHT));
				printData.add("\t\t\\text " + formatMoney(closingAmount.getValue()));
			}
		}
		if(liabilitiesList.size() > 0 || equityList.size() > 0) {
			//合計(負債、純資産)
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
			printData.add("\t\t\\box " + String.format("123 %.2f 22 %.2f", y, ROW_HEIGHT));
			printData.add("\t\t\\text " + formatMoney(openingAmount));
			printData.add("\t\t\\box " + String.format("149 %.2f 22 %.2f", y, ROW_HEIGHT));
			printData.add("\t\t\\text " + formatMoney(closingAmount));
		}
	}

	public void writeTo(File file) throws IOException {
		prepare();
		
		BrewerData pb = new BrewerData(printData);
		PdfBrewer brewer = new PdfBrewer();
		brewer.setTitle("貸借対照表");
		brewer.process(pb);
		brewer.save(file);
		
		
		/*
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
		for(String s : printData) {
			writer.write(s);
			writer.write("\r\n");
		}
		writer.close();
		*/
	}

	/** 次期開始仕訳を作成します。
	 * 
	 * @param file 次期開始仕訳を保存するファイル。nullを指定した場合、ファイル出力はおこないません。
	 * @return 次期開始仕訳のYAML文字列
	 * @throws IOException 
	 */
	public String createOpeningJournalEntries(File file) throws IOException {
		List<Entry<AccountTitle, Amount>> debtors = new ArrayList<Entry<AccountTitle, Amount>>();
		int debtorsTotal = 0;
		List<Entry<AccountTitle, Amount>> creditors = new ArrayList<Entry<AccountTitle, Amount>>();
		int creditorsTotal = 0;
		
		StringBuilder sb = new StringBuilder();
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(this.closingDate);
		calendar.add(Calendar.DATE, 1);
		
		if(isSoloProprietorship) {
			//個人事業主の場合は、事業主貸(資産)、事業主借(負債)、所得金額(純資産)が次期の元入金に加算されます。
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
				sb.append("- 日付: " + df.format(calendar.getTime()) + "\r\n");
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
				sb.append("- 日付: " + df.format(calendar.getTime()) + "\r\n");
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
				} else if(accountTitle.getType() == AccountType.Liabilities || accountTitle.getType() == AccountType.NetAssets) {
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
				sb.append("- 日付: " + df.format(calendar.getTime()) + "\r\n");
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
					sb.append("- 日付: " + df.format(calendar.getTime()) + "\r\n");
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
					sb.append("- 日付: " + df.format(calendar.getTime()) + "\r\n");
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
		
		if(file != null) {
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
			writer.write(s);
			writer.close();
		}
		
		return s;
	}
	
	private static String formatMoney(int amount) {
		if(MINUS_SIGN != null && amount < 0) {
			return "△" + String.format("%,d", -amount);
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
