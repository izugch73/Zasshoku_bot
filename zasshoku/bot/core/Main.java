package zasshoku.bot.core;

import java.text.SimpleDateFormat;
import java.util.Calendar;

public class Main {

	public static final String DEBUG_MODE = "-Debug";


	/**
	 * 引数が何か（なんでも良い）渡ってきたらデバッグモードで起動する。
	 *
	 * @param args
     */
	public static void main(String[] args) {

		if (args.length > 0) {

			Thread th = new Thread(new ZasshokuBot(DEBUG_MODE));
			System.out.println(Main.getTime() + " 【System】 デバッグモードで起動します。");
			th.start();

		} else {

			Thread th = new Thread(new ZasshokuBot(""));
			System.out.println(Main.getTime() + " 【System】 本番モードで起動します。");
			th.start();

		}

	}

	/**
	 * 現在時刻を返す
	 * @return
     */
	public static String getTime() {

		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("[yyyy.MM.dd HH:mm:ss]");
		String strDate = sdf.format(cal.getTime());

		return strDate;

	}

}
