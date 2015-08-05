package zasshoku.bot.core;

import java.text.SimpleDateFormat;
import java.util.Calendar;

public class Main {

	public static void main(String[] args) {

		if (args.length > 0) {
			Thread th = new Thread(new ZasshokuBot("-Debug"));
			System.out.println(Main.getTime() + " �ySystem�z �f�o�b�O���[�h�ŋN�����܂��B");
			th.start();

		} else {
			Thread th = new Thread(new ZasshokuBot(""));
			System.out.println(Main.getTime() + " �ySystem�z �{�ԃ��[�h�ŋN�����܂��B");
			th.start();

		}

	}

	public static String getTime() {

		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("[yyyy.MM.dd HH:mm:ss]");
		String strDate = sdf.format(cal.getTime());

		return strDate;

	}

}
