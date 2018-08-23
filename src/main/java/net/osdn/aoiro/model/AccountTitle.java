package net.osdn.aoiro.model;

import java.util.List;
import java.util.Set;

/** 勘定科目
 *
 */
public class AccountTitle {
	
	/** 諸口 */
	public static AccountTitle SUNDRIES = new AccountTitle(null, "諸口", false);
	
	/** 決算勘定「損益」です。収益と費用がこの損益勘定に振り替えられます。*/
	public static AccountTitle INCOME_SUMMARY = new AccountTitle(AccountType.Revenue, "損益", true);
	
	/** 決算勘定「残高」です。資産と負債がこの損益勘定に振り替えられます。*/
	public static AccountTitle BALANCE = new AccountTitle(AccountType.Assets, "残高", true);
	
	/** 決算勘定「繰越利益剰余金」です。収益-費用で算出されます。法人で使用する勘定科目です。*/
	public static AccountTitle RETAINED_EARNINGS = new AccountTitle(AccountType.NetAssets, "繰越利益剰余金", true);
	
	/** 決算勘定「控除前の所得金額」です。収益-費用で算出されます。個人事業主で使用する勘定科目です。*/
	public static AccountTitle PRETAX_INCOME = new AccountTitle(AccountType.NetAssets, "控除前の所得金額", true);
	
	/** 種類 */
	private AccountType type;
	
	/** 表示名 */
	private String displayName;
	
	/** 決算勘定 */
	private boolean isClosing;

	public AccountTitle(AccountType type, String displayName) {
		this(type, displayName, false);
	}

	public AccountTitle(AccountType type, String displayName, boolean isClosing) {
		this.type = type;
		this.displayName = displayName;
		this.isClosing = isClosing;
	}

	/** 勘定科目の種類を取得します。
	 * 
	 * @return 勘定科目の種類
	 */
	public AccountType getType() {
		return type;
	}
	
	/** 勘定科目名を取得します。
	 * 
	 * @return 勘定科目名
	 */
	public String getDisplayName() {
		return displayName;
	}
	
	/** この勘定科目が決算勘定かどうかを返します。
	 * 
	 * @return この勘定科目が決算勘定である場合は true、そうでなければ false を返します。
	 */
	public boolean isClosing() {
		return isClosing;
	}

	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(displayName);
		sb.append(" (");
		sb.append(type);
		sb.append(")");
		return sb.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((displayName == null) ? 0 : displayName.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AccountTitle other = (AccountTitle) obj;
		if (displayName == null) {
			if (other.displayName != null)
				return false;
		} else if (!displayName.equals(other.displayName))
			return false;
		if (type != other.type)
			return false;
		return true;
	}
	
	
	/** 勘定科目リストから指定した勘定科目名を探して返します。
	 * 
	 * @param accountTitles 勘定科目リスト
	 * @param displayName 勘定科目名
	 * @return 勘定科目名が見つかればその勘定科目を返します。そうでなければ null を返します。
	 */
	public static AccountTitle getByDisplayName(Set<AccountTitle> accountTitles, String displayName) {
		if(displayName != null) {
			for(AccountTitle accountTitle : accountTitles) {
				if(displayName.equalsIgnoreCase(accountTitle.getDisplayName())) {
					return accountTitle;
				}
			}
		}
		return null;
	}
	
	/** 勘定科目リストに指定した勘定科目名が含まれているかどうか判定します。
	 * 
	 * @param accountTitles 勘定科目リスト
	 * @param displayName 勘定科目名
	 * @return 指定した勘定科目名が含まれている場合は true、そうでなければ false を返します。
	 */
	public static boolean contains(List<AccountTitle> accountTitles, String displayName) {
		if(displayName != null) {
			for(AccountTitle accountTitle : accountTitles) {
				if(displayName.equalsIgnoreCase(accountTitle.getDisplayName())) {
					return true;
				}
			}
		}
		return false;
	}
}
