package net.osdn.aoiro;

import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.ProtectionDomain;

public class Util {

	private static Path appDir;
	private static String pdfCreatorName;
	private static String pdfCreatorVersion;
	
	public static Path getApplicationDirectory() {
		if(appDir == null) {
			appDir = getApplicationDirectory(Util.class);
		}
		return appDir;
	}

	public static Path getApplicationDirectory(Class<?> cls) {
		try {
			ProtectionDomain pd = cls.getProtectionDomain();
			CodeSource cs = pd.getCodeSource();
			URL location = cs.getLocation();
			URI uri = location.toURI();
			Path path = Path.of(uri);
			Path parent = path.getParent();
			return parent.toAbsolutePath().normalize();
		} catch (Exception e) {
			return Path.of(".").toAbsolutePath().normalize();
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

	public static void setPdfCreator(String name, String version) {
		Util.pdfCreatorName = name;
		Util.pdfCreatorVersion = version;
	}

	public static String getPdfCreator() {
		String name = Util.pdfCreatorName;
		if(name == null) {
			name = "aoiro";
		}
		String version = Util.pdfCreatorVersion;
		if(version == null) {
			int[] v = getApplicationVersion();
			if(v != null) {
				if(v[2] == 0 && v[3] == 0) {
					version = String.format("%d.%d", v[0], v[1]);
				} else if(v[3] == 0) {
					version = String.format("%d.%d.%d", v[0], v[1], v[2]);
				} else {
					version = String.format("%d.%d.%d.%d", v[0], v[1], v[2], v[3]);
				}
			}
		}
		if(version == null || version.isBlank()) {
			return name;
		} else {
			return name + " " + version;
		}
	}
}
