package net.osdn.aoiro.loader.yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
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
import net.osdn.aoiro.model.ProportionalDivision;

import static net.osdn.aoiro.ErrorMessage.error;

/** YAMLファイルから家事按分をロードします。
 * 
 */
public class ProportionalDivisionsLoader {

	private Path path;
	private Map<String, AccountTitle> accountTitleByDisplayName;

	public ProportionalDivisionsLoader(Path path, Set<AccountTitle> accountTitles) {
		this.path = path;

		this.accountTitleByDisplayName = new HashMap<String, AccountTitle>();
		for(AccountTitle accountTitle : accountTitles) {
			accountTitleByDisplayName.put(accountTitle.getDisplayName(), accountTitle);
		}
	}

	/** 家事按分リストを取得します。
	 *
	 * @return 家事按分リスト
	 * @throws IOException
	 */
	public List<ProportionalDivision> getProportionalDivisions() throws IOException {
		return getProportionalDivisions(false);
	}

	/** 家事按分リストを取得します。
	 * 
	 * @return 家事按分リスト
	 */
	public List<ProportionalDivision> getProportionalDivisions(boolean skipErrors) throws IOException {
		try {
			ItemReader reader = new ItemReader(path, skipErrors);
			reader.read();
			return reader.proportionalDivisions;
		} catch(YamlException e) {
			YamlBeansUtil.Message m = YamlBeansUtil.getMessage(e);
			throw error(" [エラー] " + path + " (" + m.getLine() + "行目, " + m.getColumn() + "桁目)\r\n " + m.getMessage());
		}
	}

	private class ItemReader extends YamlReader {

		private Path path;
		private boolean skipErrors;
		private List<ProportionalDivision> proportionalDivisions = new ArrayList<>();

		public ItemReader(Path path, boolean skipErrors) throws IOException {
			super(Files.readString(path, StandardCharsets.UTF_8));
			this.path = path;
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
			int line = pos.line + 0;
			Object obj = super.readValue(type, elementType, defaultType);
			if(obj instanceof Item) {
				Item item = (Item)obj;
				if(item.勘定科目 == null) {
					if(skipErrors) {
						return null;
					}
					throw error(" [エラー] " + path + " (" + line + "行目)\r\n 勘定科目が指定されていません。");
				}
				AccountTitle accountTitle =  accountTitleByDisplayName.get(item.勘定科目.trim());
				if(accountTitle == null) {
					if(skipErrors) {
						return null;
					}
					throw error(" [エラー] " + path + " (" + line + "行目)\r\n 未定義の勘定科目が指定されました: " + item.勘定科目);
				}
				if(item.事業割合 == null) {
					if(skipErrors) {
						return null;
					}
					throw error(" [エラー] " + path + " (" + line + "行目)\r\n 事業割合が指定されていません。");
				}
				item.事業割合 = item.事業割合.trim();
				double businessRatio;
				try {
					businessRatio = Double.parseDouble(item.事業割合) / 100.0;
				} catch(Exception e) {
					if(skipErrors) {
						return null;
					}
					throw error(" [エラー] " + path + " (" + line + "行目)\r\n 事業割合は数値で指定してください: " + item.事業割合);
				}
				if(businessRatio < 0.0 || businessRatio > 1.0) {
					if(skipErrors) {
						return null;
					}
					throw error(" [エラー] " + path + " (" + line + "行目)\r\n 事業割合は 0～100 の範囲で指定してください: " + item.事業割合);
				}
				ProportionalDivision proportionalDivision = new ProportionalDivision(accountTitle, businessRatio);
				proportionalDivisions.add(proportionalDivision);
			}
			return obj;
		}
	}

	private static class Item {
		public String 勘定科目;
		public String 事業割合;
	}

	/// save ///

	public static synchronized void write(Path file, List<ProportionalDivision> proportionalDivisions) throws IOException {
		String yaml = getYaml(proportionalDivisions);

		Path tmpFile = null;
		try {
			Path dir = file.getParent();
			if(Files.notExists(dir)) {
				Files.createDirectories(dir);
			}
			tmpFile = dir.resolve("家事按分.tmp");
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

	public static String getYaml(List<ProportionalDivision> proportionalDivisions) {
		return getYaml(proportionalDivisions, null);
	}

	public static String getYaml(List<ProportionalDivision> proportionalDivisions, Set<AccountTitle> filter) {
		StringBuilder sb = new StringBuilder();

		if(proportionalDivisions != null) {
			for(ProportionalDivision proportionalDivision : proportionalDivisions) {
				if(filter != null && !filter.contains(proportionalDivision.getAccountTitle())) {
					continue;
				}
				sb.append("- { \"勘定科目\" : \"");
				sb.append(YamlBeansUtil.escape(proportionalDivision.getAccountTitle().getDisplayName()));
				sb.append("\", \"事業割合\" : ");
				sb.append(proportionalDivision.getBusinessRatio() * 100.0);
				sb.append(" }\r\n");
			}
		}

		return sb.toString();
	}
}
