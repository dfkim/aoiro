package net.osdn.aoiro.report;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.osdn.aoiro.AccountSettlement;
import net.osdn.aoiro.Util;
import net.osdn.aoiro.model.AccountTitle;
import net.osdn.aoiro.model.Amount;
import net.osdn.aoiro.model.Creditor;
import net.osdn.aoiro.model.Debtor;
import net.osdn.aoiro.model.JournalEntry;
import net.osdn.aoiro.model.Node;
import net.osdn.pdf_brewer.BrewerData;
import net.osdn.pdf_brewer.PdfBrewer;

/** 損益計算書(P/L)
 *
 */
public class ProfitAndLoss {
	
	public static String MINUS_SIGN = "△";
	private static final int ROWS = 40;
	private static final double ROW_HEIGHT = 6.0;
	
	private Node<List<AccountTitle>, Amount> plRoot;
	private List<JournalEntry> journalEntries;
	private Date openingDate;
	private Date closingDate;
	private Map<AccountTitle, Amount> incomeSummaries = new HashMap<AccountTitle, Amount>(); 
	private List<Node<List<AccountTitle>, Amount>> list;
	private List<Entry<String, Amount[]>> monthlyTotals;
	private List<String> pageData = new ArrayList<String>();
	private List<String> printData;
	
