package net.osdn.aoiro.cui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.osdn.aoiro.AccountSettlement;
import net.osdn.aoiro.ErrorMessage;
import net.osdn.aoiro.Util;
import net.osdn.aoiro.loader.yaml.YamlAccountTitlesLoader;
import net.osdn.aoiro.loader.yaml.YamlJournalsLoader;
import net.osdn.aoiro.loader.yaml.YamlProportionalDivisionsLoader;
import net.osdn.aoiro.model.AccountTitle;
import net.osdn.aoiro.model.Amount;
import net.osdn.aoiro.model.JournalEntry;
import net.osdn.aoiro.model.Node;
import net.osdn.aoiro.model.ProportionalDivision;
import net.osdn.aoiro.report.BalanceSheet;
import net.osdn.aoiro.report.GeneralJournal;
import net.osdn.aoiro.report.GeneralLedger;
import net.osdn.aoiro.report.ProfitAndLoss;
import net.osdn.aoiro.report.StatementOfChangesInEquity;
import net.osdn.util.io.AutoDetectReader;

import static net.osdn.aoiro.ErrorMessage.error;

public class Main {
	
	public static void main(String[] args) {

		System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
		
		try {
			boolean skipSettlement = false;
			boolean showMonthlyTotal = false;
			Boolean isSoloProprietorship = null;
			String filename = null;

			if(args.length >= 1) {
				for (int i = 0; i < args.length; i++) {
					if (args[i].equals("-o")) {
						skipSettlement = true;
					}
					if (args[i].equals("-m")) {
						showMonthlyTotal = true;
					}
					if (args[i].equals("-p")) {
						isSoloProprietorship = Boolean.TRUE;
					}
					if (args[i].equals("-c")) {
						isSoloProprietorship = Boolean.FALSE;
					}
				}
				filename = args[args.length - 1];
			}

			if(filename == null) {
				System.out.println("Usage: aoiro.exe <options> <仕訳データファイル>");
				System.out.println("Options:");
				System.out.println("  -o    決算処理をせずに仕訳帳と総勘定元帳を出力します。");
				System.out.println("  -m    総勘定元帳に月計を印字します。");
				System.out.println("  -p    個人事業主用のデータファイルを使用します。");
				System.out.println("  -c    法人用のデータファイルを使用します。");
				System.out.println();
				pause();
				return;
			}

			Path journalEntryPath = Paths.get(filename);
			if(!Files.exists(journalEntryPath) || Files.isDirectory(journalEntryPath)) {
				System.err.println("ファイルが見つかりません: " + journalEntryPath);
				pause();
				return;
			}
			journalEntryPath = journalEntryPath.toAbsolutePath().normalize();

			if(isSoloProprietorship == null) {
				isSoloProprietorship = isSoloProprietorship(journalEntryPath);
			}
			Path defaultDir = Util.getApplicationDirectory().resolve("default");
			if (isSoloProprietorship) {
				System.out.println("次のデータファイルを使用して処理を実行します。");
				defaultDir = defaultDir.resolve("個人");
			} else {
				System.out.println("次のデータファイルを使用して処理を実行します。");
				defaultDir = defaultDir.resolve("法人");
			}
			Path inputDir = journalEntryPath.getParent();
			Path outputDir = inputDir;

			int processNumber = 0;

			// 勘定科目.yml
			Path accountTitlesPath = getAccountTitlePath(inputDir, defaultDir);
			if (accountTitlesPath == null) {
				throw error(" [エラー] ファイルが見つかりません: 勘定科目.yml");
			}

			System.out.println(" (" + (++processNumber) + ") 勘定科目 | " + accountTitlesPath);
			YamlAccountTitlesLoader accountTitlesLoader = new YamlAccountTitlesLoader(accountTitlesPath);
			Set<AccountTitle> accountTitles = accountTitlesLoader.getAccountTitles();

			// 家事按分.yml
			Path proportionalDivisionsPath = null;
			if (isSoloProprietorship) {
				proportionalDivisionsPath = getProportionalDivisionsPath(inputDir, defaultDir);
				if (proportionalDivisionsPath == null) {
					throw error(" [エラー] ファイルが見つかりません: 家事按分.yml");
				}
			}

			List<ProportionalDivision> proportionalDivisions = null;
			if (proportionalDivisionsPath != null) {
				System.out.println(" (" + (++processNumber) + ") 家事按分 | " + proportionalDivisionsPath);
				YamlProportionalDivisionsLoader proportionalDivisionsLoader = new YamlProportionalDivisionsLoader(proportionalDivisionsPath, accountTitles);
				proportionalDivisions = proportionalDivisionsLoader.getProportionalDivisions();
			}

			// 仕訳データ.yml
			YamlJournalsLoader journalsLoader = new YamlJournalsLoader(journalEntryPath, accountTitles);
			List<JournalEntry> journalEntries = journalsLoader.getJournalEntries();
			System.out.println(" (" + (++processNumber) + ") 仕訳　　 | " + journalEntryPath + " (" + journalEntries.size() + "件)");
			System.out.println();

			accountTitlesLoader.validate();

			if (!skipSettlement) {
				//決算
				System.out.println("決算処理を実行しています . . .");
				AccountSettlement accountSettlement = new AccountSettlement(accountTitles, isSoloProprietorship);
				accountSettlement.setPrintStream(System.out);
				accountSettlement.addClosingEntries(journalEntries, proportionalDivisions);
				System.out.println("");
			}

			if (skipSettlement) {
				System.out.println("帳簿を作成しています . . .");
			} else {
				System.out.println("帳簿と決算書を作成しています . . .");
			}

			// 仕訳帳
			GeneralJournal generalJournal = new GeneralJournal(journalEntries, isSoloProprietorship);

			// 総勘定元帳
			GeneralLedger generalLedger = new GeneralLedger(accountTitles, journalEntries, isSoloProprietorship, showMonthlyTotal);

			// 仕訳帳をファイルに出力します。
			// この処理は総勘定元帳（GeneralLedger）を作成してから呼び出す必要があります。GeneralLedgerによって仕訳帳の「元丁」が設定されるからです。
			generalJournal.writeTo(outputDir.resolve("仕訳帳.pdf"));
			System.out.println("  仕訳帳.pdf を出力しました。");

			// 総勘定元帳をファイルに出力します。
			// この処理は仕訳帳（GeneralJournal）を作成してから呼び出す必要があります。GeneralJournalによって総勘定元帳の「仕丁」が設定されるからです。
			generalLedger.writeTo(outputDir.resolve("総勘定元帳.pdf"));
			System.out.println("  総勘定元帳.pdf を出力しました。");

			if (!skipSettlement) {
				//損益計算書
				Node<Entry<List<AccountTitle>, Amount>> plRoot = accountTitlesLoader.getProfitAndLossRoot();
				Set<String> plSignReversedNames = accountTitlesLoader.getSignReversedNamesForProfitAndLoss();
				Set<String> plAlwaysShownNames = accountTitlesLoader.getAlwaysShownNamesForProfitAndLoss();
				Set<String> plHiddenNamesIfZero = accountTitlesLoader.getHiddenNamesIfZeroForProfitAndLoss();
				ProfitAndLoss pl = new ProfitAndLoss(plRoot, journalEntries, isSoloProprietorship,
						plSignReversedNames, plAlwaysShownNames, plHiddenNamesIfZero);
				pl.writeTo(outputDir.resolve("損益計算書.pdf"));
				System.out.println("  損益計算書.pdf を出力しました。");

				//貸借対照表
				Node<Entry<List<AccountTitle>, Amount[]>> bsRoot = accountTitlesLoader.getBalanceSheetRoot();
				Set<String> bsSignReversedNames = accountTitlesLoader.getSignReversedNamesForBalanceSheet();
				Set<String> bsAlwaysShownNames = accountTitlesLoader.getAlwaysShownNamesForBalanceSheet();
				Set<String> bsHiddenNamesIfZero = accountTitlesLoader.getHiddenNamesIfZeroForBalanceSheet();
				BalanceSheet bs = new BalanceSheet(bsRoot, journalEntries, isSoloProprietorship,
						bsSignReversedNames, bsAlwaysShownNames, bsHiddenNamesIfZero);
				bs.writeTo(outputDir.resolve("貸借対照表.pdf"));
				System.out.println("  貸借対照表.pdf を出力しました。");

				//社員資本等変動計算書
				if (!isSoloProprietorship) {
					Map<String, List<String>> ceReasons = accountTitlesLoader.getStateOfChangesInEquityReasons();
					Node<List<AccountTitle>> ceRoot = accountTitlesLoader.getStateOfChangesInEquityRoot();
					StatementOfChangesInEquity ce = new StatementOfChangesInEquity(ceReasons, ceRoot, journalEntries);
					ce.writeTo(outputDir.resolve("社員資本等変動計算書.pdf"));
					System.out.println("  社員資本等変動計算書.pdf を出力しました。");
				}

				//帳簿・決算書の作成で警告メッセージがあれば出力します。
				if (bs.getWarnings().size() > 0) {
					System.out.println();
					for (String warning : bs.getWarnings()) {
						System.out.println(warning);
					}
				}

				//繰越処理
				System.out.println("");
				System.out.println("繰越処理を実行しています . . .");

				//開始仕訳
				bs.createOpeningJournalEntries(outputDir.resolve("次年度の開始仕訳.yml"));
				System.out.println("  次年度の開始仕訳.yml を出力しました。");
			}

			//終了
			System.out.println();
			System.out.println("すべての処理が終了しました。");
		} catch(ErrorMessage e) {
			System.err.println("\r\n" + e.getMessage() + "\r\n");
		} catch(Exception e) {
			System.err.println();
			e.printStackTrace();
		}
		pause();
	}
	
