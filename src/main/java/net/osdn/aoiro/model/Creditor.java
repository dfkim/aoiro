package net.osdn.aoiro.model;

/** 貸方勘定
 * 
 */
public class Creditor extends Account {
	
	public Creditor(AccountTitle accountTitle, int amount) {
		setAccountTitle(accountTitle);
		setAmount(amount);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("{貸方勘定科目:");
		//sb.append(getAccountTitle().getDisplayName());
		sb.append(", 金額:");
		sb.append(getAmount());
		sb.append("}");
		return sb.toString();
	}
}