	public ProfitAndLoss(Node<List<AccountTitle>, Amount> plRoot, List<JournalEntry> journalEntries, boolean isSoloProprietorship) throws IOException {
		this.plRoot = plRoot;
		this.journalEntries = journalEntries;
		
		this.openingDate = AccountSettlement.getOpeningDate(journalEntries, isSoloProprietorship);
		this.closingDate = AccountSettlement.getClosingDate(journalEntries, isSoloProprietorship);

		for(JournalEntry entry : journalEntries) {
			if(entry.isIncomeSummary()) {
				for(Debtor debtor : entry.getDebtors()) {
					if(!debtor.getAccountTitle().equals(AccountTitle.INCOME_SUMMARY)) {
						Amount amount = incomeSummaries.get(debtor.getAccountTitle());
						if(amount == null) {
							amount = new Amount(debtor.getAccountTitle().getType().getNormalBalance(), 0);
							incomeSummaries.put(debtor.getAccountTitle(), amount);
						}
						if(debtor.getAccountTitle().getType().getNormalBalance() != Debtor.class) {
							amount.increase(debtor.getAmount());
						} else {
							amount.decrease(debtor.getAmount());
						}
					}
				}
				for(Creditor creditor : entry.getCreditors()) {
					if(!creditor.getAccountTitle().equals(AccountTitle.INCOME_SUMMARY)) {
						Amount amount = incomeSummaries.get(creditor.getAccountTitle());
						if(amount == null) {
							amount = new Amount(creditor.getAccountTitle().getType().getNormalBalance(), 0);
							incomeSummaries.put(creditor.getAccountTitle(), amount);
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
		
		//再帰集計
		retrieve(plRoot, journalEntries);
		
		//リスト作成
		list = getList(plRoot);
		
		//月別集計
		monthlyTotals = getMonthlyTotals(journalEntries);
		
		InputStream in = getClass().getResourceAsStream("/templates/損益計算書.pb");
		BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		String line;
		while((line = r.readLine()) != null) {
			pageData.add(line);
		}
		r.close();
	}
	
	private Amount retrieve(Node<List<AccountTitle>, Amount> node, List<JournalEntry> journalEntries) {
		Amount amount = null;
		for(Node<List<AccountTitle>, Amount> child : node.getChildren()) {
			Amount a = retrieve(child, journalEntries);
			if(a != null) {
				if(amount == null) {
					amount = new Amount(a.getNormalBalance(), a.getValue());
				} else {
					amount.increase(a);
				}
			}
		}
		if(node.getKey() != null) {
			for(AccountTitle accountTitle : node.getKey()) {
				Amount a = incomeSummaries.get(accountTitle);
				if(a != null) {
					if(amount == null) {
						amount = new Amount(a.getNormalBalance(), a.getValue());
					} else {
						amount.increase(a);
					}
				}
			}
		}
		node.setValue(amount);
		return amount;
	}
	
	protected List<Node<List<AccountTitle>, Amount>> getList(Node<List<AccountTitle>, Amount> node) {
		List<Node<List<AccountTitle>, Amount>> list = new ArrayList<Node<List<AccountTitle>, Amount>>();
		list.add(node);
		for(Node<List<AccountTitle>, Amount> child : node.getChildren()) {
			list.addAll(getList(child));
		}
		return list;
	}
	
	//月別集計
	protected List<Entry<String, Amount[]>> getMonthlyTotals(List<JournalEntry> journalEntries) {
		Map<String, Amount[]> map = new LinkedHashMap<String, Amount[]>();
		Calendar calendar = Calendar.getInstance(Util.getLocale());
		calendar.setTime(this.closingDate);
		calendar.set(Calendar.DAY_OF_MONTH, 1);
		for(int i = 1; i <= 12; i++) {
			calendar.add(Calendar.MONTH, 1);
			String month = (calendar.get(Calendar.MONTH) + 1) + "月";
			map.put(month, new Amount[2]);
		}
		map.put("雑収入", new Amount[2]);
		map.put("家事消費等", new Amount[2]);

		for(JournalEntry entry : journalEntries) {
			//開始仕訳と締切仕訳は集計に含めません。
			if(entry.isOpening() || entry.isClosing()) {
				continue;
			}
			calendar.setTime(entry.getDate());
			String month = (calendar.get(Calendar.MONTH) + 1) + "月";
			
			for(Debtor debtor : entry.getDebtors()) {
				String displayName = debtor.getAccountTitle().getDisplayName();
				if(displayName.equals("売上")) {
					Amount[] amounts = map.get(month);
					if(amounts[0] == null) {
						amounts[0] = new Amount(Creditor.class, 0);
					}
					amounts[0].decrease(debtor.getAmount());
					map.put(month, amounts);
				} else if(displayName.equals("雑収入")) {
					Amount[] amounts = map.get(displayName);
					if(amounts[0] == null) {
						amounts[0] = new Amount(Creditor.class, 0);
					}
					amounts[0].decrease(debtor.getAmount());
					map.put(month, amounts);
				} else if(displayName.equals("仕入")) {
					Amount[] amounts = map.get(month);
					if(amounts[1] == null) {
						amounts[1] = new Amount(Debtor.class, 0);
					}
					amounts[1].increase(debtor.getAmount());
					map.put(month, amounts);
				}
			}
			for(Creditor creditor : entry.getCreditors()) {
				String displayName = creditor.getAccountTitle().getDisplayName();
				if(displayName.equals("売上")) {
					Amount[] amounts = map.get(month);
					if(amounts[0] == null) {
						amounts[0] = new Amount(Creditor.class, 0);
					}
					amounts[0].increase(creditor.getAmount());
				} else if(displayName.equals("雑収入")) {
					Amount[] amounts = map.get(displayName);
					if(amounts[0] == null) {
						amounts[0] = new Amount(Creditor.class, 0);
					}
					amounts[0].increase(creditor.getAmount());
				} else if(displayName.equals("仕入")) {
					Amount[] amounts = map.get(month);
					if(amounts[1] == null) {
						amounts[1] = new Amount(Debtor.class, 0);
					}
					amounts[1].decrease(creditor.getAmount());
					map.put(month, amounts);
				}
			}
		}
		
		List<Entry<String, Amount[]>> list = new ArrayList<Entry<String, Amount[]>>();
		for(Entry<String, Amount[]> e : map.entrySet()) {
			list.add(e);
		}
		return list;
	}
	
	protected void prepare() {
		
		if(list.size() == 0) {
			return;
		}
		
		
		DateFormat df = new SimpleDateFormat("GGGG y 年 M 月 d 日", Util.getLocale());
		String openingDate = df.format(this.openingDate).replace(" 1 年", "元年");
		String closingDate = df.format(this.closingDate).replace(" 1 年", "元年");
		
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

		//印字領域の設定
		printData.add("\t\\box 0 37 -0 -0");
		printData.add("\t\\font serif 10");
		printData.add("\t\\line-style thin dot");
		
		double y = 0.0;
		for(int i = 1; i < list.size(); i++) {
			Node<List<AccountTitle>, Amount> node = list.get(i);
			
			Amount amount = node.getValue();
			//対象の仕訳が存在しない科目は印字をスキップします。
			/*
			if(amount == null) {
				continue;
			}
			*/
			
			if(i >= 2) {
				printData.add("\t\\line " + String.format("0 %.2f 95 %.2f", y, y));
			}
			
			if(node.getLevel() == 1) {
				printData.add("\t\t\\font serif 10 bold");
			} else {
				printData.add("\t\t\\font serif 10");
			}
			StringBuilder displayName = new StringBuilder();
			for(int j = 1; j < node.getLevel(); j++) {
				displayName.append("\u3000 ");//階層ごとに全角スペース1つと半角スペース1つを追加します。
			}
			displayName.append(node.getName());
			printData.add("\t\t\\box " + String.format("2 %.2f 63 %.2f", y, ROW_HEIGHT));
			printData.add("\t\t\\align center left");
			printData.add("\t\t\\text " + displayName);
			
			if(amount != null) {
				printData.add("\t\t\\box " + String.format("63 %.2f 27 %.2f", y, ROW_HEIGHT));
				printData.add("\t\t\\align center right");
				printData.add("\t\t\\text " + formatMoney(amount.getValue()));
			}
			y += ROW_HEIGHT;
		}
		printData.add("\t\\line-style thin solid");
		if(y > 0) {
			printData.add("\t\\line " + String.format("0 %.2f 95 %.2f", y, y));
			printData.add("\t\\line " + String.format("0 %.2f 95 %.2f", y + ROW_HEIGHT, y + ROW_HEIGHT));
			printData.add("\t\\line " + String.format("0 %.2f 95 %.2f", y + ROW_HEIGHT + 0.4, y + ROW_HEIGHT + 0.4));
		}
		printData.add("\t\\box 0 0 -0 -0");
		printData.add("\t\\line " + String.format("63 31.2 63 %.2f", 37 + y + ROW_HEIGHT));
		printData.add("\t\\line-style thin dot");
		printData.add("\t\\line " + String.format(" 0 30.8  0 %.2f", 37 + y + ROW_HEIGHT + 0.4));
		printData.add("\t\\line " + String.format("95 30.8 95 %.2f", 37 + y + ROW_HEIGHT + 0.4));
		printData.add("\t\\box 0 37 -0 -0");
		
		//合計 (青色申告特別控除前の所得金額)
		{
			String displayName = list.get(0).getName();
			int amountValue = 0;
			Amount amount = list.get(0).getValue();
			if(amount != null) {
				amountValue = (amount.getNormalBalance() == Creditor.class) ? amount.getValue() : -amount.getValue();
			}

			printData.add("\t\t\\font serif 10 bold");
			printData.add("\t\t\\box " + String.format("2 %.2f 63 %.2f", y, ROW_HEIGHT));
			printData.add("\t\t\\align center left");
			printData.add("\t\t\\text " + displayName);
			printData.add("\t\t\\box " + String.format("63 %.2f 27 %.2f", y, ROW_HEIGHT));
			printData.add("\t\t\\align center right");
			printData.add("\t\t\\text " + formatMoney(amountValue));
		}
		
		//月別
		Amount salesTotal = null;
		Amount purchaseTotal = null;
		
		y = 0.0;
		printData.add("\t\\box 0 37 -0 -0");
		printData.add("\t\t\\font serif 10");
		for(Entry<String, Amount[]> e : monthlyTotals) {
			String displayName = e.getKey();
			Amount[] amounts = e.getValue();
			
			if(displayName.endsWith("月")) {
				printData.add("\t\t\\box " + String.format("105 %.2f 13.5 %.2f", y, ROW_HEIGHT));
				printData.add("\t\t\\align center right");
			} else {
				printData.add("\t\t\\box " + String.format("105 %.2f 20 %.2f", y, ROW_HEIGHT));
				printData.add("\t\t\\align center");
			}
			printData.add("\t\t\\text " + displayName);
			
			if(amounts[0] != null) {
				printData.add("\t\t\\box " + String.format("125 %.2f 21 %.2f", y, ROW_HEIGHT));
				printData.add("\t\t\\align center right");
				printData.add("\t\t\\text " + formatMoney(amounts[0].getValue()));
				if(salesTotal == null) {
					salesTotal = new Amount(Creditor.class, 0);
				}
				salesTotal.increase(amounts[0].getValue());
			}
			if(amounts[1] != null) {
				printData.add("\t\t\\box " + String.format("150 %.2f 21 %.2f", y, ROW_HEIGHT));
				printData.add("\t\t\\align center right");
				printData.add("\t\t\\text " + formatMoney(amounts[1].getValue()));
				if(purchaseTotal == null) {
					purchaseTotal = new Amount(Debtor.class, 0);
				}
				purchaseTotal.increase(amounts[1].getValue());
			}
			y += ROW_HEIGHT;
		}
		printData.add("\t\t\\font serif 10 bold");
		if(salesTotal != null) {
			printData.add("\t\t\\box " + String.format("125 %.2f 21 %.2f", y, ROW_HEIGHT));
			printData.add("\t\t\\align center right");
			printData.add("\t\t\\text " + formatMoney(salesTotal.getValue()));
		}
		if(purchaseTotal != null) {
			printData.add("\t\t\\box " + String.format("150 %.2f 21 %.2f", y, ROW_HEIGHT));
			printData.add("\t\t\\align center right");
			printData.add("\t\t\\text " + formatMoney(purchaseTotal.getValue()));
		}
	}

	
	public void writeTo(File file) throws IOException {
		prepare();
		
		BrewerData pb = new BrewerData(printData);
		PdfBrewer brewer = new PdfBrewer();
		brewer.setTitle("損益計算書");
		brewer.process(pb);
		brewer.save(file);
	}
	
	private static String formatMoney(int amount) {
		if(MINUS_SIGN != null && amount < 0) {
			return "△" + String.format("%,d", -amount);
		}
		return String.format("%,d", amount);
	}
	
	/*
	private void dump(int indent, Node<List<AccountTitle>, Amount> node) {
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < indent; i++) {
			sb.append(" - ");
		}
		sb.append(node.getName());
		sb.append("{ " + node.getValue() + " } ");
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
		for(Node<List<AccountTitle>, Amount> child : node.getChildren()) {
			dump(indent + 1, child);
		}
	}
	*/
}
