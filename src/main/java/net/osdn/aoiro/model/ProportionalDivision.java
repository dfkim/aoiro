package net.osdn.aoiro.model;

/** 家事按分
 * 
 */
public class ProportionalDivision {

	/** 家事按分する勘定科目 */
	private AccountTitle accountTitle;
	
	/** 按分する事業割合 0.0～1.0 です。0～100 ではありません。*/
	private double businessRatio;
	
	public ProportionalDivision(AccountTitle accountTitle, double businessRatio) {
		this.accountTitle = accountTitle;
		this.businessRatio = businessRatio;
	}
	
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
	
	/** 事業割合 0.0～1.0 を返します。0～100 ではありません。
	 * 
	 * @return 事業割合
	 */
	public double getBusinessRatio() {
		return businessRatio;
	}

	/** 事業割合を 0.0～1.0 の範囲で設定します。0～100 ではありません。
	 * 
	 * @param businessRatio
	 */
	public void setBusinessRatio(double businessRatio) {
		if(businessRatio < 0.0) {
			businessRatio = 0.0;
		}
		if(businessRatio > 1.0) {
			businessRatio = 1.0;
		}
		this.businessRatio = businessRatio;
	}
}
