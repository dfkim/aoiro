package net.osdn.aoiro.model;

public class JournalEntryError {

	protected static final JournalEntryError NO_ERROR = new JournalEntryError(ErrorType.NO_ERROR);
	protected static final JournalEntryError NO_DATE = new JournalEntryError(ErrorType.NO_DATE);
	protected static final JournalEntryError NO_DESCRIPTION = new JournalEntryError(ErrorType.NO_DESCRIPTION);
	protected static final JournalEntryError NO_ACCOUNT = new JournalEntryError(ErrorType.NO_ACCOUNT);
	protected static final JournalEntryError NO_DEBTOR = new JournalEntryError(ErrorType.NO_DEBTOR);
	protected static final JournalEntryError NO_CREDITOR = new JournalEntryError(ErrorType.NO_CREDITOR);
	protected static final JournalEntryError NOT_MATCH_AMOUNT = new JournalEntryError(ErrorType.NOT_MATCH_AMOUNT);

	public enum ErrorType {
		/** エラーはありません（正常） */
		NO_ERROR,

		/** 日付が指定されていません */
		NO_DATE,

		/** 摘要が指定されていません */
		NO_DESCRIPTION,

		/** 勘定科目が指定されていません */
		NO_ACCOUNT,

		/** 借方の金額が指定されていません */
		NO_DEBTOR_AMOUNT,

		/** 貸方の金額が指定されていません */
		NO_CREDITOR_AMOUNT,

		/** 借方の勘定科目が指定されていません */
		NO_DEBTOR,

		/** 貸方の勘定科目が指定されていません */
		NO_CREDITOR,

		/** 借方と貸方の金額が一致していません */
		NOT_MATCH_AMOUNT
	}

	private ErrorType errorType;
	private AccountTitle accountTitle;

	public JournalEntryError(ErrorType type) {
		this.errorType = type;
	}

	public JournalEntryError(ErrorType type, AccountTitle accountTitle) {
		this.errorType = type;
		this.accountTitle = accountTitle;
	}

	/** エラーの種類を返します。
	 *
	 * @return エラーの種類。エラーがない場合は NO_ERROR を返します。
	 */
	public ErrorType getErrorType() {
		return errorType;
	}

	/** エラーに関連する勘定科目を返します。
	 *
	 * @return エラーに関連する勘定科目。エラーに関連する勘定科目がない場合は null。
	 */
	public AccountTitle getAccountTitle() {
		return accountTitle;
	}
}
