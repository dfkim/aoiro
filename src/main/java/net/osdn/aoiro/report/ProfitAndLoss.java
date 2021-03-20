package net.osdn.aoiro.report;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
import net.osdn.aoiro.Util;
import net.osdn.aoiro.model.AccountTitle;
import net.osdn.aoiro.model.Amount;
import net.osdn.aoiro.model.Creditor;
import net.osdn.aoiro.model.Debtor;
import net.osdn.aoiro.model.JournalEntry;
import net.osdn.aoiro.model.Node;
import net.osdn.aoiro.report.layout.ProfitAndLossLayout;
import net.osdn.pdf_brewer.BrewerData;
import net.osdn.pdf_brewer.FontLoader;
import net.osdn.pdf_brewer.PdfBrewer;

/** 損益計算書(P/L)
 *
 */
public class ProfitAndLoss {
	
	public static String MINUS_SIGN = "△";
	private static final int ROWS = 40;
	private static final double ROW_HEIGHT = 6.0;
	
	private ProfitAndLossLayout plLayout;
	private List<JournalEntry> journalEntries;
	private boolean isSoloProprietorship;

	private LocalDate openingDate;
	private LocalDate closingDate;
	private Map<AccountTitle, Amount> incomeSummaries = new HashMap<>();
	private List<Node<Entry<List<AccountTitle>, Amount>>> list;
	private List<Entry<String, Amount[]>> monthlyTotals;
	private List<String> pageData = new ArrayList<>();
	private List<String> printData;
	private FontLoader fontLoader;
	private boolean bindingMarginEnabled = true;

