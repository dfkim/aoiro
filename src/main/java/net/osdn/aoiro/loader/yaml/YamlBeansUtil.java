package net.osdn.aoiro.loader.yaml;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class YamlBeansUtil {

	private static final Pattern MESSAGE_PATTERN = Pattern.compile("Line (\\d+), column (\\d+): (.*)", Pattern.DOTALL);

	public static Position getPosition(YamlReader reader) {
		Position pos = new Position();

		YamlReader.YamlReaderException e = reader.new YamlReaderException("");
		Matcher m = MESSAGE_PATTERN.matcher(e.getMessage());
		if(m.matches()) {
			try { pos.line = Integer.parseInt(m.group(1)); } catch(Exception ignore) {}
			try { pos.column = Integer.parseInt(m.group(2)); } catch(Exception ignore) {}
		}
		return pos;
	}

	public static Message getMessage(YamlException exception) {
		Throwable e;

		e = exception;
		while(e != null) {
			String message = e.getMessage();
			if(message != null) {
				Matcher m = MESSAGE_PATTERN.matcher(message);
				if(m.matches()) {
					return new Message(m.group(1), m.group(2), m.group(3));
				}
			}
			e = e.getCause();
		}

		e = exception;
		while(e != null) {
			if(e.getCause() == null) {
				if(e.getMessage() != null && !e.getMessage().isBlank()) {
					return new Message("0", "0", e.getMessage());
				}
			}
			e = e.getCause();
		}

		return new Message("0", "0", exception.getMessage());
	}

	public static class Position {
		public int line;
		public int column;
	}

	public static class Message {
		private int line;
		private int column;
		private String message;

		private Message(String line, String column, String message) {
			try {
				this.line = Integer.parseInt(line);
			} catch(Exception ignore) {}

			try {
				this.column = Integer.parseInt(column);
			} catch(Exception ignore) {}

			this.message = message;
		}

		public int getLine() {
			return this.line;
		}

		public int getColumn() {
			return this.column;
		}

		public String getMessage() {
			return this.message;
		}
	}
}
