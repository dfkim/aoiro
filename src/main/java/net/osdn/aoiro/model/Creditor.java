package net.osdn.aoiro.model;

/** 貸方勘定
 * 
 */
public class Creditor extends Account {
	
	public Creditor(AccountTitle accountTitle, long amount) {
		setAccountTitle(accountTitle);
		setAmount(amount);
	}

	@Override
	public Creditor clone() {
		return new Creditor(getAccountTitle(), getAmount());
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("{貸方勘定科目:\"");
		AccountTitle accountTitle = getAccountTitle();
		String displayName = accountTitle != null ? accountTitle.getDisplayName() : null;
		sb.append(displayName != null ? displayName : "");
		sb.append("\", 金額:");
		sb.append(getAmount());
		sb.append("}");
		return sb.toString();
	}
}
