package net.osdn.aoiro.report;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.chrono.JapaneseChronology;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.osdn.aoiro.AccountSettlement;
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
	
	private Node<Entry<List<AccountTitle>, Amount>> plRoot;
	private List<JournalEntry> journalEntries;
	private Set<String> signReversedNames;
	private Set<String> alwaysShownNames;
	private Set<String> hiddenNamesIfZero;
	private LocalDate openingDate;
	private LocalDate closingDate;
	private Map<AccountTitle, Amount> incomeSummaries = new HashMap<AccountTitle, Amount>(); 
	private List<Node<Entry<List<AccountTitle>, Amount>>> list;
	private List<Entry<String, Amount[]>> monthlyTotals;
	private List<String> pageData = new ArrayList<String>();
	private List<String> printData;
	
	public ProfitAndLoss(Node<Entry<List<AccountTitle>, Amount>> plRoot, List<JournalEntry> journalEntries, boolean isSoloProprietorship, Set<String> signReversedNames, Set<String> alwaysShownNames, Set<String> hiddenNamesIfZero) throws IOException {
		this.plRoot = plRoot;
		this.journalEntries = journalEntries;
		this.signReversedNames = signReversedNames != null ? signReversedNames : new HashSet<String>();
		this.alwaysShownNames = alwaysShownNames != null ? alwaysShownNames : new HashSet<String>();
		this.hiddenNamesIfZero = hiddenNamesIfZero != null ? hiddenNamesIfZero : new HashSet<String>();
		
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
		list = createList(plRoot);
		//list = getList(plRoot);

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
	
	private Amount retrieve(Node<Entry<List<AccountTitle>, Amount>> node, List<JournalEntry> journalEntries) {
		Amount amount = null;
		for(Node<Entry<List<AccountTitle>, Amount>> child : node.getChildren()) {
			Amount a = retrieve(child, journalEntries);
			if(a != null) {
				if(amount == null) {
					amount = new Amount(a.getNormalBalance(), a.getValue());
				} else {
					amount.increase(a);
				}
			}
		}
		if(node.getValue().getKey() != null) {
			for(AccountTitle accountTitle : node.getValue().getKey()) {
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
		node.getValue().setValue(amount);
		return amount;
	}
	
	protected List<Node<Entry<List<AccountTitle>, Amount>>> createList(Node<Entry<List<AccountTitle>, Amount>> plRoot) {
		List<Node<Entry<List<AccountTitle>, Amount>>> list = new ArrayList<Node<Entry<List<AccountTitle>, Amount>>>();
		
		Amount cumulativeAmount = new Amount(Creditor.class, 0);
		for(Node<Entry<List<AccountTitle>, Amount>> topLevelNode : plRoot.getChildren()) {
			cumulativeAmount.increase(topLevelNode.getValue().getValue());
			topLevelNode.getValue().setValue(cumulativeAmount.clone());
			topLevelNode.setSubTotal(true);
			for(Node<Entry<List<AccountTitle>, Amount>> childNode : topLevelNode.getChildren()) {
				list.addAll(getSubList(childNode));
			}
			list.add(topLevelNode);
		}
		return list;
	}
	
	protected List<Node<Entry<List<AccountTitle>, Amount>>> getSubList(Node<Entry<List<AccountTitle>, Amount>> node) {
		List<Node<Entry<List<AccountTitle>, Amount>>> list = new ArrayList<Node<Entry<List<AccountTitle>, Amount>>>();
		list.add(node);
		for(Node<Entry<List<AccountTitle>, Amount>> child : node.getChildren()) {
			list.addAll(getSubList(child));
		}
		return list;
	}
	
	//月別集計
	protected List<Entry<String, Amount[]>> getMonthlyTotals(List<JournalEntry> journalEntries) {
		Map<String, Amount[]> map = new LinkedHashMap<String, Amount[]>();
		YearMonth ym = YearMonth.from(this.openingDate);
		for(int i = 0; i < 12; i++) {
			String month = ym.plusMonths(i).getMonthValue() + "月";
			map.put(month, new Amount[2]);
		}
		map.put("家事消費等", new Amount[2]);
		map.put("雑収入", new Amount[2]);

		for(JournalEntry entry : journalEntries) {
			//開始仕訳と締切仕訳は集計に含めません。
			if(entry.isOpening() || entry.isClosing()) {
				continue;
			}
			String month = entry.getDate().getMonthValue() + "月";
			
			for(Debtor debtor : entry.getDebtors()) {
				String displayName = debtor.getAccountTitle().getDisplayName();
				if(displayName.equals("売上")) {
					Amount[] amounts = map.get(month);
					if(amounts[0] == null) {
						amounts[0] = new Amount(Creditor.class, 0);
					}
					amounts[0].decrease(debtor.getAmount());
					map.put(month, amounts);
				} else if(displayName.equals("家事消費等") || displayName.equals("雑収入")) {
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
				} else if(displayName.equals("家事消費等") || displayName.equals("雑収入")) {
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
		
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("GGGG y 年 M 月 d 日").withChronology(JapaneseChronology.INSTANCE);
		String openingDate = dtf.format(this.openingDate).replace(" 1 年", "元年");
		String closingDate = dtf.format(this.closingDate).replace(" 1 年", "元年");
		
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
		for(int i = 0; i < list.size(); i++) {
			Node<Entry<List<AccountTitle>, Amount>> node = list.get(i);

			Amount amount = node.getValue().getValue();
			//対象の仕訳が存在しない科目は印字をスキップします。（ただし、常に表示する見出しに含まれていない場合に限る。）
			if(amount == null && !alwaysShownNames.contains(node.getName())) {
				continue;
			}
			//ゼロなら表示しない見出しに含まれていて、ゼロの場合、印字をスキップします。
			if((amount == null || amount.getValue() == 0) && hiddenNamesIfZero.contains(node.getName())) {
				continue;
			}

			if(i >= 1) {
				if(node.isSubTotal()) {
					printData.add("\t\\line-style thin solid");
				} else {
					printData.add("\t\\line-style thin dot");
				}
				printData.add("\t\\line " + String.format("0 %.2f 95 %.2f", y, y));
			}
			
			if(node.getLevel() == 0 || (plRoot.getChildren().size() == 1 && node.getLevel() == 1)) {
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
				int sign = signReversedNames.contains(node.getName()) ? -1 : 1;
				printData.add("\t\t\\text " + formatMoney(sign * amount.getValue()));
			}
			y += ROW_HEIGHT;
		}
		printData.add("\t\\line-style thin solid");
		if(y > 0) {
			printData.add("\t\\line " + String.format("0 %.2f 95 %.2f", y - ROW_HEIGHT, y - ROW_HEIGHT));
			printData.add("\t\\line " + String.format("0 %.2f 95 %.2f", y, y));
			printData.add("\t\\line " + String.format("0 %.2f 95 %.2f", y + 0.4, y + 0.4));
		}
		printData.add("\t\\box 0 0 -0 -0");
		printData.add("\t\\line " + String.format("63 31.2 63 %.2f", 37 + y));
		printData.add("\t\\line-style thin dot");
		printData.add("\t\\line " + String.format(" 0 30.8  0 %.2f", 37 + y + 0.4));
		printData.add("\t\\line " + String.format("95 30.8 95 %.2f", 37 + y + 0.4));
		printData.add("\t\\box 0 37 -0 -0");
		
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

		PdfBrewer brewer = new PdfBrewer();
		BrewerData pb = new BrewerData(printData, brewer.getFontLoader());
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
