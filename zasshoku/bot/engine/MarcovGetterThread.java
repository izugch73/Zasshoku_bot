package zasshoku.bot.engine;

import java.util.List;

public class MarcovGetterThread extends Thread {

	public static String resultString = "";

	public static String getResult() {
		return resultString;
	}

	private boolean threadRunning = false;

	private List<String> stringTable = null;

	public MarcovGetterThread(List<String> stringTable) {
		this.stringTable = stringTable;
		threadRunning = true;
	}

	public void stopThread() {
		threadRunning = false;
	}

	@Override
	public void run() {

		Marcov marcov = new Marcov(stringTable);

		while (threadRunning) {

			resultString = marcov.func();
			threadRunning = false;

		}

	}

}
