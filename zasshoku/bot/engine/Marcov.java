package zasshoku.bot.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.java.sen.SenFactory;
import net.java.sen.StringTagger;
import net.java.sen.dictionary.Token;

public class Marcov {
	ArrayList<StringData> prefix1 = new ArrayList<StringData>();

	ArrayList<StringData> prefix2 = new ArrayList<StringData>();

	ArrayList<StringData> prefix3 = new ArrayList<StringData>();

	ArrayList<StringData> suffix = new ArrayList<StringData>();

	private List<String> stringTable;

	public Marcov(List<String> stringTable) {
		this.stringTable = stringTable;
		func();
	}

	public String func() {
		StringTagger tagger = SenFactory.getStringTagger(null);
		List<Token> tokens = new ArrayList<>();

		for (String str : stringTable) {

			try {
				tagger.analyze(str.toString(), tokens);
			} catch (Exception e) {
				System.out.println("[System] " + str + " が解析できません。");
				continue;
			}

			for (int cnt2 = 0; cnt2 < tokens.size() - 1; cnt2++) {
				StringData pre1 = new StringData();
				StringData pre2 = new StringData();
				StringData pre3 = new StringData();
				StringData suf = new StringData();

				pre1.setData(tokens.get(cnt2).getSurface());
				pre2.setData(tokens.get(cnt2 + 1).getSurface());

				try {
					pre3.setData(tokens.get(cnt2 + 2).getSurface());
				} catch (IndexOutOfBoundsException e1) {
					pre3.setData("");
				}
				try {

					suf.setData(tokens.get(cnt2 + 3).getSurface());
				} catch (IndexOutOfBoundsException e) {
					suf.setData("");
				}

				if (cnt2 == 0) {
					pre1.setStart(true);
				}

				prefix1.add(pre1);
				prefix2.add(pre2);
				prefix3.add(pre3);
				suffix.add(suf);

				// System.out.print(prefix1.get(cnt2).getData() + "\t");
				// System.out.print(prefix2.get(cnt2).getData() + "\t");
				// System.out.print(prefix3.get(cnt2).getData() + "\t");
				// System.out.print(suffix.get(cnt2).getData());
				// System.out.println();
			}
		}

		String key;

		ArrayList firstWord = new ArrayList();

		for (StringData sData : prefix1) {
			if (sData.isStart()) {
				firstWord.add(sData.getData());
			}
		}

		key = (String) firstWord.get(new Random().nextInt(firstWord.size()));

		StringBuilder ansStr = new StringBuilder("");

		while (true) {

			ArrayList<WordMix> searchResult = new ArrayList<>();

			for (int cnt = 0; cnt < suffix.size(); cnt++) {
				if (prefix1.get(cnt).getData().equals(key)) {
					WordMix wordMix = new WordMix();
					wordMix.setPrefix1(prefix1.get(cnt));
					wordMix.setPrefix2(prefix2.get(cnt));
					wordMix.setPrefix3(prefix3.get(cnt));
					wordMix.setSuffix(suffix.get(cnt));
					wordMix.setIndex(cnt);

					searchResult.add(wordMix);
				}
			}
			if (searchResult.size() <= 0) {
				break;
			}

			int index1 = new Random().nextInt(searchResult.size());

			String choiceStr = searchResult.get(index1).getSim();

			prefix1.get(searchResult.get(index1).getIndex()).setRead(true);
			prefix2.get(searchResult.get(index1).getIndex()).setRead(true);
			suffix.get(searchResult.get(index1).getIndex()).setRead(true);

			ansStr.append(choiceStr);

			key = searchResult.get(index1).getSuffix().getData();
		}

		return ansStr.toString();
		// System.out.println("ansStr:" + ansStr);
	}
}

class StringData {
	private String data;

	private boolean isRead;

	private boolean isStart;

	public StringData() {
		this.isRead = false;
		this.isStart = false;
	}

	public String getData() {
		return data;
	}

	public void setData(String data) {
		this.data = data;
	}

	public boolean isRead() {
		return isRead;
	}

	public void setRead(boolean isRead) {
		this.isRead = isRead;
	}

	public boolean isStart() {
		return isStart;
	}

	public void setStart(boolean isStart) {
		this.isStart = isStart;
	}
}

class WordMix {
	private StringData prefix1;

	private StringData prefix2;

	private StringData prefix3;

	private StringData suffix;

	private int index;

	public String getSim() {
		return getPrefix1().getData() + getPrefix2().getData()
				+ getPrefix3().getData();
	}

	public StringData getPrefix1() {
		return prefix1;
	}

	public void setPrefix1(StringData prefix1) {
		this.prefix1 = prefix1;
	}

	public StringData getPrefix2() {
		return prefix2;
	}

	public void setPrefix2(StringData prefix2) {
		this.prefix2 = prefix2;
	}

	public StringData getPrefix3() {
		return prefix3;
	}

	public void setPrefix3(StringData prefix3) {
		this.prefix3 = prefix3;
	}

	public StringData getSuffix() {
		return suffix;
	}

	public void setSuffix(StringData suffix) {
		this.suffix = suffix;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}
}