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
	public static final String ZASSHOKU_BOT = "zasshoku_bot";

	// リプライが来てるから適当に返す
	public void checkReplyFromZasshoku(Twitter twitter, Status status,
			List<String> textList, int count, List<EXPStatus> expStatuses,
			boolean isDebug) throws TwitterException, IOException {

		if (count > 5)
			return;

		if (ZASSHOKU_BOT.equals(status.getUser().getScreenName())) {
			// 自分からのリプライだから無視する
			return;
		}

		// リプライくれた人
		User replyFrom = status.getUser();
		String userScreenName = replyFrom.getScreenName(); // izugch4423 (@なし)
		String userName = replyFrom.getName(); // 城崎伊澄

		// 覚えてるかどうか聞かれた時、照合する
		String text = status.getText();
		if (text.indexOf("のことおぼえてる？") != -1 || text.indexOf("のこと覚えてる？") != -1
				|| text.indexOf("のこと憶えてる？") != -1) {

			User target = status.getUser();

			System.out.println("【リプライをくれた人が保存されているか検索しています...】");

			IDs readedIds = getUserData_Followers(target, isDebug);
			// User readedUser = (User) readed[0];
			// IDs readedIds = (IDs) readed[1];
			if (null == readedIds) {

				StringBuilder sb = new StringBuilder("@");
				sb.append(target.getScreenName() + " ");
				sb.append(target.getName() + " さんのこと、ちょっとわからないです。でも、今おぼえました。");

				// 覚える（勝手に）
				rememberFollower(twitter, status, isDebug);

				// 憶えたことを喋る
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
				sb.append(target.getName() + " さんのこと、おぼえてましたけど、おぼえなおしました。");
				// 勝手に更新する
				rememberFollower(twitter, status, isDebug);
				statusUpdateIfIsNotDebugging(twitter, status, sb.toString(),
						isDebug);

				// 今現在のフォロワー
				IDs nowFollowerIds = twitter
						.getFollowersIDs(target.getId(), -1);

				// 前憶えたときのフォロワー一覧Set
				Set<Long> before = new HashSet<Long>();
				for (Long l : readedIds.getIDs())
					before.add(l);

				// 今現在のフォロワー一覧Set
				Set<Long> after = new HashSet<Long>();
				for (Long l : nowFollowerIds.getIDs())
					after.add(l);

				// 差分（減ってるときだけ）
				before.removeAll(after);

				if (before.isEmpty()) {

					// お変りない
					statusUpdateDirectMessageIfIsNotDebugging(twitter,
							target.getScreenName(), "フォロワーに変化はないようですね～！",
							isDebug);

				} else {

					// リムーブされた人のリストを作る
					StringBuilder result = new StringBuilder("以下の人にリムーブされてます。");
					for (Long l : before) {
						result.append(System.lineSeparator());
						User removedUser = twitter.showUser(l);
						result.append("・" + removedUser.getName() + "("
								+ removedUser.getScreenName() + ")");
					}

					// 結果をDMする
					statusUpdateDirectMessageIfIsNotDebugging(twitter,
							target.getScreenName(), result.toString(), isDebug);

				}

			}

			return;

		} else if (text.indexOf("のことおぼえて") != -1
				|| text.indexOf("のこと覚えて") != -1 || text.indexOf("のこと憶えて") != -1) {
			// 覚えて欲しい場合は、覚えてあげる

			// 「~さんのこと、覚えましたよ～」というセリフをツイートする。
			String result = rememberFollower(twitter, status, isDebug);
			statusUpdateIfIsNotDebugging(twitter, status, result, isDebug);

			return;

		} else if (text.endsWith("ステータス")) {

			EXPStatus targetUser = null;
			for (EXPStatus es : expStatuses) {
				if (userScreenName.equals(es.getUser().getScreenName())) {
					targetUser = es;
					break;
				}
			}
			// サーチして存在してなかったら新規
			if (targetUser == null) {
				targetUser = new EXPStatus(replyFrom);
				targetUser.addExp(1);
				expStatuses.add(targetUser);
			}

			// ステータスを表示する。
			StringBuilder sb = new StringBuilder("@");
			sb.append(userScreenName + " ");
			sb.append(userName + "さんのレベルは" + targetUser.getLevel() + "です。");
			sb.append(System.lineSeparator());
			sb.append("現在の経験値は " + targetUser.getExp() + "exp です。");
			sb.append(System.lineSeparator());
			sb.append("次のレベルまであと " + targetUser.getNextLevelExperience()
					+ "exp 必要です。");
			sb.append(System.lineSeparator());
			sb.append("#zasshoku_bot_exp");

			statusUpdateIfIsNotDebugging(twitter, status, sb.toString(),
					isDebug);

		}

		// ////////////////////////////////////// // ここからは絶対やる

		boolean ifLevelUp = false;
		EXPStatus targetUser = null;
		// リプライをくれたので、1exp
		for (EXPStatus es : expStatuses) {
			if (userScreenName.equals(es.getUser().getScreenName())) {
				ifLevelUp = es.setExp(es.getExp() + 1);
				targetUser = es;
				System.out.println("@" + userScreenName + "さんに経験値を1付与しました。");
				System.out.println("現在経験値 " + es.getExp());
				break;
			}
		}
		// サーチして存在してなかったら新規
		if (targetUser == null) {
			targetUser = new EXPStatus(replyFrom);
			targetUser.addExp(1);
			expStatuses.add(targetUser);
		}

		// 返す文字列を作る
		try {

			// 成功するまでマルコフ文字列を作る
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
				// レベルアップ情報
				StringBuilder sb2 = new StringBuilder("@");
				sb2.append(userScreenName + " ");
				sb2.append("レベルアップ！" + (targetUser.getLevel() - 1) + "→"
						+ targetUser.getLevel() + "　次のレベルまで"); // レベルは既に上がってるので、(-1)→(±0)
				sb2.append(targetUser.getNextLevelExperience() + "exp");
				sb2.append(System.lineSeparator());
				sb2.append("#zasshoku_bot_exp");

				statusUpdateIfIsNotDebugging(twitter, status, sb2.toString(),
						isDebug);
			}

		} catch (Exception e) {
			// たぶんツイートに失敗してる。
			return;
		}

		// ユーザーのレベルアップ情報保存
		saveToUserData_Experiences(expStatuses);

	}

	/**
	 * HomeTimeLine拾って、5件以上リツイートがあるツイートがあるなら便乗する。 ただしそれがRTならしない。
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
			// System.out.println(Main.getTime() + "【リツイート】" +
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
						.println("[Marcov processing error] 文章を適当に作るのに失敗したのでもっかいやります。");
				thread.stopThread();

				return false;

			}

		} catch (InterruptedException e) {

			e.printStackTrace();

		}

		return true;

	}

	/**
	 * ひとのことを覚える。喋るなら、戻った値をstatus Updateする。<br>
	 * （これを呼び出しただけだと、保存するが何も喋らないので受け取り手には分からない）
	 * 
	 * @param twitter
	 * @param status
	 *            "憶えてくれ～"って言った人のツイート。ユーザ情報はここから奪う。
	 * @param isDebug
	 * @return "@getScreenName() getName()さんのこと、覚えましたよ～。"
	 */
	private String rememberFollower(Twitter twitter, Status status,
			boolean isDebug) throws TwitterException {

		User target = status.getUser();
		saveToUserData_Followers(target,
				twitter.getFollowersIDs(target.getId(), -1));

		StringBuilder sb = new StringBuilder("@");
		sb.append(target.getScreenName() + " ");
		sb.append(target.getName() + " さんのこと、おぼえましたよ～。");

		System.out.println(Main.getTime() + " 【情報の記憶】"
				+ status.getUser().getName() + "(@"
				+ status.getUser().getScreenName()
				+ ")のフォロワー一覧を保存しました。follower="
				+ twitter.getFollowersIDs(target.getId(), -1).getIDs().length);

		String result = sb.toString();
		return result;

	}

	/**
	 * 
	 * つぶやく。コンソールへのデバッグ文字列は必ず出す。
	 * 
	 * @param twitter
	 * @param status
	 *            リプライ先ステータス。nullも可
	 * @param text
	 * @param isDebug
	 *            falseだとコンソールに吐く。trueだとつぶやく。
	 * @throws TwitterException
	 */
	private void statusUpdateIfIsNotDebugging(Twitter twitter, Status status,
			String text, boolean isDebug) throws TwitterException {

		if (!isDebug) {
			StatusUpdate sup = new StatusUpdate(text);
			if (status != null) {
				// リプライ先がある
				sup.inReplyToStatusId(status.getId());
			}
			twitter.updateStatus(sup);
		}

		// 【コンソール】
		// リプライ先がある場合、そのIDを出す
		System.out.println(Main.getTime()
				+ " "
				+ text
				+ (status != null ? " [STATUS_ID = " + status.getId()
						+ " への返信です]" : ""));

	}

	/**
	 * リツイート版。
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

		// 【コンソール】
		System.out.println(Main.getTime() + " 【次をRTしました】"
				+ targetTweet.getText());

	}

	/**
	 * DM版。
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

		// 【コンソール】
		System.out.println(Main.getTime() + " 【DMを送付しました】 @" + getScreenName
				+ " " + message);

	}

	// ユーザ情報（フォロワー）保存
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
				System.out.println("【ユーザ読み出しの失敗】"
						+ "データファイルはあるけど、なんかロックかしらんけど読めなかった");
				e.printStackTrace();
			}

		}

		return null;

	}

	// ユーザ情報（フォロワー）保存
	private static String USER_DATA_EXP_FILE_PATH = "usersEXPdata.dat";

	/**
	 * 全ユーザ分1ファイル
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
				System.out.println("【ユーザ読み出しの失敗2】"
						+ "データファイルはあるけど、なんかロックかしらんけど読めなかった");
				e.printStackTrace();
			}

		}

		return null;

	}

}
