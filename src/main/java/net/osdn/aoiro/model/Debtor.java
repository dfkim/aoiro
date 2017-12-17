package net.osdn.aoiro.model;

/** 借方勘定
 *
 */
public class Debtor extends Account {
	
	public Debtor(AccountTitle account, int amount) {
		setAccountTitle(account);
		setAmount(amount);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("{借方勘定科目:");
		sb.append(getAccountTitle().getDisplayName());
		sb.append(", 金額:");
		sb.append(getAmount());
		sb.append("}");
		return sb.toString();
	}
}
