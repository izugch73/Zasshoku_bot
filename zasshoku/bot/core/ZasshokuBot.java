package zasshoku.bot.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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

	private long FIVE_MINUTE_MILLISEC = 300_000; // �������A5�������B300_000�����B
	private String ACCESS_TOKEN_FILE_PATH = "./asset/atoken.token";

	private Twitter twitter = null;
	private static final String consumerKey = "FXGrKhbKwqV61PywAqUYQ";
	private static final String consumerSecret = "STyrMORbdLfYNtvPXxPG6hO5k47U9nBPUeNTaMwgpI";
	private static final String accessToken = "942588480-WOuousMNNqjeP6027xqinaqMjZNIbcMifumOoq14";
	private static final String accessTokenSecret = "34ZqVhSMLvAoIvrG0yv26T5ydITawMnOqkKT09FV1M";
	private AccessToken token;

	boolean isDebug = false;

	public ZasshokuBot(String arg) {

		if ("-Debug".equals(arg)) {
			isDebug = true;
		}

		// �F��
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
			// ���[�U���x����񂪓ǂݏo���Ȃ��Ȃ�V�K�쐬����B
			// �N���������ǂݍ��܂Ȃ��i���i�̓I���������j�B�������ނ̂̓��v���C�Ƃ�RT�Ƃ��N���̌o���l���オ�����炻�̓s�x�B
			expStatuses = new ArrayList<EXPStatus>();
		}

		while (true) {
			ResponseList<Status> homeTimeline = null;

			try {

				// int rand = new Random().nextInt(10);
				tweetCounter++;
				replyCount = 0;

				// �擾�ς݊֌W�Ȃ��ŋ߂���200��
				Paging page = new Paging(1, 200);
				homeTimeline = twitter.getHomeTimeline(page);
				List<String> textList = new ArrayList<>(200);
				for (Status status : homeTimeline) {
					if (status.getText().indexOf("http") != -1)
						continue;
					textList.add(status.getText());
				}

				// ���ʏ�̂Ԃ₫
				if (tweetCounter > 11) {
					tweetCounter = 0;

					while (!sa.getMarcovText(textList))
						;
					String result = MarcovGetterThread.getResult();
					result = result.replaceAll("#", "");

					// �y�ʏ�̂Ԃ₫�z
					System.out.println(Main.getTime() + " " + result);
					if (!isDebug)
						twitter.updateStatus(result);

				}

				// ���^�C�����C���E��

				// HomeTimeline�擾
				List<Status> recentHomeTimeline = getRecentHomeTimeline(twitter);
				// mentionTimeline�擾
				List<Status> mentionTimeline = getRecentMentionTimeline(twitter);

				// �t���O
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

				System.out.println("�c�C�[�g�Ɏ��s���܂����B5����ɍĒ��킵�܂��B");

				if (homeTimeline != null) {
					RateLimitStatus rateLimitStatus = homeTimeline
							.getRateLimitStatus();
					int untilReset = rateLimitStatus.getSecondsUntilReset();
					System.out.println(Main.getTime() + " API���������H���Z�b�g�܂ł��� "
							+ untilReset + "sec");
				}

				try {
					Thread.sleep(FIVE_MINUTE_MILLISEC);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
					System.out.println("5���҂ĂȂ������̂ł������܂��B");
				}

				continue;

			}

		}

	}

	/**
	 * �O��擾����Status�ȍ~��List��Ԃ��B �i�����ɂ̓A�N�V�������Ă��Ȃ��j
	 * 
	 * @param twitter
	 * @throws TwitterException
	 */
	public List<Status> getRecentHomeTimeline(Twitter twitter)
			throws TwitterException {

		Paging page = new Paging(1, 200);
		ResponseList<Status> homeTimeline = twitter.getHomeTimeline(page);

		List<Status> returnList = new ArrayList<Status>();

		// �ŏ�
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
	 * �O��擾����Status�ȍ~��List��Ԃ��B �i�����ɂ̓A�N�V�������Ă��Ȃ��j
	 * 
	 * @param twitter
	 * @throws TwitterException
	 */
	public List<Status> getRecentMentionTimeline(Twitter twitter)
			throws TwitterException {

		Paging page = new Paging(1, 200);
		ResponseList<Status> homeTimeline = twitter.getMentionsTimeline(page);

		List<Status> returnList = new ArrayList<Status>();

		// �ŏ�
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
	 * �t�@�C������A�N�Z�X�g�[�N����ǂ�
	 * 
	 * @param context
	 * @return true OK: false NG
	 */
	public boolean readAccessTokenFromFile() {

		File accessTokenFile = new File(ACCESS_TOKEN_FILE_PATH);

		if (accessTokenFile.exists()) {
			try {
				FileInputStream input = new FileInputStream(
						accessTokenFile.getName());
				ObjectInputStream inObj = new ObjectInputStream(input);
				this.token = (AccessToken) inObj.readObject();
				inObj.close();
				input.close();
				return true;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return false;

	}

	/**
	 * �A�N�Z�X�g�[�N����ۑ�
	 * 
	 * @param context
	 */
	public void writeAccessTokenToFile() {
		File accessTokenFile = new File(ACCESS_TOKEN_FILE_PATH);
		try {
			FileOutputStream output = new FileOutputStream(
					accessTokenFile.getName());
			ObjectOutputStream outObject = new ObjectOutputStream(output);
			outObject.writeObject(this.token);

			outObject.close();
			output.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
