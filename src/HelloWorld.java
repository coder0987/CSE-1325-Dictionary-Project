import java.net.*;
import java.io.*;
import org.json.*;
import java.util.*;

class URLConnectionReader {
    public static BufferedReader getHTTP(String url) throws Exception {
        URL connection = new URL(url);
        URLConnection yc = connection.openConnection();
        return new BufferedReader(
                new InputStreamReader(
                        yc.getInputStream()));
    }
    public static JSONObject getJSONFromBufferedReader(BufferedReader br) throws IOException {
        String inputLine;
        StringBuilder finalJSON = new StringBuilder();
        finalJSON.append("{\"data\":");

        while ((inputLine = br.readLine()) != null) {
            finalJSON.append(inputLine);

        }
        br.close();
        finalJSON.append("}");
        //System.out.println(finalJSON);
        return new JSONObject(finalJSON.toString());
    }

    public static JSONObject apiFetch(String url) {
        try {
            return getJSONFromBufferedReader(getHTTP(url));
        } catch (Exception e) {
            return null;
        }
    }
}

public class HelloWorld {
    public static void main(String[] args) {
        System.out.println("Define any word! Loading...");
        Scanner sc = new Scanner(System.in); //System.in is a standard input stream

        StringSimilarity similarityDetector = null;
        try {
            similarityDetector = new StringSimilarity("src/words.txt");
            System.out.println(similarityDetector.everyWord.size() + " words loaded");
        } catch (IOException e) {
            System.out.println("Unable to process similar words: " + e);
        }

        int numSimilarWords = 3;
        boolean random = false;

        String str = "hello";
        while (!str.equals("--end") && !str.equals("--e")) {
            if (str.equals("--random") || str.equals("--r") && similarityDetector != null) {
                random = true;
                str = similarityDetector.everyWord.get((int) (Math.random()*similarityDetector.everyWord.size()));
            } else if (str.equals("--help") || str.equals("--h")) {
                System.out.println("--end or --e: end the program");
                System.out.println("--help or --h: list all commands");
                System.out.println("--random or --r: define a random word");
                System.out.print("> ");
                str = sc.nextLine();
                continue;
	    }
		else if (str.equals("--say") || str.equals("--s")) {
			try {new SaySomething("Hello Two");} catch (IOException e){}
			str = sc.nextLine();
			continue;
            } else if (str.isEmpty()) {
                System.out.print("> ");
                str = sc.nextLine();
                continue;
            }

	    // Handle strings with multiple words.
	    String[] temp = str.split(" ");
	    if (temp.length > 1) {
		    try{
		    	new SaySomething("src/file.txt");
		    } catch(IOException e) {
		    }
                System.out.print("> ");
                str = sc.nextLine();
		    continue;
	    }

            String def = defineWord(str);
            if (def == null && similarityDetector != null) {
                if (random) {
                    str = "--random";
                    continue;
                } else {
                    System.out.println("No definitions for '" + str + "' were found :(");
                    String[] similarWords = similarityDetector.findClosest(str, numSimilarWords);
                    System.out.print("Possible Matches:");
                    for (int i = 0; i < numSimilarWords; i++) {
                        System.out.print(" " + similarWords[i]);
                    }
                    System.out.println("\n-- Please note that not every word is in the dictionary --\n");
                }
            } else if (def == null) {
                System.out.println("No definitions for '" + str + "' were found :(");
            } else {
                System.out.println(def);
            }
            random = false;
            System.out.println("Enter '--end' to stop. Use '--help' for a list of all commands");
            System.out.print("What word would you like to define?\n> ");
            str = sc.nextLine();
        }
    }
    public static String defineWord(String word) {
        String baseURL = "https://api.dictionaryapi.dev/api/v2/entries/en/";
        JSONObject definition = URLConnectionReader.apiFetch(baseURL + word);
        if (definition == null) {
            return null;
        }
        String returnDefinition = "";
        try {
            JSONArray item = definition.getJSONArray("data");
            JSONObject wordData = item.getJSONObject(0);
            returnDefinition += wordData.getString("word");
            returnDefinition += "\n";

            JSONArray meanings = wordData.getJSONArray("meanings");

            for (int i=0; i<meanings.length(); i++) {
                returnDefinition += (i+1) + " - " + meanings.getJSONObject(i).getString("partOfSpeech") + ":\n";
                JSONArray variousDefinitions = meanings.getJSONObject(i).getJSONArray("definitions");
                for (int j = 0; j < variousDefinitions.length(); j++) {
                    returnDefinition += " - " + variousDefinitions.getJSONObject(j).getString("definition") + "\n";
                    //Synonyms are here
                }
            }
        } catch (Exception e) {
            System.out.println("Error: " + e);
            System.out.println(definition);
        }


        return returnDefinition;
    }
}

class StringSimilarity {
    ArrayList<String> everyWord;
    public StringSimilarity(String filename) throws IOException {
        everyWord = new ArrayList<>();
        BufferedReader bf = new BufferedReader(
                new FileReader(filename));
        String line = bf.readLine();
        while (line != null) {
            everyWord.add(line);
            line = bf.readLine();
        }
        bf.close();
    }
    public static double similarity(String s1, String s2) {
        String longer = s1, shorter = s2;
        if (s1.length() < s2.length()) {
            longer = s2;
            shorter = s1;
        }
        int longerLength = longer.length();
        if (longerLength == 0) {
            return 1.0;
        }
        return (longerLength - editDistance(longer, shorter)) / (double) longerLength;

    }

    public static int editDistance(String s1, String s2) {
        s1 = s1.toLowerCase();
        s2 = s2.toLowerCase();

        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0)
                    costs[j] = j;
                else {
                    if (j > 0) {
                        int newValue = costs[j - 1];
                        if (s1.charAt(i - 1) != s2.charAt(j - 1))
                            newValue = Math.min(Math.min(newValue, lastValue),
                                    costs[j]) + 1;
                        costs[j - 1] = lastValue;
                        lastValue = newValue;
                    }
                }
            }
            if (i > 0)
                costs[s2.length()] = lastValue;
        }
        return costs[s2.length()];
    }

    public String[] findClosest(String base, int howMany) {
        ArrayList<StringScore> temp = new ArrayList<StringScore>();
        if (howMany == 0) {return null;}
        for (int i = 0; i < everyWord.size(); i++) {
            StringScore current = new StringScore(everyWord.get(i),(int)(similarity(everyWord.get(i), base) * 100));
            if (temp.isEmpty()) {
                temp.add(current);
            } else {
                for (int j=0; j<temp.size(); j++) {
                    if (current.getScore() > temp.get(j).getScore()) {
                        temp.add(j,current);
                        break;
                    }
                }
                if (temp.size() > howMany) {
                    temp.remove(temp.size()-1);
                }
            }
        }
        String[] returnArr = new String[howMany];
        for (int i=0; i<temp.size(); i++) {
            returnArr[i] = temp.get(i).getString();
        }
        return returnArr;
    }

}

class StringScore {
    String string;
    int score;
    public StringScore(String str, int scr) {
        string = str;
        score = scr;
    }

    public int getScore() {
        return score;
    }

    public String getString() {
        return string;
    }
}

class SaySomething {
	public SaySomething(String s) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(s));
		String str = br.readLine();
		while(str != null)
		{
			System.out.println(str);
			str = br.readLine();
		}
	}
}
