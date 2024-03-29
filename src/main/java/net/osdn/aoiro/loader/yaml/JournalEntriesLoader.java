package net.osdn.aoiro.loader.yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;

import com.esotericsoftware.yamlbeans.parser.Parser;
import com.esotericsoftware.yamlbeans.tokenizer.Tokenizer;
import net.osdn.aoiro.model.AccountTitle;
import net.osdn.aoiro.model.Creditor;
import net.osdn.aoiro.model.Debtor;
import net.osdn.aoiro.model.JournalEntry;

import static net.osdn.aoiro.ErrorMessage.error;

/** YAMLファイルから仕訳をロードします。
 *
 */
public class JournalEntriesLoader {

	private static DateTimeFormatter dateParser = DateTimeFormatter.ofPattern("y-M-d");

	private Path path;
	private Map<String, AccountTitle> accountTitleByDisplayName;

	public JournalEntriesLoader(Path path, Set<AccountTitle> accountTitles) {
		this.path = path;

		this.accountTitleByDisplayName = new HashMap<String, AccountTitle>();
		for(AccountTitle accountTitle : accountTitles) {
			accountTitleByDisplayName.put(accountTitle.getDisplayName(), accountTitle);
		}
	}

	/** 仕訳リストを取得します。
	 *
	 * @return 仕訳リスト
	 * @throws IOException I/Oエラーが発生した場合
	 */
	public List<JournalEntry> getJournalEntries() throws IOException {
		return getJournalEntries(false, false);
	}

	/** 仕訳リストを取得します。
	 *
	 * @param ignoreWarnings 貸借金額の不一致など一部の警告を無視します。（警告のある仕訳も結果のリストに含まれます。）
	 * @param skipErrors 未定義の勘定科目が使われているなどのエラーを無視します。（エラーのある仕訳は結果のリストに含まれません。）
	 * @return 仕訳リスト
	 * @throws IOException I/Oエラーが発生した場合
	 */
	public List<JournalEntry> getJournalEntries(boolean ignoreWarnings, boolean skipErrors) throws IOException {
		try {
			ItemReader reader = new ItemReader(path, ignoreWarnings, skipErrors);
			reader.read();
			return reader.journalEntries;
		} catch(YamlException e) {
			YamlBeansUtil.Message m = YamlBeansUtil.getMessage(e);
			throw error(" [エラー] " + path + " (" + m.getLine() + "行目, " + m.getColumn() + "桁目)\r\n " + m.getMessage());
		}
	}

	private static LocalDate parseDate(String s) {
		s = s.replace('/', '-')
				.replace('.', '-')
				.replace('年', '-')
				.replace('月', '-')
				.replace('日', ' ')
				.trim();
		try {
			return LocalDate.from(dateParser.parse(s));
		} catch(Exception e) {
			return null;
		}
	}

	private static Long parseAmount(String s) {
		s = s.replace(",", "").trim();
		try {
			return Long.parseLong(s);
		} catch(Exception e) {
			return null;
		}
	}

	private class ItemReader extends YamlReader {

		private Path path;
		private boolean ignoreWarnings;
		private boolean skipErrors;
		private List<JournalEntry> journalEntries = new ArrayList<>();

		public ItemReader(Path path, boolean ignoreWarnings, boolean skipErrors) throws IOException {
			super(Files.readString(path, StandardCharsets.UTF_8));
			this.path = path;
			this.ignoreWarnings = ignoreWarnings;
			this.skipErrors = skipErrors;
		}

		@SuppressWarnings("unchecked")
		@Override
		public List<Item> read() throws YamlException {
			return super.read(List.class, Item.class);
		}