	private static Path getAccountTitlePath(Path inputPath, Path defaultPath) {
		Path path;

		path = inputPath.resolve("勘定科目.yml");
		if(Files.exists(path) && !Files.isDirectory(path)) {
			return path;
		}

		path = defaultPath.resolve("勘定科目.yml");
		if(Files.exists(path) && !Files.isDirectory(path)) {
			return path;
		}

		return null;
	}
	
	private static Path getProportionalDivisionsPath(Path inputPath, Path defaultPath) {
		Path path;

		path = inputPath.resolve("家事按分.yml");
		if(Files.exists(path) && !Files.isDirectory(path)) {
			return path;
		}

		path = defaultPath.resolve("家事按分.yml");
		if(Files.exists(path) && !Files.isDirectory(path)) {
			return path;
		}

		return null;
	}
	
	
	/** 仕訳データファイルから個人事業主かどうかを判定します。
	 * 仕訳データファイルに元入金が含まれていれば個人事業主と判定します。
	 * 
	 * @param journalEntryPath 仕訳データファイル
	 * @return 仕訳データファイルに元入金がある場合は true、そうでなければ false を返します。
	 * @throws IOException
	 */
	public static boolean isSoloProprietorship(Path journalEntryPath) throws IOException {
		String yaml = AutoDetectReader.readAll(journalEntryPath);
		return yaml.contains("元入金");
	}
	
	private static void pause() {
		System.out.println("続行するにはEnterキーを押してください . . .");
		try {
			System.in.read();
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
}
