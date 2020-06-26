package net.osdn.aoiro.report;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.chrono.JapaneseChronology;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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
import net.osdn.aoiro.model.AccountType;
import net.osdn.aoiro.model.Amount;
import net.osdn.aoiro.model.Creditor;
import net.osdn.aoiro.model.Debtor;
import net.osdn.aoiro.model.JournalEntry;
import net.osdn.aoiro.model.Node;
import net.osdn.aoiro.report.layout.StatementOfChangesInEquityLayout;
import net.osdn.pdf_brewer.BrewerData;
import net.osdn.pdf_brewer.PdfBrewer;

public class StatementOfChangesInEquity {
	
	public static String MINUS_SIGN = "△";
	private static final double HEADER_TITLE_WIDTH = 140.0;
	private static final double ROW_HEIGHT = 6.0;
	
	private class Rectangle {
		public int    columnIndex;
		public double x;
		public double y;
		public double width;
		public double height;
	}

	private StatementOfChangesInEquityLayout sceLayout;

	private LocalDate openingDate;
	private LocalDate closingDate;
	private Map<AccountTitle, Amount> openingBalances = new HashMap<>();
	private Map<AccountTitle, Amount> closingBalances = new HashMap<>();
	private Map<String, Map<AccountTitle, Amount>> changes = new LinkedHashMap<>();

	private int headerColumns;
	private int headerRows;
	private Map<Node<List<AccountTitle>>, Rectangle> headerRects = new LinkedHashMap<>();
	private List<Node<Amount[]>> rows = new ArrayList<>();
	
	private List<String> pageData = new ArrayList<>();
	private List<String> printData;
	
