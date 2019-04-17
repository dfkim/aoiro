package net.osdn.aoiro;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Locale;

public class Util {

	private static File appDir;
	private static Locale locale = new Locale("ja", "JP", "JP");
	
	public static File getApplicationDirectory() {
		if(appDir == null) {
			appDir = getApplicationDirectory(Util.class);
		}
		return appDir;
	}

	public static File getApplicationDirectory(Class<?> cls) {
		try {
			ProtectionDomain pd = cls.getProtectionDomain();
			CodeSource cs = pd.getCodeSource();
			URL location = cs.getLocation();
			URI uri = location.toURI();
			String path = uri.getPath();
			File file = new File(path);
			return file.getParentFile();
		} catch (Exception e) {
			try {
				return new File(".").getCanonicalFile();
			} catch (IOException e1) {
				return new File(".").getAbsoluteFile();
			}
		}
	}
	
	public static int[] getApplicationVersion() {
		String s = System.getProperty("java.application.version");
		if(s == null || s.trim().length() == 0) {
			return null;
		}
		
		s = s.trim() + ".0.0.0.0";
		String[] array = s.split("\\.", 5);
		int[] version = new int[4];
		for(int i = 0; i < 4; i++) {
			try {
				version[i] = Integer.parseInt(array[i]);
			} catch(NumberFormatException e) {
				e.printStackTrace();
			}
		}
		if(version[0] == 0 && version[1] == 0 && version[2] == 0 && version[3] == 0) {
			return null;
		}
		return version;
	}
	
	/** アルファベット表記の元号を漢字に置換して返します。
	 * 環境によってはSimpleDateFormatのGGGGで和暦にしたときに元号が漢字ではなくアルファベットになってしまう問題への対処です。
	 * アルファベット表記の元号とは "Meiji"、"Taisho"、"Showa"、"Heisei"、"Reiwa"　のことです。
	 * "M"、"T"、"S"、"H"、"R" という短縮表記が漢字表記の元号に置換されるわけではありません。
	 * 
	 * @param s アルファベットの元号を含む文字列
	 * @return アルファベットの元号を漢字に置換した文字列
	 */
	public static String toKanjiEra(String s) {
		if(s == null) {
			return null;
		}
		return s
				.replace("Meiji",  "明治")
				.replace("Taisho", "大正")
				.replace("Showa",  "昭和")
				.replace("Heisei", "平成")
				.replace("Reiwa",  "令和");
	}
	
	public static Locale getLocale() {
		return locale;
	}
}
