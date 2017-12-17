package net.osdn.aoiro.model;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** 仕訳
 *
 */
public class JournalEntry {

	private static ThreadLocal<DateFormat> threadLocalDateFormat = new ThreadLocal<DateFormat>() {
	    @Override
	    protected DateFormat initialValue() {
	        return new SimpleDateFormat("yyyy-MM-dd");
	    }
	};
	
	/** 日付 */
	private Date date;
	
	/** 摘要 */
	private String description;
	
	/** 借方 */
	private List<Debtor> debtors;
	
	/** 貸方 */
	private List<Creditor> creditors;
	
	public JournalEntry(Date date, String description, List<Debtor> debtors, List<Creditor> creditors) {
		this.date = date;
		this.description = description;
		this.debtors = debtors;
		this.creditors = creditors;

		if(this.debtors == null) {
			this.debtors = new ArrayList<Debtor>();
		}
		if(this.creditors == null) {
			this.creditors = new ArrayList<Creditor>();
		}
	}
	
	/** 日付を取得します。
	 * 
	 * @return 日付
	 */
	public Date getDate() {
		return date;
	}
	
	/** 日付を設定します。
	 * 
	 * @param date 日付
	 */
	public void setDate(Date date) {
		this.date = date;
	}
	
	/** 摘要を取得します。
	 * 
	 * @return 摘要
	 */
	public String getDescription() {
		return description;
	}
	
	/** 摘要を設定します。
	 * 
	 * @param description 摘要
	 */
	public void setDescription(String description) {
		this.description = description;
	}
	
	/** 借方を取得します。
	 * 
	 * @return 借方
	 */
	public List<Debtor> getDebtors() {
		return debtors;
	}

	/** 貸方を取得します。
	 * 
	 * @return 貸方
	 */
	public List<Creditor> getCreditors() {
		return creditors;
	}
	

	/** この仕訳が開始仕訳かどうかを返します。
	 * 
	 * @return 開始仕訳の場合は true、そうでなければ false を返します。
	 */
	public boolean isOpening() {
		//はじめに締切仕訳でないことを確認します。
		//開始仕訳の判断する勘定科目(元入金)は残高振替仕訳にも含まれるため、
		//締切仕訳の元入金が誤って開始仕訳と判断されないようにするためです。
		if(isClosing()) {
			return false;
		}
		//個人事業主の場合は貸方に元入金を含む仕訳を開始仕訳として扱います。
		//FIXME: 法人の場合の開始仕訳への対応を追加する必要があります。
		for(Debtor debtor : debtors) {
			String displayName = debtor.getAccountTitle().getDisplayName();
			if(displayName.equals("元入金")) {
				return true;
			}
		}
		for(Creditor creditor : creditors) {
			String displayName = creditor.getAccountTitle().getDisplayName();
			if(displayName.equals("元入金")) {
				return true;
			}
		}
		return false;
	}
	
	/** この仕訳が締切仕訳かどうかを返します。
	 * この仕訳の借方、貸方のいずれかまたは両方に決算勘定を含む場合、この仕訳は締切仕訳です。
	 * 
	 * @return 締切仕訳の場合は true、そうでなければ false を返します。
	 */
	public boolean isClosing() {
		for(Debtor debtor : debtors) {
			if(debtor.getAccountTitle().isClosing()) {
				return true;
			}
		}
		for(Creditor creditor : creditors) {
			if(creditor.getAccountTitle().isClosing()) {
				return true;
			}
		}
		return false;
	}

	/** この仕訳が損益振替仕訳かどうかを返します。
	 * この仕訳の借方、貸方のいずれかまたは両方に損益勘定を含む場合、この仕訳は損益振替仕訳です。
	 * 
	 * @return 損益振替仕訳の場合は true、そうでなければ false を返します。
	 */
	public boolean isIncomeSummary() {
		for(Debtor debtor : debtors) {
			if(debtor.getAccountTitle().equals(AccountTitle.INCOME_SUMMARY)) {
				return true;
			}
		}
		for(Creditor creditor : creditors) {
			if(creditor.getAccountTitle().equals(AccountTitle.INCOME_SUMMARY)) {
				return true;
			}
		}
		return false;
	}
	
	/** この仕訳が残高振替仕訳かどうかを返します。
	 * この仕訳の借方、貸方のいずれかまたは両方に残高勘定を含む場合、この仕訳は残高振替仕訳です。
	 * 
	 * @return 残高振替仕訳の場合は true、そうでなければ false を返します。
	 */
	public boolean isBalance() {
		for(Debtor debtor : debtors) {
			if(debtor.getAccountTitle().equals(AccountTitle.BALANCE)) {
				return true;
			}
		}
		for(Creditor creditor : creditors) {
			if(creditor.getAccountTitle().equals(AccountTitle.BALANCE)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(threadLocalDateFormat.get().format(date));
		sb.append(", 借方:[");
		for(int i = 0; i < debtors.size(); i++) {
			Debtor debtor = debtors.get(i);
			sb.append("{");
			sb.append(debtor.getAccountTitle().getDisplayName());
			sb.append(":");
			sb.append(debtor.getAmount());
			sb.append("}");
			if(i + 1 < debtors.size()) {
				sb.append(", ");
			}
		}
		sb.append("], 貸方:[");
		for(int i = 0; i < creditors.size(); i++) {
			Creditor creditor = creditors.get(i);
			sb.append("{");
			sb.append(creditor.getAccountTitle().getDisplayName());
			sb.append(":");
			sb.append(creditor.getAmount());
			sb.append("}");
			if(i + 1 < creditors.size()) {
				sb.append(", ");
			}
		}
		sb.append("], ");
		sb.append(description);
		return sb.toString();
	}
}