	public StatementOfChangesInEquity(StatementOfChangesInEquityLayout sceLayout, List<JournalEntry> journalEntries) throws IOException {
		this.sceLayout = sceLayout;

		this.openingDate = AccountSettlement.getOpeningDate(journalEntries, false);
		this.closingDate = AccountSettlement.getClosingDate(journalEntries, false);
		
		InputStream in = getClass().getResourceAsStream("/templates/社員資本等変動計算書.pb");
		BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		String line;
		while((line = r.readLine()) != null) {
			pageData.add(line);
		}
		r.close();
		
		for(JournalEntry entry : journalEntries) {
			if(entry.isOpening()) { //当期首残高
				for(Debtor debtor : entry.getDebtors()) {
					if(debtor.getAccountTitle().getType() == AccountType.Equity) {
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
				}
				for(Creditor creditor : entry.getCreditors()) {
					if(creditor.getAccountTitle().getType() == AccountType.Equity) {
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
			} else if(entry.isBalance()) { //当期末残高
				for(Debtor debtor : entry.getDebtors()) {
					if(debtor.getAccountTitle().getType() == AccountType.Equity) {
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
					if(creditor.getAccountTitle().getType() == AccountType.Equity) {
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
			} else { //当期変動額
				for(Debtor debtor : entry.getDebtors()) {
					if(debtor.getAccountTitle().getType() == AccountType.Equity) {
						Map<AccountTitle, Amount> map = changes.get(entry.getDescription());
						if(map == null) {
							map = new HashMap<AccountTitle, Amount>();
							changes.put(entry.getDescription(), map);
						}
						Amount amount = map.get(debtor.getAccountTitle());
						if(amount == null) {
							amount = new Amount(debtor.getAccountTitle().getType().getNormalBalance(), 0);
							map.put(debtor.getAccountTitle(), amount);
						}
						if(debtor.getAccountTitle().getType().getNormalBalance() == Debtor.class) {
							amount.increase(debtor.getAmount());
						} else {
							amount.decrease(debtor.getAmount());
						}
					}
				}
				for(Creditor creditor : entry.getCreditors()) {
					if(creditor.getAccountTitle().getType() == AccountType.Equity) {
						Map<AccountTitle, Amount> map = changes.get(entry.getDescription());
						if(map == null) {
							map = new HashMap<AccountTitle, Amount>();
							changes.put(entry.getDescription(), map);
						}
						Amount amount = map.get(creditor.getAccountTitle());
						if(amount == null) {
							amount = new Amount(creditor.getAccountTitle().getType().getNormalBalance(), 0);
							map.put(creditor.getAccountTitle(), amount);
						}
						if(creditor.getAccountTitle().getType().getNormalBalance() == Creditor.class) {
							amount.increase(creditor.getAmount());
						} else {
							amount.decrease(creditor.getAmount());
						}
					}
				}
			}
		}

		{ //変動事由に含まれていない摘要が存在した場合は変動事由に追加します。
			Set<String> descriptions = new HashSet<>();
			for(List<String> list : sceLayout.getReasons().values()) {
				if(list != null) {
					for(String s : list) {
						descriptions.add(s);
					}
				}
			}
			for(String description : changes.keySet()) {
				if(!descriptions.contains(description)) {
					sceLayout.getReasons().put(description, Arrays.asList(description));
				}
			}
		}

		// ヘッダー領域を計算します。
		calculateHeaderRects(sceLayout.getRoot());
		
		// 当期首残高
		Node<Amount[]> openingRow = new Node<>(0, "当期首残高");
		openingRow.setValue(new Amount[headerColumns]);
		rows.add(openingRow);
		for(Entry<AccountTitle, Amount> e : openingBalances.entrySet()) {
			for(int columnIndex : getColumnIndexes(e.getKey())) {
				Amount amount = openingRow.getValue()[columnIndex];
				if(amount == null) {
					amount = new Amount(Creditor.class, 0);
					openingRow.getValue()[columnIndex] = amount;
				}
				amount.increase(e.getValue());
			}
		}
		
		// 当期変動額合計
		Node<Amount[]> totalChangesRow = new Node<>(0, "当期変動額合計");
		totalChangesRow.setValue(new Amount[headerColumns]);
		rows.add(totalChangesRow);
		for(Entry<String, List<String>> ey : sceLayout.getReasons().entrySet()) {
			Node<Amount[]> changeRow = new Node<>(1, "　" + ey.getKey());
			changeRow.setValue(new Amount[headerColumns]);
			rows.add(changeRow);
			if(ey.getValue() != null) {
				for(String description : ey.getValue()) {
					Map<AccountTitle, Amount> map = changes.get(description);
					if(map != null) {
						for(Entry<AccountTitle, Amount> ex : map.entrySet()) {
							for(int columnIndex : getColumnIndexes(ex.getKey())) {
								{
									Amount amount = changeRow.getValue()[columnIndex];
									if(amount == null) {
										amount = new Amount(Creditor.class, 0);
										changeRow.getValue()[columnIndex] = amount;
									}
									amount.increase(ex.getValue());
								}
								{
									Amount amount = totalChangesRow.getValue()[columnIndex];
									if(amount == null) {
										amount = new Amount(Creditor.class, 0);
										totalChangesRow.getValue()[columnIndex] = amount;
									}
									amount.increase(ex.getValue());
								}
							}
						}
					}
				}
			}
		}
		
		// 当期末残高
		Node<Amount[]> closingRow = new Node<>(0, "当期末残高");
		closingRow.setValue(new Amount[headerColumns]);
		rows.add(closingRow);
		for(Entry<AccountTitle, Amount> e : closingBalances.entrySet()) {
			for(int columnIndex : getColumnIndexes(e.getKey())) {
				Amount amount = closingRow.getValue()[columnIndex];
				if(amount == null) {
					amount = new Amount(Creditor.class, 0);
					closingRow.getValue()[columnIndex] = amount;
				}
				amount.increase(e.getValue());
			}
		}
	}

	protected List<Integer> getColumnIndexes(AccountTitle accountTitle) {
		List<Integer> columnIndexes = new ArrayList<>();
		
		for(Entry<Node<List<AccountTitle>>, Rectangle> e : this.headerRects.entrySet()) {
			for(AccountTitle title : e.getKey().getValue()) {
				if(title.equals(accountTitle)) {
					columnIndexes.add(e.getValue().columnIndex);
				}
			}
		}
		return columnIndexes;
	}
	
	private void calculateHeaderRects(Node<List<AccountTitle>> ceRoot) {
		headerColumns = getHeaderColumns(ceRoot);
		headerRows = getHeaderRows(ceRoot);
		retrieveHeaderRects(headerColumns, headerRows, ceRoot, 0);
		List<Map.Entry<Node<List<AccountTitle>>, Rectangle>> entries = new ArrayList<>(this.headerRects.entrySet());
		Collections.sort(entries, new Comparator<Map.Entry<Node<List<AccountTitle>>, Rectangle>>() {
			@Override
			public int compare(Entry<Node<List<AccountTitle>>, Rectangle> o1, Entry<Node<List<AccountTitle>>, Rectangle> o2) {
				int diffColumnIndex = o1.getValue().columnIndex - o2.getValue().columnIndex;
				if(diffColumnIndex == 0) {
					return (o1.getKey().getLevel() - o2.getKey().getLevel());
				} else {
					return diffColumnIndex;
				}
			}
		});
		this.headerRects.clear();
		for(Map.Entry<Node<List<AccountTitle>>, Rectangle> entry : entries) {
			this.headerRects.put(entry.getKey(), entry.getValue());
		}
	}

	private int getHeaderColumns(Node<List<AccountTitle>> node) {
		if(node.getChildren().isEmpty()) {
			return 1;
		} else {
			int columns = 0;
			for(Node<List<AccountTitle>> child : node.getChildren()) {
				columns += getHeaderColumns(child);
			}
			return columns;
		}
	}
	
	private int getHeaderRows(Node<List<AccountTitle>> node) {
		if(node.getChildren().isEmpty()) {
			int lines = getLines(node.getName());
			int level = node.getLevel();
			return lines + level - 1;
		} else {
			int maxRows = 0;
			for(Node<List<AccountTitle>> child : node.getChildren()) {
				int rows = getHeaderRows(child);
				if(rows > maxRows) {
					maxRows = rows;
				}
			}
			return maxRows;
		}
	}
	
	private int retrieveHeaderRects(int headerColumns, int headerRows, Node<List<AccountTitle>> node, int left) {
		int width = 0;
		if(node.getChildren().isEmpty()) {
			width = 1;
		} else {
			for(Node<List<AccountTitle>> child : node.getChildren()) {
				width += retrieveHeaderRects(headerColumns, headerRows, child, left + width);
			}
		}

		if(node.getLevel() > 0) {
			Rectangle rect = new Rectangle();
			rect.columnIndex = left;
			rect.x = HEADER_TITLE_WIDTH * left / headerColumns;
			rect.y = ROW_HEIGHT * (node.getLevel() - 1);
			rect.width = HEADER_TITLE_WIDTH * width / headerColumns;
			rect.height = node.getChildren().isEmpty() ? (ROW_HEIGHT * headerRows - rect.y) : ROW_HEIGHT;
			headerRects.put(node, rect);
		}

		return width;
	}
	
	private static int getLines(String s) {
		int lines = 1;
		int fromIndex = 0;
		int i;

		while((i = s.indexOf('\n', fromIndex)) >= 0) {
			lines++;
			fromIndex = i + 1;
		}
		return lines;
	}
	
	protected void prepare() {
		double y;
		
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
		printData.add("\t\t\\font serif 10");
		printData.add("\t\t\\align center");
		printData.add("\t\t\\text " + openingDate + " ～ " + closingDate);
		
		//印字領域の設定
		printData.add("\t\\box 0 0 -0 -0");
		printData.add("\t\t\\line-style thin solid");

		//ボディ部分の横罫線
		y = 25.2 + headerRows * ROW_HEIGHT;
		printData.add("\t\t\\line " + String.format("0 %.2f -0 %.2f", y, y));
		y += ROW_HEIGHT;
		printData.add("\t\t\\line " + String.format("0 %.2f -0 %.2f", y, y));
		printData.add("\t\t\\line-style thin dot");
		for(int i = 0; i < sceLayout.getReasons().keySet().size(); i++) {
			y += ROW_HEIGHT;
			printData.add("\t\t\\line " + String.format("0 %.2f -0 %.2f", y, y));
		}
		printData.add("\t\t\\line-style thin solid");
		y += ROW_HEIGHT;
		printData.add("\t\t\\line " + String.format("0 %.2f -0 %.2f", y, y));
		y += ROW_HEIGHT;
		printData.add("\t\t\\line " + String.format("0 %.2f -0 %.2f", y, y));
		printData.add("\t\t\\line " + String.format("0 %.2f -0 %.2f", y + 0.4, y + 0.4));
		
		//ヘッダー部分
		printData.add("\t\\box " + String.format("35.0 25.2 %.2f %.2f", HEADER_TITLE_WIDTH, y - 25.2));
		Set<Double> lineX = new HashSet<>();
		for(Entry<Node<List<AccountTitle>>, Rectangle> e : this.headerRects.entrySet()) {
			Rectangle rect = e.getValue();
			Node<List<AccountTitle>> node = e.getKey();
			printData.add("\t\t\\box " + String.format("%.2f %.2f %.2f %.2f", rect.x, rect.y, rect.width, rect.height));
			if(node.getChildren().size() > 0 &&node.getName().length() > 7) {
				printData.add("\t\t\t\\font serif 7");
			} else {
				printData.add("\t\t\t\\font serif 8");
			}
			printData.add("\t\t\t\\text " + node.getName());
			printData.add("\t\t\t\\line " + String.format("0 %.2f -0 %.2f", rect.height, rect.height));
			if(!lineX.contains(rect.x)) {
				printData.add("\t\t\\box " + String.format("%.2f %.2f %.2f -0", rect.x, rect.y, rect.width));
				printData.add("\t\t\t\\line 0 0 0 -0");
				lineX.add(rect.x);
			}
		}
		
		y = 25.2 + headerRows * ROW_HEIGHT;
		for(Node<Amount[]> row : rows) {
			printData.add("\t\\box " + String.format("0 %.2f -0 -0", y));
			printData.add("\t\t\\font serif 10" + (row.getLevel() == 0 ? " bold" : ""));
			//
			printData.add("\t\t\\align center left");
			printData.add("\t\t\\box " + String.format("2 0 33.0 %.2f", ROW_HEIGHT));
			printData.add("\t\t\t\\text " + row.getName());
			//
			printData.add("\t\t\\align center right");
			Amount[] amounts = row.getValue();
			for(int i = 0; i < amounts.length; i++) {
				if(amounts[i] != null) {
					double x = 35.0 + (HEADER_TITLE_WIDTH * i / amounts.length);
					double w = (HEADER_TITLE_WIDTH / amounts.length) - 2.0;
					printData.add("\t\t\\box " + String.format("%.2f 0 %.2f %.2f", x, w, ROW_HEIGHT));
					printData.add("\t\t\t\\text " + formatMoney(amounts[i].getValue()));
				}
			}
			y += ROW_HEIGHT;
		}
	}
	
	public void writeTo(Path path) throws IOException {
		prepare();

		PdfBrewer brewer = new PdfBrewer();
		brewer.setCreator(Util.getPdfCreator());
		BrewerData pb = new BrewerData(printData, brewer.getFontLoader());
		brewer.setTitle("社員資本等変動計算書");
		brewer.process(pb);
		brewer.save(path);
	}
	
	private static String formatMoney(long amount) {
		if(MINUS_SIGN != null && amount < 0) {
			return MINUS_SIGN + String.format("%,d", -amount);
		}
		return String.format("%,d", amount);
	}
}
