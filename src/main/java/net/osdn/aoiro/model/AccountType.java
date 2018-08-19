package net.osdn.aoiro.model;

/** 勘定科目の種類
 *
 */
public enum AccountType {

	/** 資産 */
	Assets(Debtor.class),
	
	/** 負債 */
	Liabilities(Creditor.class),
	
	/** 純資産 */
	NetAssets(Creditor.class),
	
	/** 収益 */
	Revenue(Creditor.class),
	
	/** 費用 */
	Expense(Debtor.class);
	
	private Class<? extends Account> normalBalance;
	
	private AccountType(Class<? extends Account> normalBalance) {
		this.normalBalance = normalBalance;
	}
	
	/** この勘定の通常のバランスを返します。
	 * Assets(資産)、Expense(費用)は借方(Debtor.class)を返し、借方で増加、貸方で減少する勘定です。
	 * Liabilities(負債)、NetAssets(純資産)、収益(Revenue)は貸方(Creditor.class)を返し、借方で減少、貸方で増加する勘定です。
	 * 
	 * @return 通常バランスが借方の場合、Debtor.class を返します。通常バランスが貸方の場合、Creditor.class を返します。
	 */
	public Class<? extends Account> getNormalBalance() {
		return normalBalance;
	}
}
