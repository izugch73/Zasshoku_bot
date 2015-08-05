package zasshoku.bot.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import twitter4j.IDs;
import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;
import zasshoku.bot.bean.EXPStatus;
import zasshoku.bot.engine.MarcovGetterThread;

public class StatusAnalyzer {

	// (?<=^|(?<=[^a-zA-Z0-9-_\\.]))
	public static final String REGEX_REPLY = "@([A-Za-z]+[A-Za-z0-9_]+)";

	// ���v���C�����Ă邩��K���ɕԂ�
	public void checkReplyFromZasshoku(Twitter twitter, Status status,
			List<String> textList, int count, List<EXPStatus> expStatuses,
			boolean isDebug) throws TwitterException, IOException {

		if (count > 5)
			return;

		if ("zasshoku_bot".equals(status.getUser().getScreenName())) {
			// ��������̃��v���C�����疳������
			return;
		}

		// ���v���C���ꂽ�l
		User replyFrom = status.getUser();
		String userScreenName = replyFrom.getScreenName(); // izugch4423 (@�Ȃ�)
		String userName = replyFrom.getName(); // ���ɐ�

		// �o���Ă邩�ǂ��������ꂽ���A�ƍ�����
		String text = status.getText();
		if (text.indexOf("�̂��Ƃ��ڂ��Ă�H") != -1 || text.indexOf("�̂��Ɗo���Ă�H") != -1
				|| text.indexOf("�̂��Ɖ����Ă�H") != -1) {

			User target = status.getUser();

			System.out.println("�y���v���C�����ꂽ�l���ۑ�����Ă��邩�������Ă��܂�...�z");

			IDs readedIds = getUserData_Followers(target, isDebug);
			// User readedUser = (User) readed[0];
			// IDs readedIds = (IDs) readed[1];
			if (null == readedIds) {

				StringBuilder sb = new StringBuilder("@");
				sb.append(target.getScreenName() + " ");
				sb.append(target.getName() + " ����̂��ƁA������Ƃ킩��Ȃ��ł��B�ł��A�����ڂ��܂����B");

				// �o����i����Ɂj
				rememberFollower(twitter, status, isDebug);

				// ���������Ƃ𒝂�
				statusUpdateIfIsNotDebugging(twitter, status, sb.toString(),
						isDebug);
				// if (!isDebug) {
				// StatusUpdate sup = new StatusUpdate(sb.toString());
				// sup.inReplyToStatusId(status.getId());
				//
				// twitter.updateStatus(sup);
				// }

			} else {

				StringBuilder sb = new StringBuilder("@");
				sb.append(target.getScreenName() + " ");
				sb.append(target.getName() + " ����̂��ƁA���ڂ��Ă܂������ǁA���ڂ��Ȃ����܂����B");
				// ����ɍX�V����
				rememberFollower(twitter, status, isDebug);
				statusUpdateIfIsNotDebugging(twitter, status, sb.toString(),
						isDebug);

				// �����݂̃t�H�����[
				IDs nowFollowerIds = twitter
						.getFollowersIDs(target.getId(), -1);

				// �O�������Ƃ��̃t�H�����[�ꗗSet
				Set<Long> before = new HashSet<Long>();
				for (Long l : readedIds.getIDs())
					before.add(l);

				// �����݂̃t�H�����[�ꗗSet
				Set<Long> after = new HashSet<Long>();
				for (Long l : nowFollowerIds.getIDs())
					after.add(l);

				// �����i�����Ă�Ƃ������j
				before.removeAll(after);

				if (before.isEmpty()) {

					// ���ς�Ȃ�
					statusUpdateDirectMessageIfIsNotDebugging(twitter,
							target.getScreenName(), "�t�H�����[�ɕω��͂Ȃ��悤�ł��ˁ`�I",
							isDebug);

				} else {

					// �����[�u���ꂽ�l�̃��X�g�����
					StringBuilder result = new StringBuilder("�ȉ��̐l�Ƀ����[�u����Ă܂��B");
					for (Long l : before) {
						result.append(System.lineSeparator());
						User removedUser = twitter.showUser(l);
						result.append("�E" + removedUser.getName() + "("
								+ removedUser.getScreenName() + ")");
					}

					// ���ʂ�DM����
					statusUpdateDirectMessageIfIsNotDebugging(twitter,
							target.getScreenName(), result.toString(), isDebug);

				}

			}

			return;

		} else if (text.indexOf("�̂��Ƃ��ڂ���") != -1
				|| text.indexOf("�̂��Ɗo����") != -1 || text.indexOf("�̂��Ɖ�����") != -1) {
			// �o���ė~�����ꍇ�́A�o���Ă�����

			// �u~����̂��ƁA�o���܂�����`�v�Ƃ����Z���t���c�C�[�g����B
			String result = rememberFollower(twitter, status, isDebug);
			statusUpdateIfIsNotDebugging(twitter, status, result, isDebug);

			return;

		} else if (text.endsWith("�X�e�[�^�X")) {

			EXPStatus targetUser = null;
			for (EXPStatus es : expStatuses) {
				if (userScreenName.equals(es.getUser().getScreenName())) {
					targetUser = es;
					break;
				}
			}
			// �T�[�`���đ��݂��ĂȂ�������V�K
			if (targetUser == null) {
				targetUser = new EXPStatus(replyFrom);
				targetUser.addExp(1);
				expStatuses.add(targetUser);
			}

			// �X�e�[�^�X��\������B
			StringBuilder sb = new StringBuilder("@");
			sb.append(userScreenName + " ");
			sb.append(userName + "����̃��x����" + targetUser.getLevel() + "�ł��B");
			sb.append(System.lineSeparator());
			sb.append("���݂̌o���l�� " + targetUser.getExp() + "exp �ł��B");
			sb.append(System.lineSeparator());
			sb.append("���̃��x���܂ł��� " + targetUser.getNextLevelExperience()
					+ "exp �K�v�ł��B");
			sb.append(System.lineSeparator());
			sb.append("#zasshoku_bot_exp");

			statusUpdateIfIsNotDebugging(twitter, status, sb.toString(),
					isDebug);

		}

		// ////////////////////////////////////// // ��������͐�΂��

		boolean ifLevelUp = false;
		EXPStatus targetUser = null;
		// ���v���C�����ꂽ�̂ŁA1exp
		for (EXPStatus es : expStatuses) {
			if (userScreenName.equals(es.getUser().getScreenName())) {
				ifLevelUp = es.setExp(es.getExp() + 1);
				targetUser = es;
				System.out.println("@" + userScreenName + "����Ɍo���l��1�t�^���܂����B");
				System.out.println("���݌o���l " + es.getExp());
				break;
			}
		}
		// �T�[�`���đ��݂��ĂȂ�������V�K
		if (targetUser == null) {
			targetUser = new EXPStatus(replyFrom);
			targetUser.addExp(1);
			expStatuses.add(targetUser);
		}

		// �Ԃ�����������
		try {

			// ��������܂Ń}���R�t����������
			while (!getMarcovText(textList))
				;
			String marResult = MarcovGetterThread.getResult();

			StringBuilder sb = new StringBuilder("@");
			sb.append(userScreenName + " ");
			sb.append(marResult.replaceAll(REGEX_REPLY, ""));

			String result = sb.toString();
			result = result.replaceAll("#", "");

			statusUpdateIfIsNotDebugging(twitter, status, result, isDebug);

			if (ifLevelUp) {
				// ���x���A�b�v���
				StringBuilder sb2 = new StringBuilder("@");
				sb2.append(userScreenName + " ");
				sb2.append("���x���A�b�v�I" + (targetUser.getLevel() - 1) + "��"
						+ targetUser.getLevel() + "�@���̃��x���܂�"); // ���x���͊��ɏオ���Ă�̂ŁA(-1)��(�}0)
				sb2.append(targetUser.getNextLevelExperience() + "exp");
				sb2.append(System.lineSeparator());
				sb2.append("#zasshoku_bot_exp");

				statusUpdateIfIsNotDebugging(twitter, status, sb2.toString(),
						isDebug);
			}

		} catch (Exception e) {
			// ���Ԃ�c�C�[�g�Ɏ��s���Ă�B
			return;
		}

		// ���[�U�[�̃��x���A�b�v���ۑ�
		saveToUserData_Experiences(expStatuses);

	}

