package net.osdn.aoiro.report;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import net.osdn.aoiro.AccountSettlement;
import net.osdn.aoiro.model.Creditor;
import net.osdn.aoiro.model.Debtor;
import net.osdn.aoiro.model.JournalEntry;
import net.osdn.pdf_brewer.BrewerData;
import net.osdn.pdf_brewer.PdfBrewer;

/** 仕訳帳
 * 
 */
public class GeneralJournal {
	
	private static final int ROWS = 50;
	private static final double ROW_HEIGHT = 5.0;
	
	private List<JournalEntry> entries;
	int financialYear;
	boolean isFromNewYearsDay;
	
	private List<String> pageData = new ArrayList<String>();
	private List<String> printData;
	
	public GeneralJournal(List<JournalEntry> journalEntries, boolean isSoloProprietorship) throws IOException {
		this.entries = journalEntries;
		
		InputStream in = getClass().getResourceAsStream("/templates/仕訳帳.pb");
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
		
		//総勘定元帳と相互にページ番号を印字するために
		//writeToを呼び出してPDFを作成する前にprepareを呼び出しておく必要があります。
		prepare();
	}
	
	protected void prepare() {
		int pageNumber = 0;
		int restOfRows = 0;
		int currentRow = 0;
		int debtorTotal = 0;
		int creditorTotal = 0;

		printData = new ArrayList<String>();
		printData.add("\\media A4");
		
		//穴あけパンチの位置を合わせるための中心線を先頭ページのみ印字します。
		printData.add("\\line-style thin dot");
		printData.add("\\line 0 148.5 5 148.5");
		
		for(int i = 0; i < entries.size(); i++) {
			JournalEntry entry = entries.get(i);
			int month = entry.getDate().getMonthValue();
			int day = entry.getDate().getDayOfMonth();

			//この仕訳の後で改ページが必要かどうか
			boolean isCarriedForward = false;
			
			//仕訳の印字に必要な行数を求めます。
			int rowsRequired = getRowsRequired(entry);
			//最後の明細の場合は締切行を追加するために 1行加算します。
			if(i == entries.size() - 1) {
				rowsRequired++;
			} else if(currentRow != 0) {
				if(rowsRequired <= restOfRows) {
					//最後の明細ではない場合、次の仕訳の印字必要行数を調べて改ページが発生する有無を求めます。
					JournalEntry nextEntry = entries.get(i + 1);
					int nextEntryRowsRequired = getRowsRequired(nextEntry);
					//次の仕訳の印字必要行数を含めると超過する場合は改ページが必要になります。
					if(rowsRequired + nextEntryRowsRequired + 1 > restOfRows) {
						isCarriedForward = true;
						rowsRequired++; //次頁繰越を印字するために必要行数を加算します。
					}
				}
			}
			
			//仕訳の印字に必要な行数が残り行数を超えているときに改ページします。
			if(rowsRequired > restOfRows) {
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
				
				//2ページ目以降は前頁繰越を印字します。
				if(pageNumber >= 2) {
					printData.add("\t\t\\box " + String.format("0 %.2f -0 %.2f", currentRow * ROW_HEIGHT, ROW_HEIGHT));
					printData.add("\t\t\t\\box " + String.format("16 0 75 %.2f", ROW_HEIGHT));
					printData.add("\t\t\t\\align center right");
					printData.add("\t\t\t\\font serif 10");
					printData.add("\t\t\t\\text 前頁繰越");
					printData.add("\t\t\t\\box " + String.format("101 0 32 %.2f", ROW_HEIGHT));
					printData.add("\t\t\t\\text " + String.format("%,d",  debtorTotal));
					printData.add("\t\t\t\\box " + String.format("138 0 32 %.2f", ROW_HEIGHT));
					printData.add("\t\t\t\\text " + String.format("%,d",  creditorTotal));
					currentRow++;
					restOfRows--;
				}
			}
			
			//総勘定元帳に記載する仕訳帳ページ(仕丁)を設定します。
			for(Debtor debtor : entry.getDebtors()) {
				debtor.setJournalPageNumber(pageNumber);
			}
			for(Creditor creditor : entry.getCreditors()) {
				creditor.setJournalPageNumber(pageNumber);
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
			double y = 0.0;
			//借方
			if(entry.getDebtors().size() >= 2) {
				printData.add("\t\t\t\\box " + String.format("18 0 34 %.2f", ROW_HEIGHT));
				printData.add("\t\t\t\\align center left");
				printData.add("\t\t\t\\text 諸口");
				y += ROW_HEIGHT;
			}
			for(int j = 0; j < entry.getDebtors().size(); j++) {
				Debtor debtor = entry.getDebtors().get(j);
				debtorTotal += debtor.getAmount();
				printData.add("\t\t\t\\box " + String.format("16 %.2f 77 %.2f", y, ROW_HEIGHT));
				printData.add("\t\t\t\\align center left");
				printData.add("\t\t\t\\text " + "（" + debtor.getAccountTitle().getDisplayName() + "）");
				printData.add("\t\t\t\\box " + String.format("101 %.2f 32 %.2f", y, ROW_HEIGHT));
				printData.add("\t\t\t\\align center right");
				printData.add("\t\t\t\\text " + String.format("%,d", debtor.getAmount()));
				//元丁
				if(debtor.getLedgerPageNumber() >= 0) {
					printData.add("\t\t\t\\box " + String.format("93 %.2f 8 %.2f", y, ROW_HEIGHT));
					printData.add("\t\t\t\\align center");
					printData.add("\t\t\t\\text " + debtor.getLedgerPageNumber());
				}
				y += ROW_HEIGHT;
			}
			//貸方
			if(entry.getDebtors().size() >= 2 && entry.getCreditors().size() == 1) {
				//借方が諸口で貸方が1行しかない場合は借方の諸口行に貸方を記入します。
				Creditor creditor = entry.getCreditors().get(0);
				creditorTotal += creditor.getAmount();
				printData.add("\t\t\t\\box " + String.format("16 0 77 %.2f", ROW_HEIGHT));
				printData.add("\t\t\t\\align center right");
				printData.add("\t\t\t\\text " + "（" + creditor.getAccountTitle().getDisplayName() + "）");
				printData.add("\t\t\t\\box " + String.format("138 0 32 %.2f", ROW_HEIGHT));
				printData.add("\t\t\t\\align center right");
				printData.add("\t\t\t\\text " + String.format("%,d", creditor.getAmount()));
				//元丁
				if(creditor.getLedgerPageNumber() >= 0) {
					printData.add("\t\t\t\\box " + String.format("93 0 8 %.2f", ROW_HEIGHT));
					printData.add("\t\t\t\\align center");
					printData.add("\t\t\t\\text " + creditor.getLedgerPageNumber());
				}
			} else {
				if(entry.getCreditors().size() >= 2) {
					printData.add("\t\t\t\\box " + String.format("54.5 0 36.5 %.2f", ROW_HEIGHT));
					printData.add("\t\t\t\\align center right");
					printData.add("\t\t\t\\text 諸口");
				}
				for(int j = 0; j < entry.getCreditors().size(); j++) {
					Creditor creditor = entry.getCreditors().get(j);
					creditorTotal += creditor.getAmount();
					printData.add("\t\t\t\\box " + String.format("16 %.2f 77 %.2f", y, ROW_HEIGHT));
					printData.add("\t\t\t\\align center right");
					printData.add("\t\t\t\\text " + "（" + creditor.getAccountTitle().getDisplayName() + "）");
					printData.add("\t\t\t\\box " + String.format("138 %.2f 32 %.2f", y, ROW_HEIGHT));
					printData.add("\t\t\t\\align center right");
					printData.add("\t\t\t\\text " + String.format("%,d", creditor.getAmount()));
					//元丁
					if(creditor.getLedgerPageNumber() >= 0) {
						printData.add("\t\t\t\\box " + String.format("93 %.2f 8 %.2f", y, ROW_HEIGHT));
						printData.add("\t\t\t\\align center");
						printData.add("\t\t\t\\text " + creditor.getLedgerPageNumber());
					}
					y += ROW_HEIGHT;
				}
			}
			//摘要
			printData.add("\t\t\t\\box " + String.format("18 %.2f 75 %.2f", y, ROW_HEIGHT));
			printData.add("\t\t\t\\font serif 8");
			printData.add("\t\t\t\\align center left");
			printData.add("\t\t\t\\text " + entry.getDescription());
			
			//締切線 (改ページ前および最終明細の摘要欄には締切線を引きません)
			if(!isCarriedForward && (i + 1 < entries.size())) {
				printData.add("\t\t\t\\box " + String.format("16 %.2f 77 %.2f", y, ROW_HEIGHT));
				printData.add("\t\t\t\\line-style medium solid");
				printData.add("\t\t\t\\line 0.325 -0 -0.325 -0");
			}
			
			//次頁繰越の印字が必要な場合
			if(isCarriedForward) {
				if((ROWS - currentRow - rowsRequired) * ROW_HEIGHT > 0) {
					printData.add("\t\t\t\\box " + String.format("16 %.2f 77 %.2f", y + ROW_HEIGHT, (ROWS - currentRow - rowsRequired) * ROW_HEIGHT));
					printData.add("\t\t\t\\line-style medium solid");
					printData.add("\t\t\t\\line -0.325 0.15 0.325 -0.15");
				}
				
				printData.add("\t\t\\line-style medium solid");
				printData.add("\t\t\\box " + String.format("16 %.2f 77 %.2f", (ROWS - 1) * ROW_HEIGHT, ROW_HEIGHT));
				printData.add("\t\t\\line 0.325 0 -0.325 0");
				printData.add("\t\t\\box " + String.format("101 %.2f -0 %.2f", (ROWS - 1) * ROW_HEIGHT, ROW_HEIGHT));
				printData.add("\t\t\\line 0.325 0 -0.125 0");
				printData.add("\t\t\\box " + String.format("16 %.2f 75 %.2f", (ROWS - 1) * ROW_HEIGHT, ROW_HEIGHT));
				printData.add("\t\t\\align center right");
				printData.add("\t\t\\font serif 10");
				printData.add("\t\t\\text 次頁繰越");
				printData.add("\t\t\\box " + String.format("101 %.2f 32 %.2f", (ROWS - 1) * ROW_HEIGHT, ROW_HEIGHT));
				printData.add("\t\t\\text " + String.format("%,d", debtorTotal));
				printData.add("\t\t\\box " + String.format("138 %.2f 32 %.2f", (ROWS - 1) * ROW_HEIGHT, ROW_HEIGHT));
				printData.add("\t\t\\text " + String.format("%,d", creditorTotal));
			}

			currentRow += rowsRequired;
			restOfRows -= rowsRequired;

			//期末締切線
			if(i == entries.size() - 1) {
				printData.add("\t\t\\box " + String.format("0 %.2f -0 %.2f", (currentRow - 1) * ROW_HEIGHT, ROW_HEIGHT + 0.5));
				printData.add("\t\t\\line-style medium solid");
				printData.add("\t\t\\line 101.325 0 -0.125 0");
				printData.add("\t\t\\line 0.125 -0.5 15.675 -0.5");
				printData.add("\t\t\\line 0.125 -0.0 15.675 -0.0");
				printData.add("\t\t\\line 101.325 -0.5 -0.125 -0.5");
				printData.add("\t\t\\line 101.325 -0.0 -0.125 -0.0");
				printData.add("\t\t\t\\align center right");
				printData.add("\t\t\t\\font serif 10");
				printData.add("\t\t\t\\box " + String.format("101 0 32 %.2f", ROW_HEIGHT));
				printData.add("\t\t\t\\text " + String.format("%,d", debtorTotal));
				printData.add("\t\t\t\\box " + String.format("138 0 32 %.2f", ROW_HEIGHT));
				printData.add("\t\t\t\\text " + String.format("%,d", creditorTotal));
			}
		}
	}

	/** 指定した仕訳を印字するのに必要な行数を取得します。
	 * 
	 * @param entry 仕訳
	 * @return 指定した仕訳を印字するのに必要な行数を返します。
	 */
	public int getRowsRequired(JournalEntry entry) {
		int rowsRequired = 0;
		//借方が2つ以上ある場合は「諸口」のために1行加算します。
		if(entry.getDebtors().size() >= 2) {
			rowsRequired++;
		}
		//借方件数を加算します。
		rowsRequired += entry.getDebtors().size();
		//借方が2つ以上かつ貸方も2つ以上ある場合は貸方件数を加算します。
		if(entry.getDebtors().size() >= 2 && entry.getCreditors().size() == 1) {
			//借方が2つ以上で貸方が1つの場合は借方の「諸口」行に貸方を印字するので行を加算する必要がありません。
		} else {
			rowsRequired += entry.getCreditors().size();
		}
		//摘要のために1行加算します。
		rowsRequired++;
		
		return rowsRequired;
	}
	
	public void writeTo(File file) throws IOException {
		prepare();

		PdfBrewer brewer = new PdfBrewer();
		BrewerData pb = new BrewerData(printData, brewer.getFontLoader());
		brewer.setTitle("仕訳帳");
		brewer.process(pb);
		brewer.save(file);
	}
}
