package zasshoku.bot.core;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import twitter4j.Paging;
import twitter4j.RateLimitStatus;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import zasshoku.bot.bean.EXPStatus;
import zasshoku.bot.engine.MarcovGetterThread;

public class ZasshokuBot implements Runnable {

	private long FIVE_MINUTE_MILLISEC = 300_000; // いいか、5分だぞ。300_000だぞ。
	private String ACCESS_TOKEN_FILE_PATH = "./asset/atoken.token";

	private Twitter twitter = null;
	private static final String consumerKey = "FXGrKhbKwqV61PywAqUYQ";
	private static final String consumerSecret = "STyrMORbdLfYNtvPXxPG6hO5k47U9nBPUeNTaMwgpI";
	private static final String accessToken = "942588480-WOuousMNNqjeP6027xqinaqMjZNIbcMifumOoq14";
	private static final String accessTokenSecret = "34ZqVhSMLvAoIvrG0yv26T5ydITawMnOqkKT09FV1M";
	private AccessToken token;

	private boolean isDebug = false;

	/**
	 * コンストラクタ<br>
	 *     ・モード（デバッグ・通常）の判定<br>
	 *     ・アカウント認証
	 *
	 * @param arg 空文字（通常）もしくは "-Debug"（デバッグモード）
     */
	public ZasshokuBot(String arg) {

		if (Main.DEBUG_MODE.equals(arg)) {
			isDebug = true;
		}

		// 認証
		twitter = TwitterFactory.getSingleton();

		twitter.setOAuthConsumer(consumerKey, consumerSecret);
		if (readAccessTokenFromFile() == false) {
			token = new AccessToken(accessToken, accessTokenSecret);
		}
		twitter.setOAuthAccessToken(token);

	}

	private static Status latestHomeTimelineStatus = null;
	private static Status latestMentionTimelineStatus = null;
	private int tweetCounter = 11;
	private int replyCount = 0;

	private List<EXPStatus> expStatuses = null;

	@Override
	public void run() {

		StatusAnalyzer sa = new StatusAnalyzer();

		expStatuses = sa.getUserData_Experiences(isDebug);
		if (expStatuses == null) {
			// ユーザレベル情報が読み出せないなら新規作成する。
			// 起動時しか読み込まない（普段はオンメモリ）。書き込むのはリプライとかRTとか誰かの経験値が上がったらその都度。
			expStatuses = new ArrayList<EXPStatus>();
		}

		while (true) {
			ResponseList<Status> homeTimeline = null;

			try {

				// int rand = new Random().nextInt(10);
				tweetCounter++;
				replyCount = 0;

				// 取得済み関係なく最近から200語
				Paging page = new Paging(1, 200);
				homeTimeline = twitter.getHomeTimeline(page);
				List<String> textList = new ArrayList<>(200);
				for (Status status : homeTimeline) {
					if (status.getText().indexOf("http") != -1)
						continue;
					textList.add(status.getText());
				}

				// ◆通常のつぶやき
				if (tweetCounter > 11) {
					tweetCounter = 0;

					while (!sa.getMarcovText(textList))
						;
					String result = MarcovGetterThread.getResult();
					result = result.replaceAll("#", "");

					// 【通常のつぶやき】
					System.out.println(Main.getTime() + " " + result);
					if (!isDebug)
						twitter.updateStatus(result);

				}

				// ◆タイムライン拾い

				// HomeTimeline取得
				List<Status> recentHomeTimeline = getRecentHomeTimeline(twitter);
				// mentionTimeline取得
				List<Status> mentionTimeline = getRecentMentionTimeline(twitter);

				// フラグ
				for (Status status : recentHomeTimeline) {

					sa.retweetToo(twitter, status, isDebug);

					// sa.bottoYondaka(twitter, status, isDebug);

				}

				for (Status status : mentionTimeline) {

					// sa.checkFrendship(twitter, status);

					sa.checkReplyFromZasshoku(twitter, status, textList,
							replyCount++, expStatuses, isDebug);

				}

				// Thread.sleep(300000);
				Thread.sleep(FIVE_MINUTE_MILLISEC);

			} catch (InterruptedException | TwitterException | IOException e) {
				e.printStackTrace();

				System.out.println("ツイートに失敗しました。5分後に再挑戦します。");

				if (homeTimeline != null) {
					RateLimitStatus rateLimitStatus = homeTimeline
							.getRateLimitStatus();
					int untilReset = rateLimitStatus.getSecondsUntilReset();
					System.out.println(Main.getTime() + " API制限かも？リセットまであと "
							+ untilReset + "sec");
				}

				try {
					Thread.sleep(FIVE_MINUTE_MILLISEC);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
					System.out.println("5分待てなかったのですぐやります。");
				}

				continue;

			}

		}

	}

	/**
	 * 前回取得したStatus以降のListを返す。 （これらにはアクションしていない）
	 * 
	 * @param twitter
	 * @throws TwitterException
	 */
	public List<Status> getRecentHomeTimeline(Twitter twitter)
			throws TwitterException {

		Paging page = new Paging(1, 200);
		ResponseList<Status> homeTimeline = twitter.getHomeTimeline(page);

		List<Status> returnList = new ArrayList<Status>();

		// 最初
		if (latestHomeTimelineStatus == null) {
			latestHomeTimelineStatus = homeTimeline.get(0);
			return homeTimeline;
		}

		for (Status status : homeTimeline) {
			if (latestHomeTimelineStatus.getId() != status.getId()) {
				returnList.add(status);
			} else {
				break;
			}
		}

		latestHomeTimelineStatus = homeTimeline.get(0);

		return returnList;
	}

	/**
	 * 前回取得したStatus以降のListを返す。 （これらにはアクションしていない）
	 * 
	 * @param twitter
	 * @throws TwitterException
	 */
	public List<Status> getRecentMentionTimeline(Twitter twitter)
			throws TwitterException {

		Paging page = new Paging(1, 200);
		ResponseList<Status> homeTimeline = twitter.getMentionsTimeline(page);

		List<Status> returnList = new ArrayList<Status>();

		// 最初
		if (latestMentionTimelineStatus == null) {
			latestMentionTimelineStatus = homeTimeline.get(0);
			return homeTimeline;
		}

		for (Status status : homeTimeline) {
			if (latestMentionTimelineStatus.getId() != status.getId()) {
				returnList.add(status);
			} else {
				break;
			}
		}

		latestMentionTimelineStatus = homeTimeline.get(0);

		return returnList;
	}

	/**
	 * ファイルからアクセストークンを読む
	 * 
	 * @return true OK: false NG
	 */
	public boolean readAccessTokenFromFile() {

		File accessTokenFile = new File(ACCESS_TOKEN_FILE_PATH);

		if (accessTokenFile.exists()) {

			try (ObjectInputStream inObj = new ObjectInputStream(new FileInputStream(accessTokenFile.getName()))) {

				this.token = (AccessToken) inObj.readObject();
				return true;

			} catch (Exception e) {
				e.printStackTrace();
			}

		}
		return false;

	}

//	/**
//	 * アクセストークンを保存
//	 *
//	 */
//	public void writeAccessTokenToFile() {
//
//		File accessTokenFile = new File(ACCESS_TOKEN_FILE_PATH);
//
//		try(ObjectOutputStream outObject = new ObjectOutputStream(
//				new FileOutputStream(accessTokenFile.getName()))) {
//
//			outObject.writeObject(this.token);
//
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//
//	}
}