	/**
	 * HomeTimeLine�E���āA5���ȏナ�c�C�[�g������c�C�[�g������Ȃ�֏悷��B ���������ꂪRT�Ȃ炵�Ȃ��B
	 * 
	 * @param twitter
	 * @param status
	 * @throws TwitterException
	 */
	public void retweetToo(Twitter twitter, Status status, boolean isDebug)
			throws TwitterException {

		if (status.isRetweet())
			return;

		if (status.isRetweeted())
			return;

		if (status.isRetweetedByMe())
			return;

		if (status.getRetweetCount() > 5) {
			statusUpdateRetweetIfIsNotDebugging(twitter, status, isDebug);
			// System.out.println(Main.getTime() + "�y���c�C�[�g�z" +
			// status.getText());
			// if (!isDebug)
			// twitter.retweetStatus(status.getId());
		}

	}

	/**
	 * 
	 * @param textList
	 * @return
	 */
	public boolean getMarcovText(List<String> textList) {

		MarcovGetterThread thread = new MarcovGetterThread(textList);
		thread.start();

		try {

			Thread.sleep(500);

			if (Thread.State.TERMINATED != thread.getState()) {

				System.out
						.println("[Marcov processing error] ���͂�K���ɍ��̂Ɏ��s�����̂ł����������܂��B");
				thread.stopThread();

				return false;

			}

		} catch (InterruptedException e) {

			e.printStackTrace();

		}

		return true;

	}

