package net.osdn.aoiro.loader.yaml;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.osdn.aoiro.model.AccountTitle;
import net.osdn.aoiro.model.ProportionalDivision;
import net.osdn.util.yaml.Yaml;

/** YAMLファイルから家事按分をロードします。
 * 
 */
public class YamlProportionalDivisionsLoader {
	
	private Map<String, AccountTitle> accountTitleByDisplayName;
	private List<ProportionalDivision> proportionalDivisions = new ArrayList<ProportionalDivision>();

	public YamlProportionalDivisionsLoader(File file, List<AccountTitle> accountTitles) throws IOException {
		this.accountTitleByDisplayName = new HashMap<String, AccountTitle>();
		for(AccountTitle accountTitle : accountTitles) {
			accountTitleByDisplayName.put(accountTitle.getDisplayName(), accountTitle);
		}
		
		Yaml yaml = new Yaml(file);
		List<Object> list = yaml.getList();
		for(Object obj : list) {
			if(obj instanceof Map) {
				@SuppressWarnings("unchecked")
				Map<String, Object> map = (Map<String, Object>)obj;
				Object value = map.get("勘定科目");
				if(value == null) {
					throw new IllegalArgumentException("家事按分に勘定科目が指定されていないデータが見つかりました。");
				}
				AccountTitle accountTitle = accountTitleByDisplayName.get(value.toString());
				if(accountTitle == null) {
					throw new IllegalArgumentException("家事按分に指定されている勘定科目が見つかりません: " + value.toString());
				}
				value = map.get("事業割合");
				if(value == null) {
					throw new IllegalArgumentException("家事按分に事業割合が指定されていないデータが見つかりました。");
				}
				double businessRatio;
				try {
					businessRatio = Double.parseDouble(value.toString()) / 100.0;
					if(businessRatio < 0.0 || businessRatio > 1.0) {
						throw new IllegalArgumentException();
					}
				} catch(Exception e) {
					throw new IllegalArgumentException("家事按分の事業割合の指定が正しくありません: " + value.toString());
				}
				ProportionalDivision proportionalDivision = new ProportionalDivision(accountTitle, businessRatio);
				proportionalDivisions.add(proportionalDivision);
			}
		}
	}
	
	/** 家事按分リストを取得します。
	 * 
	 * @return 家事按分リスト
	 */
	public List<ProportionalDivision> getProportionalDivisions() {
		return proportionalDivisions;
	}
}
