package net.osdn.aoiro.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** 仕訳
 *
 */
public class JournalEntry {

	/** 日付 */
	private LocalDate date;
	
	/** 摘要 */
	private String description;
	
	/** 借方 */
	private List<Debtor> debtors;

	/** 貸方 */
	private List<Creditor> creditors;

	/** この仕訳のYAML文字列 */
	private String yaml;

	public JournalEntry() {
		this(null, "", null, null);
	}
	
	public JournalEntry(LocalDate date, String description, List<Debtor> debtors, List<Creditor> creditors) {
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
		updateYaml();
	}

	/** 日付を取得します。
	 * 
	 * @return 日付
	 */
	public LocalDate getDate() {
		return date;
	}
	
	/** 日付を設定します。
	 * 
	 * @param date 日付
	 */
	public void setDate(LocalDate date) {
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

	/** この仕訳が空かどうかを返します。
	 * 日付、摘要、借方、貸方のすべてが空文字、null、0件の場合、この仕訳は空です。
	 *
	 * @return この仕訳が空の場合は true、そうでなければ false
	 */
	public boolean isEmpty() {
		return date == null
				&& (description == null || description.isBlank())
				&& (debtors == null || debtors.size() == 0)
				&& (creditors == null || creditors.size() == 0);
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
		
		//摘要が「前期繰越」「資本金」となっている場合は開始仕訳として扱います。
		//個人事業主、法人いずれの場合も開始仕訳として認識されます。
		if(description.equals("前期繰越") || description.equals("資本金")) {
			return true;
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

	public String updateYaml() {
		StringBuilder sb = new StringBuilder();

		sb.append("- \"日付\" : ");
		if(date != null) {
			sb.append('"');
			sb.append(DateTimeFormatter.ISO_LOCAL_DATE.format(date));
			sb.append("\"\r\n");
		} else {
			sb.append("null\r\n");
		}

		sb.append("  \"摘要\" : \"");
		if(description != null) {
			sb.append(description);
		}
		sb.append("\"\r\n");

		sb.append("  \"借方\" : [ ");
		for(int i = 0; i < debtors.size(); i++) {
			Debtor debtor = debtors.get(i);
			sb.append("{\"勘定科目\" : ");
			AccountTitle accountTitle = debtor.getAccountTitle();
			String displayName = accountTitle != null ? accountTitle.getDisplayName() : null;
			sb.append(displayName != null ? ("\"" + displayName + "\"") : "null");
			sb.append(", \"金額\" : ");
			sb.append(Long.toString(debtor.getAmount()));
			if(i + 1 < debtors.size()) {
				sb.append("}, ");
			} else {
				sb.append("} ");
			}
		}
		sb.append("]\r\n");

		sb.append("  \"貸方\" : [ ");
		for(int i = 0; i < creditors.size(); i++) {
			Creditor creditor = creditors.get(i);
			sb.append("{\"勘定科目\" : ");
			AccountTitle accountTitle = creditor.getAccountTitle();
			String displayName = accountTitle != null ? accountTitle.getDisplayName() : null;
			sb.append(displayName != null ? ("\"" + displayName + "\"") : "null");
			sb.append(", \"金額\" : ");
			sb.append(Long.toString(creditor.getAmount()));
			if(i + 1 < creditors.size()) {
				sb.append("}, ");
			} else {
				sb.append("} ");
			}
		}
		sb.append("]\r\n");

		yaml = sb.toString();
		return yaml;
	}

	public String getYaml() {
		if(yaml == null) {
			updateYaml();
		}
		return yaml;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if(date != null) {
			sb.append(DateTimeFormatter.ISO_LOCAL_DATE.format(date));
		} else {
			sb.append("null");
		}
		sb.append(", 借方:[");
		for(int i = 0; i < debtors.size(); i++) {
			Debtor debtor = debtors.get(i);
			sb.append("{");
			AccountTitle accountTitle = debtor.getAccountTitle();
			String displayName = accountTitle != null ? accountTitle.getDisplayName() : null;
			sb.append(displayName != null ? displayName : "null");
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
			AccountTitle accountTitle = creditor.getAccountTitle();
			String displayName = accountTitle != null ? accountTitle.getDisplayName() : null;
			sb.append(displayName != null ? displayName : "null");
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