		@SuppressWarnings("rawtypes")
		@Override
		protected Object readValue(Class type, Class elementType, Class defaultType) throws YamlException, Parser.ParserException, Tokenizer.TokenizerException {
			if(type != Item.class) {
				return super.readValue(type, elementType, defaultType);
			}

			YamlBeansUtil.Position pos = YamlBeansUtil.getPosition(this);
			int line = pos.line + 1;
			Object obj = super.readValue(type, elementType, defaultType);
			if(obj instanceof Item) {
				Item item = (Item)obj;

				if(item.日付 == null && ignoreWarnings == false) {
					// ignoreWarnings = true の場合、日付が null でもエラーとしません。
					if(!skipErrors) {
						throw error(" [エラー] " + path + " (" + line + "行目)\r\n 日付が指定されていません。");
					}
				}
				LocalDate date = parseDate(item.日付);
				if(date == null && ignoreWarnings == false) {
					// ignoreWarnings = true の場合、日付が null でもエラーとしません。
					if(!skipErrors) {
						throw error(" [エラー] " + path + " (" + line + "行目)\r\n 日付の形式に誤りがあります: " + item.日付);
					}
				}

				if(item.摘要 == null) {
					if(!skipErrors) {
						throw error(" [エラー] " + path + " (" + line + "行目)\r\n 摘要が指定されていません。");
					}
					item.摘要 = "";
				}
				String description = item.摘要.trim();

				if(item.借方 == null) {
					if(!skipErrors) {
						throw error(" [エラー] " + path + " (" + line + "行目)\r\n 借方が指定されていません。");
					}
					item.借方 = new ChildItem[0];
				}
				List<Debtor> debtors = new ArrayList<Debtor>();
				long debtorsAmount = 0;
				for(ChildItem d : item.借方) {
					if(d == null) {
						if(!skipErrors) {
							throw error(" [エラー] " + path + " (" + line + "行目)\r\n 借方が指定されていません。");
						}
						continue;
					}
					if(d.勘定科目 == null) {
						if(!skipErrors) {
							throw error(" [エラー] " + path + " (" + line + "行目)\r\n 借方の勘定科目が指定されていません。");
						}
						continue;
					}
					AccountTitle accountTitle = accountTitleByDisplayName.get(d.勘定科目.trim());
					if(accountTitle == null) {
						if(!skipErrors) {
							throw error(" [エラー] " + path + " (" + line + "行目)\r\n 借方に未定義の勘定科目が指定されました: " + d.勘定科目);
						}
						continue;
					}
					if(d.金額 == null) {
						if(!skipErrors) {
							throw error(" [エラー] " + path + " (" + line + "行目\r\n 借方の金額が指定されていません。");
						}
						d.金額 = "-1";
					}
					Long amount = parseAmount(d.金額);
					if(amount == null) {
						if(!skipErrors) {
							throw error(" [エラー] " + path + " (" + line + "行目)\r\n 借方の金額は数値で指定してください: " + d.金額);
						}
						amount = -1L;
					}
					if(amount < 0 && ignoreWarnings == false) {
						// ignoreWarnings = true の場合、金額がマイナスでもエラーとしません。（GUIで金額未入力のときに -1 を設定するためです。）
						if(!skipErrors) {
							throw error(" [エラー] " + path + " (" + line + "行目)\r\n 借方の金額にマイナスの数値を指定することはできません: " + d.金額);
						}
					}
					if(amount > 0) {
						debtorsAmount += amount;
					}
					debtors.add(new Debtor(accountTitle, amount));
				}

				if(item.貸方 == null) {
					if(!skipErrors) {
						throw error(" [エラー] " + path + " (" + line + "行目)\r\n 貸方が指定されていません。");
					}
					item.貸方 = new ChildItem[0];
				}
				List<Creditor> creditors = new ArrayList<Creditor>();
				long creditorsAmount = 0;
				for(ChildItem c : item.貸方) {
					if(c == null) {
						if(!skipErrors) {
							throw error(" [エラー] " + path + " (" + line + "行目)\r\n 貸方が指定されていません。");
						}
						continue;
					}
					if(c.勘定科目 == null) {
						if(!skipErrors) {
							throw error(" [エラー] " + path + " (" + line + "行目)\r\n 貸方の勘定科目が指定されていません。");
						}
						continue;
					}
					AccountTitle accountTitle = accountTitleByDisplayName.get(c.勘定科目.trim());
					if(accountTitle == null) {
						if(!skipErrors) {
							throw error(" [エラー] " + path + " (" + line + "行目)\r\n 貸方に未定義の勘定科目が指定されました: " + c.勘定科目);
						}
						continue;
					}
					if(c.金額 == null) {
						if(!skipErrors) {
							throw error(" [エラー] " + path + " (" + line + "行目)\r\n 貸方の金額が指定されていません。");
						}
						c.金額 = "-1";
					}
					Long amount = parseAmount(c.金額);
					if(amount == null) {
						if(!skipErrors) {
							throw error(" [エラー] " + path + " (" + line + "行目)\r\n 貸方の金額は数値で指定してください: " + c.金額);
						}
						amount = -1L;
					}
					if(amount < 0 && ignoreWarnings == false) {
						// ignoreWarnings = true の場合、金額がマイナスでもエラーとしません。（GUIで金額未入力のときに -1 を設定するためです。）
						if(!skipErrors) {
							throw error(" [エラー] " + path + " (" + line + "行目)\r\n 貸方の金額にマイナスの数値を指定することはできません: " + c.金額);
						}
					}
					if(amount > 0) {
						creditorsAmount += amount;
					}
					creditors.add(new Creditor(accountTitle, amount));
				}

				if(debtorsAmount != creditorsAmount && ignoreWarnings == false) {
					// ignoreWarnings = true の場合、貸借金額の不一致はエラーとしません。
					if(!skipErrors) {
						throw error(" [エラー] " + path + " (" + line + "行目)\r\n 借方と貸方の金額が一致していません: 借方金額 " + debtorsAmount + ", 貸方金額 " + creditorsAmount);
					}
				}

				JournalEntry entry = new JournalEntry(date, description, debtors, creditors);
				journalEntries.add(entry);
			}
			return obj;
		}
	}

