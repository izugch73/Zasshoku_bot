package zasshoku.bot.core;

import java.io.*;
import java.nio.file.*;
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
     * ・モード（デバッグ・通常）の判定<br>
     * ・アカウント認証
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

        // アクセストークンファイルを作っていなければ、トークンを生成して保持
        if (readAccessTokenFromFile() == false) {
            token = new AccessToken(accessToken, accessTokenSecret);
            writeAccessTokenToFile(token);
        }//	trueだったらthis.tokenに読まれている

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
            expStatuses = new ArrayList<>();
        }

        for (; ; ) {
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
                List<Status> recentHomeTimeline = getRecentTimeline(TimeLineType.HOME);
                // mentionTimeline取得
                List<Status> mentionTimeline = getRecentTimeline(TimeLineType.MENTION);

                // 便乗RT判定。TLに5RT以上のRTでないツイートが流れていたら、RTする。
                for (Status status : recentHomeTimeline) {
                    sa.retweetToo(twitter, status, isDebug);
                }

                // リプライ判定。誰かからリプライが来ているため、その返事を用意する。
                for (Status status : mentionTimeline) {
                    sa.checkReplyFromZasshoku(twitter, status, textList,
                            replyCount++, expStatuses, isDebug);
                }

                // 5分待つ
                Thread.sleep(FIVE_MINUTE_MILLISEC);

            } catch (InterruptedException | TwitterException | IOException e) {

                e.printStackTrace();

                System.out.println("ツイートに失敗しました。5分後に再挑戦します。");


                // API制限を疑う。でも概ね「Duplicate tweet」だと思う。
                if (homeTimeline != null) {
                    RateLimitStatus rateLimitStatus = homeTimeline
                            .getRateLimitStatus();
                    int untilReset = rateLimitStatus.getSecondsUntilReset();
                    System.out.println(Main.getTime() + " API制限かも？リセットまであと "
                            + untilReset + "sec");
                }

                try {
                    // 5分待つ
                    Thread.sleep(FIVE_MINUTE_MILLISEC);
                } catch (InterruptedException e1) {
                    // 5分待つことすらできないクズ
                    e1.printStackTrace();
                    System.out.println("5分待てなかったのですぐやります。");
                }

                continue;

            }

        }

    }


    private enum TimeLineType {
        HOME, MENTION
    }

    /**
     * 前回取得したStatus以降のListを返す。HomeかMemtionは引数で選択。
     *
     * @param type ホームかリプライ
     * @return 差分だけのツイート
     * @throws TwitterException
     */
    private List<Status> getRecentTimeline(TimeLineType type) throws TwitterException {

        Status latestTimelineStatus = type == TimeLineType.HOME ? this.latestHomeTimelineStatus : this.latestMentionTimelineStatus;

        Paging page = new Paging(1, 200);
        ResponseList<Status> timeline = type == TimeLineType.HOME ? this.twitter.getHomeTimeline(page) : this.twitter.getMentionsTimeline(page);

        List<Status> returnList = new ArrayList<>();

        // そもそも取得していない時（起動時など）の処理
        if (latestTimelineStatus == null) {
            if (type == TimeLineType.HOME)
                this.latestHomeTimelineStatus = timeline.get(0);
            else
                this.latestMentionTimelineStatus = timeline.get(0);
            return timeline;
        }

        // 差分のみreturnListへ追加
        for (Status status : timeline) {
            if (latestTimelineStatus.getId() != status.getId()) {
                returnList.add(status);
            } else {
                break;
            }
        }

        // 現在の先頭のツイートIDをlatestとして登録
        if (type == TimeLineType.HOME)
            this.latestHomeTimelineStatus = timeline.get(0);
        else
            this.latestMentionTimelineStatus = timeline.get(0);

        return returnList;

    }

    /**
     * ファイルからアクセストークンを読む
     *
     * @return true OK: false NG
     */
    public boolean readAccessTokenFromFile() {

        Path accessTokenFilePath = FileSystems.getDefault().getPath(ACCESS_TOKEN_FILE_PATH);

        if (accessTokenFilePath.toFile().exists()) {

            try (ObjectInputStream io = new ObjectInputStream(Files.newInputStream(accessTokenFilePath, StandardOpenOption.READ))) {

                this.token = (AccessToken) io.readObject();
                return true;

            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        return false;

    }

    /**
     * アクセストークンを保存
     *
     * @param target 保存するアクセストークン
     */
    public void writeAccessTokenToFile(AccessToken target) {

        Path accessTokenFilePath = FileSystems.getDefault().getPath(ACCESS_TOKEN_FILE_PATH);

        try (ObjectOutputStream io = new ObjectOutputStream(Files.newOutputStream(accessTokenFilePath, StandardOpenOption.WRITE))) {

            io.writeObject(target);

        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
