package net.osdn.aoiro;

@SuppressWarnings("serial")
public class ErrorMessage extends Error {

	public ErrorMessage(String message) {
		super(message);
	}

	public ErrorMessage(String message, Throwable cause) {
		super(message, cause);
	}

	public static ErrorMessage error(String message) {
		return new ErrorMessage(message);
	}

	public static ErrorMessage error(String message, Throwable cause) {
		return new ErrorMessage(message, cause);
	}

}
