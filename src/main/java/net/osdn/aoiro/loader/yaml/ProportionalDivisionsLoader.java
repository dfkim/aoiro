package net.osdn.aoiro.loader.yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import net.osdn.util.io.AutoDetectReader;

import static net.osdn.aoiro.ErrorMessage.error;

/** YAMLファイルから家事按分をロードします。
 * 
 */
public class ProportionalDivisionsLoader {
	
	private Map<String, AccountTitle> accountTitleByDisplayName;
	private List<ProportionalDivision> proportionalDivisions = new ArrayList<ProportionalDivision>();

	public ProportionalDivisionsLoader(Path path, Set<AccountTitle> accountTitles) throws IOException {
		this.accountTitleByDisplayName = new HashMap<String, AccountTitle>();
		for(AccountTitle accountTitle : accountTitles) {
			accountTitleByDisplayName.put(accountTitle.getDisplayName(), accountTitle);
		}

		try {
			new ItemReader(path).read();
		} catch(YamlException e) {
			YamlBeansUtil.Message m = YamlBeansUtil.getMessage(e);
			throw error(" [エラー] " + path + " (" + m.getLine() + "行目, " + m.getColumn() + "桁目)\r\n " + m.getMessage());
		}
	}
	
	/** 家事按分リストを取得します。
	 * 
	 * @return 家事按分リスト
	 */
	public List<ProportionalDivision> getProportionalDivisions() {
		return proportionalDivisions;
	}

	private class ItemReader extends YamlReader {

		private Path path;

		public ItemReader(Path path) throws IOException {
			super(AutoDetectReader.readAll(path));
			this.path = path;
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
					throw error(" [エラー] " + path + " (" + line + "行目)\r\n 勘定科目が指定されていません。");
				}
				AccountTitle accountTitle =  accountTitleByDisplayName.get(item.勘定科目.trim());
				if(accountTitle == null) {
					throw error(" [エラー] " + path + " (" + line + "行目)\r\n 未定義の勘定科目が指定されました: " + item.勘定科目);
				}
				if(item.事業割合 == null) {
					throw error(" [エラー] " + path + " (" + line + "行目)\r\n 事業割合が指定されていません。");
				}
				item.事業割合 = item.事業割合.trim();
				double businessRatio;
				try {
					businessRatio = Double.parseDouble(item.事業割合) / 100.0;
				} catch(Exception e) {
					throw error(" [エラー] " + path + " (" + line + "行目)\r\n 事業割合は数値で指定してください: " + item.事業割合);
				}
				if(businessRatio < 0.0 || businessRatio > 1.0) {
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

	public static synchronized void save(Path file, List<ProportionalDivision> proportionalDivisions) throws IOException {
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

			Files.move(tmpFile, file,
					StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE);

		} finally {
			if(tmpFile != null) {
				try { Files.deleteIfExists(tmpFile); } catch(Exception ignore) {}
			}
		}
	}

	public static String getYaml(List<ProportionalDivision> proportionalDivisions) {
		StringBuilder sb = new StringBuilder();

		if(proportionalDivisions != null) {
			for(ProportionalDivision proportionalDivision : proportionalDivisions) {
				sb.append("- {勘定科目: ");
				sb.append(proportionalDivision.getAccountTitle().getDisplayName());
				sb.append(", 事業割合: ");
				sb.append(proportionalDivision.getBusinessRatio() * 100.0);
				sb.append("}\r\n");
			}
		}

		return sb.toString();
	}
}
