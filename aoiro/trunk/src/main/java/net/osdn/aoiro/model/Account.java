package net.osdn.aoiro.model;

import java.util.Objects;

/** 勘定
 * 
 * 借方勘定(Debtor)と貸方勘定(Creditor)のスーパークラスです。
 * 
 */
public abstract class Account {

	/** 勘定科目 */
	private AccountTitle accountTitle;
	
	/** 金額 */
	private int amount;
	
	/** 仕訳帳ページ番号 (仕丁) */
	private int journalPageNumber = 0;
	
	/** 総勘定元帳ページ番号 (元丁) */
	private int ledgerPageNumber = 0;

	/** 勘定科目を取得します。
	 * 
	 * @return 勘定科目
	 */
	public AccountTitle getAccountTitle() {
		return accountTitle;
	}
	
	/** 勘定科目を設定します。
	 * 
	 * @param accountTitle 勘定科目
	 */
	public void setAccountTitle(AccountTitle accountTitle) {
		this.accountTitle = accountTitle;
	}
	
	/** 金額を取得します。
	 * 
	 * @return 金額
	 */
	public int getAmount() {
		return amount;
	}
	
	/** 金額を設定します。
	 * 
	 * @param amount 金額
	 */
	public void setAmount(int amount) {
		this.amount = amount;
	}
	
	/** 総勘定元帳に記載する仕訳帳ページ番号(仕丁)を取得します。
	 * 
	 * @return 仕訳帳ページ番号(仕丁)。仕訳帳記載が完了していない場合は 0 が返されます。
	 */
	public int getJournalPageNumber() {
		return journalPageNumber;
	}
	
	/** 総勘定元帳に記載する仕訳帳ページ番号(仕丁)を設定します。
	 * 
	 * @param pageNumber 仕訳帳ページ番号(仕丁)
	 */
	public void setJournalPageNumber(int pageNumber) {
		this.journalPageNumber = pageNumber;
	}
	
	/** 仕訳帳に記載する総勘定元帳ページ番号(元丁)を取得します。
	 * 
	 * @return 総勘定元帳ページ番号(元丁)。総勘定元帳記載が完了していない場合は 0 が返されます。
	 */
	public int getLedgerPageNumber() {
		return ledgerPageNumber;
	}
	
	/** 仕訳帳に記載する総勘定元帳ページ番号(元丁)を設定します。
	 * 
	 * @param pageNumber 総勘定元帳ページ番号(元丁)
	 */
	public void setLedgerPageNumber(int pageNumber) {
		this.ledgerPageNumber = pageNumber;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Account account = (Account) o;
		return amount == account.amount &&
				Objects.equals(accountTitle, account.accountTitle);
	}

	@Override
	public int hashCode() {
		return Objects.hash(accountTitle, amount);
	}
}
