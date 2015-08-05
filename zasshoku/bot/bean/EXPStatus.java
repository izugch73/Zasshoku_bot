package zasshoku.bot.bean;

import java.io.Serializable;

import twitter4j.User;

public class EXPStatus implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private User user = null;
	private int exp = 0;
	private int nextLvExp = 0;
	private int level = 1;

	public EXPStatus(User user) {
		super();
		this.user = user;
		// this.level = level;
		// this.exp = exp;
		this.nextLvExp = getNextLevelExperiencePrivate(); // 2になるのに必要な経験値
	}

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public int getExp() {
		return exp;
	}

	/**
	 * 
	 * @return レベルが上ったかどうか
	 */
	public boolean setExp(int exp) {
		this.exp = exp;

		if (exp >= nextLvExp) {
			this.level++; // レベラッポ
			this.nextLvExp = getNextLevelExperiencePrivate();
			return true;
		}

		return false;
	}

	public boolean addExp(int exp) {
		return this.setExp(this.exp + exp);
	}

	public int getLevel() {
		return level;
	}

	public void setLevel(int level) {
		this.level = level;
	}

	/**
	 * 次のレベルになるために必要な経験値
	 */
	public int getNextLevelExperience() {
		return this.nextLvExp;
	}

	private int getNextLevelExperiencePrivate(/* int nowLevel */) {
		int returnExp = 0;
		for (int i = 1; i < level + 1; i++) {
			returnExp += Math.ceil(1.1d * (double) i);
		}

		return returnExp - exp;
	}
}
