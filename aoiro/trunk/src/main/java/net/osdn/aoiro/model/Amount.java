package net.osdn.aoiro.model;

/** 借方または貸方の金額
 * はじめに設定したバランスを維持します。
 * 金額がマイナスに転じても借方、貸方が変わることはありません。
 * 
 */
public class Amount implements Cloneable {
	
	private Class<? extends Account> normalBalance;
	private int value;
	
	public Amount(Class<? extends Account> normalBalance, int value) {
		this.normalBalance = normalBalance;
		this.value = value;
	}
	
	/** 借方または貸方を返します。
	 * 
	 * @return 借方の場合は Debtor.class、貸方の場合は Creditor.class を返します。
	 */
	public Class<? extends Account> getNormalBalance() {
		return normalBalance;
	}
	
	/** 金額を返します。
	 * 
	 * @return 金額
	 */
	public int getValue() {
		return value;
	}
	
	/** 金額を設定します。
	 * 
	 * @param value 金額
	 */
	public void setValue(int value) {
		this.value = value;
	}

	/** 金額を増加または減少させます。
	 * バランスが同じ場合は金額が増加します。バランスが異なる場合は金額が減少します。
	 * 
	 * @param amount 増減させる金額
	 */
	public void increase(Amount amount) {
		if(amount != null) {
			if(amount.getNormalBalance() == normalBalance) {
				value += amount.getValue();
			} else {
				value -= amount.getValue();
			}
		}
	}
	
	/** 指定した分だけ金額を増加させます。
	 * 
	 * @param amount 増加させる金額
	 */
	public void increase(int amount) {
		 this.value += amount;
	}
	
	/** 金額を増加または減少させます。
	 * バランスが同じ場合は金額が減少します。バランスが異なる場合は金額が増加します。
	 * 
	 * @param amount 増減させる金額
	 */
	public void decrease(Amount amount) {
		if(amount != null) {
			if(amount.getNormalBalance() == normalBalance) {
				value -= amount.getValue();
			} else {
				value += amount.getValue();
			}
		}
	}
	
	/** 指定した分だけ金額を減少させます。
	 * 
	 * @param amount 減少させる金額
	 */
	public void decrease(int amount) {
		this.value -= amount;
	}
	
	public Amount clone() {
		return new Amount(normalBalance, value);
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if(normalBalance == Debtor.class) {
			sb.append("借方 ");
		} else if(normalBalance == Creditor.class) {
			sb.append("貸方 ");
		}
		sb.append(value);
		return sb.toString();
	}
}