	/**
	 * �ЂƂ̂��Ƃ��o����B����Ȃ�A�߂����l��status Update����B<br>
	 * �i������Ăяo�����������ƁA�ۑ����邪��������Ȃ��̂Ŏ󂯎���ɂ͕�����Ȃ��j
	 * 
	 * @param twitter
	 * @param status
	 *            "�����Ă���`"���Č������l�̃c�C�[�g�B���[�U���͂�������D���B
	 * @param isDebug
	 * @return "@getScreenName() getName()����̂��ƁA�o���܂�����`�B"
	 */
	private String rememberFollower(Twitter twitter, Status status,
			boolean isDebug) throws TwitterException {

		User target = status.getUser();
		saveToUserData_Followers(target,
				twitter.getFollowersIDs(target.getId(), -1));

		StringBuilder sb = new StringBuilder("@");
		sb.append(target.getScreenName() + " ");
		sb.append(target.getName() + " ����̂��ƁA���ڂ��܂�����`�B");

		System.out.println(Main.getTime() + " �y���̋L���z"
				+ status.getUser().getName() + "(@"
				+ status.getUser().getScreenName()
				+ ")�̃t�H�����[�ꗗ��ۑ����܂����Bfollower="
				+ twitter.getFollowersIDs(target.getId(), -1).getIDs().length);

		String result = sb.toString();
		return result;

	}

	/**
	 * 
	 * �Ԃ₭�B�R���\�[���ւ̃f�o�b�O������͕K���o���B
	 * 
	 * @param twitter
	 * @param status
	 *            ���v���C��X�e�[�^�X�Bnull����
	 * @param text
	 * @param isDebug
	 *            false���ƃR���\�[���ɓf���Btrue���ƂԂ₭�B
	 * @throws TwitterException
	 */
	private void statusUpdateIfIsNotDebugging(Twitter twitter, Status status,
			String text, boolean isDebug) throws TwitterException {

		if (!isDebug) {
			StatusUpdate sup = new StatusUpdate(text);
			if (status != null) {
				// ���v���C�悪����
				sup.inReplyToStatusId(status.getId());
			}
			twitter.updateStatus(sup);
		}

		// �y�R���\�[���z
		// ���v���C�悪����ꍇ�A����ID���o��
		System.out.println(Main.getTime()
				+ " "
				+ text
				+ (status != null ? " [STATUS_ID = " + status.getId()
						+ " �ւ̕ԐM�ł�]" : ""));

	}