	private static class Item {
		public String 日付;
		public String 摘要;
		public ChildItem[] 借方;
		public ChildItem[] 貸方;
	}

	private static class ChildItem {
		public String 勘定科目;
		public String 金額;
	}

	/// save ///

	public static synchronized void write(Path file, List<JournalEntry> journalEntries) throws IOException {
		String yaml = getYaml(journalEntries);

		Path tmpFile = null;
		try {
			Path dir = file.getParent();
			if(Files.notExists(dir)) {
				Files.createDirectories(dir);
			}
			tmpFile = dir.resolve("仕訳データ.tmp");
			Files.writeString(tmpFile, yaml, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING,
					StandardOpenOption.WRITE,
					StandardOpenOption.SYNC);

			try {
				Files.move(tmpFile, file,
						StandardCopyOption.REPLACE_EXISTING,
						StandardCopyOption.ATOMIC_MOVE);
			} catch(AtomicMoveNotSupportedException e) {
				// 特定の環境において同一ドライブ・同一フォルダー内でのファイル移動であっても
				// AtomicMoveNotSupportedException がスローされることがあるようです。
				// AtomicMoveNotSupportedException がスローされた場合、ATOMIC_MOVE なしで移動を試みます。
				Files.move(tmpFile, file,
						StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			if(tmpFile != null) {
				try { Files.deleteIfExists(tmpFile); } catch(Exception ignore) {}
			}
		}
	}

	public static String getYaml(List<JournalEntry> journalEntries) {
		StringBuilder sb = new StringBuilder();

		if(journalEntries != null) {
			for(JournalEntry journalEntry : journalEntries) {
				sb.append(journalEntry.getYaml());
				sb.append("\r\n");
			}
		}

		return sb.toString();
	}
}