	public ProfitAndLoss(ProfitAndLossLayout plLayout, List<JournalEntry> journalEntries, boolean isSoloProprietorship) throws IOException {
		this.plLayout = plLayout;
		this.journalEntries = journalEntries;
		this.isSoloProprietorship = isSoloProprietorship;

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
		retrieve(plLayout.getRoot());
		
		//リスト作成
		list = createList(plLayout.getRoot());
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

	public List<JournalEntry> getJournalEntries() {
		return journalEntries;
	}

	public Map<AccountTitle, Amount> getIncomeSummaries() {
		return incomeSummaries;
	}

	// PDF出力に使用するリストデータです。
	// 損益計算書を画面に表示するなどPDF出力とは別の用途で使用するのに役立ちます。
	public List<Node<Entry<List<AccountTitle>, Amount>>> getList() {
		return list;
	}

	// PDF出力に使用する月別集計データです。
	// 損益計算書を画面に表示するなどPDF出力とは別の用途で使用するのに役立ちます。
	public List<Entry<String, Amount[]>> getMonthlyTotals() {
		return monthlyTotals;
	}

	private Amount retrieve(Node<Entry<List<AccountTitle>, Amount>> node) {
		Amount amount = null;
		for(Node<Entry<List<AccountTitle>, Amount>> child : node.getChildren()) {
			Amount a = retrieve(child);
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
		List<Node<Entry<List<AccountTitle>, Amount>>> list = new ArrayList<>();
		
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
		List<Node<Entry<List<AccountTitle>, Amount>>> list = new ArrayList<>();
		list.add(node);
		for(Node<Entry<List<AccountTitle>, Amount>> child : node.getChildren()) {
			list.addAll(getSubList(child));
		}
		return list;
	}

	/** 損益計算書の指定した勘定科目名と同じグループにある勘定科目のセットを返します。
	 *
	 * @param node 開始ノード
	 * @return 指定した勘定科目と同じグループにある勘定科目のセット
	 */
	protected Set<AccountTitle> getGroupAccounts(Node<Entry<List<AccountTitle>, Amount>> node, String displayName) {
		Set<AccountTitle> result = new HashSet<>();

		for(Node<Entry<List<AccountTitle>, Amount>> child : node.getChildren()) {
			Set<AccountTitle> s = getGroupAccounts(child, displayName);
			if(s.size() > 0) {
				result.addAll(s);
			}
		}

		if(node.getValue().getKey() != null) {
			boolean containsSales = false;
			for(AccountTitle accountTitle : node.getValue().getKey()) {
				if(accountTitle.getDisplayName().equals(displayName)) {
					containsSales = true;
					break;
				}
			}
			if(containsSales) {
				result.addAll(node.getValue().getKey());
			}
		}

		return result;
	}

	//月別集計
	protected List<Entry<String, Amount[]>> getMonthlyTotals(List<JournalEntry> journalEntries) {
		Map<String, Amount[]> map = new LinkedHashMap<>();
		if(this.openingDate != null) {
			if(isSoloProprietorship) {
				// 個人の場合は仕訳の開始日に関わらず 1月～12月の順番で表示します。
				for(int i = 0; i < 12; i++) {
					String month = (i + 1) + "月";
					map.put(month, new Amount[2]);
				}
			} else {
				// 法人の場合は仕訳の開始日の月から順番に12ヶ月を表示します。（翌年同月の前の月までになります。）
				YearMonth ym = YearMonth.from(this.openingDate);
				for(int i = 0; i < 12; i++) {
					String month = ym.plusMonths(i).getMonthValue() + "月";
					map.put(month, new Amount[2]);
				}
			}
		}
		if(isSoloProprietorship) {
			map.put("家事消費等", new Amount[2]);
		}
		map.put("雑収入", new Amount[2]);

		Set<AccountTitle> salesAccounts = getGroupAccounts(plLayout.getRoot(), "売上");
		Set<AccountTitle> purchaseAccounts = getGroupAccounts(plLayout.getRoot(), "仕入");

		for(JournalEntry entry : journalEntries) {
			//開始仕訳と締切仕訳は集計に含めません。
			if(entry.isOpening(isSoloProprietorship, openingDate) || entry.isClosing()) {
				continue;
			}
			String month = entry.getDate().getMonthValue() + "月";
			
			for(Debtor debtor : entry.getDebtors()) {
				if(salesAccounts.contains(debtor.getAccountTitle())) {
					String displayName = debtor.getAccountTitle().getDisplayName();
					if(displayName.equals("家事消費等") || displayName.equals("雑収入")) {
						// 家事消費等または雑収入
						Amount[] amounts = map.get(displayName);
						if(amounts != null) { // 法人の場合は「家事消費等」がないためamountsがnullになる可能性があります。
							if(amounts[0] == null) {
								amounts[0] = new Amount(Creditor.class, 0);
							}
							amounts[0].decrease(debtor.getAmount());
						}
					} else {
						// 月別の売上および同じグループの勘定科目
						Amount[] amounts = map.get(month);
						if(amounts[0] == null) {
							amounts[0] = new Amount(Creditor.class, 0);
						}
						amounts[0].decrease(debtor.getAmount());
					}
				} else if(purchaseAccounts.contains(debtor.getAccountTitle())) {
					// 月別の仕入および同じグループの勘定科目
					Amount[] amounts = map.get(month);
					if(amounts[1] == null) {
						amounts[1] = new Amount(Debtor.class, 0);
					}
					amounts[1].increase(debtor.getAmount());
				}
			}
			for(Creditor creditor : entry.getCreditors()) {
				if(salesAccounts.contains(creditor.getAccountTitle())) {
					String displayName = creditor.getAccountTitle().getDisplayName();
					if(displayName.equals("家事消費等") || displayName.equals("雑収入")) {
						// 家事消費等または雑収入
						Amount[] amounts = map.get(displayName);
						if (amounts != null) { // 法人の場合は「家事消費等」がないためamountsがnullになる可能性があります。
							if (amounts[0] == null) {
								amounts[0] = new Amount(Creditor.class, 0);
							}
							amounts[0].increase(creditor.getAmount());
						}
					} else {
						// 月別の売上および同じグループの勘定科目
						Amount[] amounts = map.get(month);
						if(amounts[0] == null) {
							amounts[0] = new Amount(Creditor.class, 0);
						}
						amounts[0].increase(creditor.getAmount());
					}
				} else if(purchaseAccounts.contains(creditor.getAccountTitle())) {
					// 月別の仕入および同じグループの勘定科目
					Amount[] amounts = map.get(month);
					if(amounts[1] == null) {
						amounts[1] = new Amount(Debtor.class, 0);
					}
					amounts[1].decrease(creditor.getAmount());
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
		if(bindingMarginEnabled) {
			printData.add("\\media A4");

			//穴あけパンチの位置を合わせるための中心線を先頭ページのみ印字します。
			printData.add("\\line-style thin dot");
			printData.add("\\line 0 148.5 5 148.5");

			printData.add("\\box 15 0 0 0");
			printData.add("\\line-style thin dot");
			printData.add("\\line 0 0 0 -0");
			printData.add("\\box 25 0 -10 -10");
		} else {
			// 綴じ代なしの場合は15mm分だけ横幅を短くして195mmとします。(A4本来の横幅は210mm)
			// 穴あけパンチ用の中心線も出力しません。
			printData.add("\\media 195 297");

			printData.add("\\box 10 0 -10 -10");
		}

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
			if(amount == null && !plLayout.isAlwaysShown(node.getName())) {
				continue;
			}
			//ゼロなら表示しない見出しに含まれていて、ゼロの場合、印字をスキップします。
			if((amount == null || amount.getValue() == 0) && plLayout.isHidden(node.getName())) {
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

			// ノードのレベル 0 または、レベル 0 のノード数が 1つの場合は ノードレベル 1 もボールド表示にします。
			// 個人の損益計算書のレイアウトはトップレベル（レベル 0）のノード数が 1つになります。
			// レベル 0 が「青色申告特別控除前の所得金額」で、レベル 1　に「売上金額（雑収入を含む）」「売上原価」「経費」が並びます。
			// これらをボールド表示にすることが目的です。
			// 法人の損益計算書のレイアウトはトップレベル（レベル 0）のノードが複数あります。
			// 「売上総利益」「営業利益」「経常利益」「税引前当期純利益」「当期純利益」です。
			// これらもボールド表示にします。
			if(node.getLevel() == 0 || (plLayout.getRoot().getChildren().size() == 1 && node.getLevel() == 1)) {
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
				int sign = plLayout.isSignReversed(node.getName()) ? -1 : 1;
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
		printData.add("\t\\line " + String.format(" 0 31.2  0 %.2f", 37 + y));
		printData.add("\t\\line " + String.format("95 31.2 95 %.2f", 37 + y));
		printData.add("\t\\box 0 37 -0 -0");
		
		//月別
		Amount salesTotal = null;
		Amount purchaseTotal = null;

		//月別売上に出現するもっとも大きな金額を求めます。後の工程で最大金額の桁数に応じて表示位置を調整します。
		long maxAmount = 0;
		{
			long cTotal = 0;
			long dTotal = 0;
			for(Entry<String, Amount[]> e : monthlyTotals) {
				Amount[] amounts = e.getValue();
				if(amounts[0] != null) {
					cTotal += amounts[0].getValue();
					long v = Math.abs(amounts[0].getValue());
					if(v > maxAmount) {
						maxAmount = v;
					}
				}
				if(amounts[1] != null) {
					dTotal += amounts[1].getValue();
					long v = Math.abs(amounts[1].getValue());
					if(v > maxAmount) {
						maxAmount = v;
					}
				}
			}
			if(cTotal > maxAmount) {
				maxAmount = cTotal;
			}
			if(dTotal > maxAmount) {
				maxAmount = dTotal;
			}
		}
		// 月別の金額印字幅。通常は21mm。最大金額が
		// 99999999999（11桁）を超える場合は26mm、9999999999（10桁）を超える場合は25mm、
		// 999999999（9桁）を超える場合は24mm、99999999（8桁）を超える場合は22mm にします。
		double amountPrintWidth = 21;
		if(maxAmount > 99999999999L) {
			amountPrintWidth = 26;
		} else if(maxAmount > 9999999999L) {
			amountPrintWidth = 25;
		} else if(maxAmount > 999999999L) {
			amountPrintWidth = 24;
		} else if(maxAmount > 99999999L) {
			amountPrintWidth = 22;
		}

		y = 0.0;
		printData.add("\t\\box 0 37 -0 -0");
		printData.add("\t\t\\font serif 10");
		printData.add("\t\t\\line-style thin dot");
		for(Entry<String, Amount[]> e : monthlyTotals) {
			String displayName = e.getKey();
			Amount[] amounts = e.getValue();

			if(y > 0.0) {
				printData.add("\t\t\\line " + String.format("105 %.2f -0 %.2f", y, y));
			}

			if(displayName.endsWith("月")) {
				printData.add("\t\t\t\\box " + String.format("105 %.2f 13.5 %.2f", y, ROW_HEIGHT));
				printData.add("\t\t\t\\align center right");
			} else {
				printData.add("\t\t\\line " + String.format("-0 %.2f 150 %.2f", y + 0.15, y + ROW_HEIGHT - 0.15));
				printData.add("\t\t\t\\box " + String.format("105 %.2f 20 %.2f", y, ROW_HEIGHT));
				printData.add("\t\t\t\\align center");
			}
			printData.add("\t\t\t\\text " + displayName);
			
			if(amounts[0] != null) {
				printData.add("\t\t\t\\box " + String.format("125 %.2f %.2f %.2f", y, amountPrintWidth, ROW_HEIGHT));
				printData.add("\t\t\t\\align center right");
				printData.add("\t\t\t\\text " + formatMoney(amounts[0].getValue()));
				if(salesTotal == null) {
					salesTotal = new Amount(Creditor.class, 0);
				}
				salesTotal.increase(amounts[0].getValue());
			}
			if(amounts[1] != null) {
				printData.add("\t\t\t\\box " + String.format("150 %.2f %.2f %.2f", y, amountPrintWidth, ROW_HEIGHT));
				printData.add("\t\t\t\\align center right");
				printData.add("\t\t\t\\text " + formatMoney(amounts[1].getValue()));
				if(purchaseTotal == null) {
					purchaseTotal = new Amount(Debtor.class, 0);
				}
				purchaseTotal.increase(amounts[1].getValue());
			}
			y += ROW_HEIGHT;
		}
		printData.add("\t\t\\line-style thin solid");
		printData.add("\t\t\\line " + String.format("105 %.2f -0 %.2f", y, y));
		printData.add("\t\t\\line " + String.format("105 %.2f -0 %.2f", y + ROW_HEIGHT, y + ROW_HEIGHT));
		printData.add("\t\t\\line " + String.format("105 %.2f -0 %.2f", y + ROW_HEIGHT + 0.4, y + ROW_HEIGHT + 0.4));
		printData.add("\t\t\\font serif 10 bold");
		printData.add("\t\t\t\\box " + String.format("105 %.2f 20 %.2f", y, ROW_HEIGHT));
		printData.add("\t\t\t\\align center");
		printData.add("\t\t\t\\text 計");
		if(salesTotal != null) {
			printData.add("\t\t\t\\box " + String.format("125 %.2f %.2f %.2f", y, amountPrintWidth, ROW_HEIGHT));
			printData.add("\t\t\t\\align center right");
			printData.add("\t\t\t\\text " + formatMoney(salesTotal.getValue()));
		}
		if(purchaseTotal != null) {
			printData.add("\t\t\t\\box " + String.format("150 %.2f %.2f %.2f", y, amountPrintWidth, ROW_HEIGHT));
			printData.add("\t\t\t\\align center right");
			printData.add("\t\t\t\\text " + formatMoney(purchaseTotal.getValue()));
		}
		printData.add("\t\\box 0 31 -0 -0");
		printData.add("\t\t\\line-style thin dot");
		printData.add("\t\t\\line " + String.format("105 0.2 105 %.2f", y + ROW_HEIGHT + ROW_HEIGHT));
		printData.add("\t\t\\line " + String.format(" -0 0.2  -0 %.2f", y + ROW_HEIGHT + ROW_HEIGHT));
		printData.add("\t\t\\line-style thin solid");
		printData.add("\t\t\\line " + String.format("126 0.2 126 %.2f", y + ROW_HEIGHT + ROW_HEIGHT));
		printData.add("\t\t\\line " + String.format("150 0.2 150 %.2f", y + ROW_HEIGHT + ROW_HEIGHT));
	}

	public void setFontLoader(FontLoader fontLoader) {
		this.fontLoader = fontLoader;
	}

	public void setBindingMarginEnabled(boolean enabled) {
		this.bindingMarginEnabled = enabled;
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
		brewer.setTitle("損益計算書");
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
		brewer.setTitle("損益計算書");
		brewer.process(pb);
		brewer.save(out);
		brewer.close();
	}

	private static String formatMoney(long amount) {
		if(MINUS_SIGN != null && amount < 0) {
			return MINUS_SIGN + String.format("%,d", -amount);
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