	/**
	 * ���c�C�[�g�ŁB
	 * 
	 * @param twitter
	 * @param targetTweet
	 * @param isDebug
	 * @throws TwitterException
	 */
	private void statusUpdateRetweetIfIsNotDebugging(Twitter twitter,
			Status targetTweet, boolean isDebug) throws TwitterException {

		if (!isDebug) {
			twitter.retweetStatus(targetTweet.getId());
		}

		// �y�R���\�[���z
		System.out.println(Main.getTime() + " �y����RT���܂����z"
				+ targetTweet.getText());

	}

	/**
	 * DM�ŁB
	 * 
	 * @param twitter
	 * @param targetUserId
	 * @param message
	 * @param isDebug
	 */
	private void statusUpdateDirectMessageIfIsNotDebugging(Twitter twitter,
			String getScreenName, String message, boolean isDebug)
			throws TwitterException {

		if (!isDebug) {
			twitter.sendDirectMessage(getScreenName, message);
		}

		// �y�R���\�[���z
		System.out.println(Main.getTime() + " �yDM�𑗕t���܂����z @" + getScreenName
				+ " " + message);

	}

	// ���[�U���i�t�H�����[�j�ۑ�
	private static String USER_DATA_FOLLOWERS_FILE_PATH = "_userdata.dat";

	public void saveToUserData_Followers(User user, IDs follower) {

		File accessTokenFile = new File(user.getScreenName()
				+ USER_DATA_FOLLOWERS_FILE_PATH);
		try {

			FileOutputStream output = new FileOutputStream(
					accessTokenFile.getName());
			ObjectOutputStream outObject = new ObjectOutputStream(output);
			outObject.writeObject(follower);

			outObject.close();
			output.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public IDs getUserData_Followers(User user, boolean isDebug) {

		File accessTokenFile = new File(user.getScreenName()
				+ USER_DATA_FOLLOWERS_FILE_PATH);

		if (accessTokenFile.exists()) {
			try {
				FileInputStream input = new FileInputStream(
						accessTokenFile.getName());
				ObjectInputStream inObj = new ObjectInputStream(input);
				IDs readIds = (IDs) inObj.readObject();
				inObj.close();
				input.close();

				return readIds;

			} catch (Exception e) {
				System.out.println("�y���[�U�ǂݏo���̎��s�z"
						+ "�f�[�^�t�@�C���͂��邯�ǁA�Ȃ񂩃��b�N������񂯂Ǔǂ߂Ȃ�����");
				e.printStackTrace();
			}

		}

		return null;

	}

	// ���[�U���i�t�H�����[�j�ۑ�
	private static String USER_DATA_EXP_FILE_PATH = "usersEXPdata.dat";

	/**
	 * �S���[�U��1�t�@�C��
	 */
	public void saveToUserData_Experiences(List<EXPStatus> data) {

		File accessTokenFile = new File(USER_DATA_EXP_FILE_PATH);
		try {

			FileOutputStream output = new FileOutputStream(
					accessTokenFile.getName());
			ObjectOutputStream outObject = new ObjectOutputStream(output);
			outObject.writeObject(data);

			outObject.close();
			output.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public List<EXPStatus> getUserData_Experiences(boolean isDebug) {

		File accessTokenFile = new File(USER_DATA_EXP_FILE_PATH);

		if (accessTokenFile.exists()) {
			try {
				FileInputStream input = new FileInputStream(
						accessTokenFile.getName());
				ObjectInputStream inObj = new ObjectInputStream(input);
				@SuppressWarnings("unchecked")
				List<EXPStatus> readIds = (List<EXPStatus>) inObj.readObject();
				inObj.close();
				input.close();

				return readIds;

			} catch (Exception e) {
				System.out.println("�y���[�U�ǂݏo���̎��s2�z"
						+ "�f�[�^�t�@�C���͂��邯�ǁA�Ȃ񂩃��b�N������񂯂Ǔǂ߂Ȃ�����");
				e.printStackTrace();
			}

		}

		return null;

	}

}
