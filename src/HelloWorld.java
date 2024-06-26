import java.net.*;
import java.io.*;
import org.json.*;
import java.util.*;

class URLConnectionReader {
    //Reads HTTP data from the specified url
    public static BufferedReader getHTTP(String url) throws Exception {
        URL connection = new URL(url);
        URLConnection yc = connection.openConnection();
        return new BufferedReader(
                new InputStreamReader(
                        yc.getInputStream()));
    }
    //turns the read HTTP data into a JSON object
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

    //All-in-one function with error handling to make fetching HTTP data convenient
    public static JSONObject apiFetch(String url) {
        try {
            return getJSONFromBufferedReader(getHTTP(url));
        } catch (Exception e) {
            return null;
        }
    }
}

interface Command {
    void execute(String[] args, StringSimilarity sd, Scanner sc);
}

class Option {
    public String longForm, shortForm;
    public Command command;

    public Option(String lf, String sf, Command cmd)
        { longForm = lf; shortForm = sf; command = cmd; }
}

public class HelloWorld {

    static int numSimilarWords = 3;
    static boolean exitProgram = false;
    static String[] inputWords = { "hello" };

    static Command spellCheckFile = (file, sd, sc) -> new CheckFile(file[1], sd);

    static Command getRandom = (args, sd, sc) -> {
        String tmpDef = null, ranWord;
        while(tmpDef == null)
        {
            ranWord = sd.everyWord.get((int) (Math.random() * sd.everyWord.size()));
            tmpDef = defineWord(ranWord);
        }
        System.out.println(tmpDef);
    };

    static Command showHelp = (args, sd, sc) -> {
        System.out.println("--end or --e: end the program");
        System.out.println("--help or --h: list all commands");
        System.out.println("--random or --r: define a random word");
        System.out.println("--hangman: play a game of hangman");
        System.out.println("--check _file_ or --c _file_: check file for spelling errors");
    };

    static Command hangman = (args, sd, sc) -> {
        Hangman.playHangman(sc, sd);
    };

    static Command endProg = (args, sd, sc) -> {
        exitProgram = true;
    };

    static Option[] options = {
        new Option("--check", "--c", spellCheckFile),
        new Option("--random", "--r", getRandom),
        new Option("--help", "--h", showHelp),
        new Option("--hangman", "--hm", hangman),
        new Option("--end", "--e", endProg)
    };

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

        mainloop:
        while (!exitProgram) {

            System.out.println("Enter '--end' to stop. Use '--help' for a list of all commands");
            System.out.println("What word would you like to define?");
            System.out.print("> ");
            inputWords = sc.nextLine().split(" ");

            for (Option option : options)
                if (option.longForm.equals(inputWords[0]) || option.shortForm.equals(inputWords[0]))
                {
                    option.command.execute(inputWords, similarityDetector, sc);
                    continue mainloop;
                }

            /* Default behavior */

            // Spell check words that the user inputs.
            // Only if the user inputs multiple words.
            if (inputWords.length > 1) {
                for (String word : inputWords)
                    if (similarityDetector != null && !similarityDetector.checkWord(word)) {
                        String[] sugs = similarityDetector.findClosest(word, numSimilarWords);
                        System.out.print(word + ":");
                        for(String sug : sugs)
                            System.out.print(" " + sug);
                        System.out.println();
                    }
            } else {
                // If the user only inputs one word, then try to
                // get the definition. Otherwise, list similar words.
                String def = defineWord(inputWords[0]);
                if (def == null && similarityDetector != null) {
                    System.out.println("No definitions for '" + inputWords[0] + "' were found :(");
                    String[] similarWords = similarityDetector.findClosest(inputWords[0], numSimilarWords);
                    System.out.print("Possible Matches:");
                    for (int i = 0; i < similarWords.length; i++) {
                        System.out.print(" " + similarWords[i]);
                    }
                    System.out.println("\n-- Please note that not every word is in the dictionary --\n");
                } else if (def == null) {
                    System.out.println("No definitions for '" + inputWords[0] + "' were found :(");
                } else {
                    System.out.println(def);
                }
            }

        }

        System.out.println("Thank you for using our dictionary!");
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

    //This function is from user acdcjunior on Stack Overflow
    //https://stackoverflow.com/questions/955110/similarity-string-comparison-in-java
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

    //This function is from user acdcjunior on Stack Overflow
    //https://stackoverflow.com/questions/955110/similarity-string-comparison-in-java
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

    public static int similarity2(String s1, String s2)
    {
        s1 = s1.toLowerCase();
        s2 = s2.toLowerCase();

        int[] cost = new int[s2.length() + 1];

        // fill first row
        for(int i = 0; i < cost.length; i++)
            cost[i] = i;

        for(int i = 1; i <= s1.length(); i++)
        {
            int prevValue = i;

            for(int j = 1; j <= s2.length(); j++)
            {
                int top = cost[j] + 1;
                int left = prevValue + 1;
                int diag = cost[j - 1] + ((s1.charAt(i-1) == s2.charAt(j-1)) ? 0 : 1);

                int value = Math.min(Math.min(top, left), diag);

                // shuffle in
                cost[j-1] = prevValue;
                prevValue = value;
            }

            cost[s2.length()] = prevValue;
        }

        return cost[s2.length()];
    }

    //Compare one word with every word in the words.txt file and return the top howMany words
    public String[] findClosest(String base, int howMany) {
        ArrayList<StringScore> temp = new ArrayList<>();
        if (howMany == 0) {return null;}
        for (int i = 0; i < everyWord.size(); i++) {
            StringScore current = new StringScore(everyWord.get(i),(int)(similarity2(everyWord.get(i), base)));
            if (temp.isEmpty()) {
                temp.add(current);
            } else {
                for (int j=0; j<temp.size(); j++) {
                    if (current.getScore() < temp.get(j).getScore()) {
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

    public boolean checkWord(String w) {
        for (String s : everyWord) {
            if (w.equals(s))
                return true;
        }
        return false;
    }
}

//Combines a String with a Score so they can be put together in an ArrayList easily
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

class CheckFile {
    BufferedReader br = null;
    StringSimilarity sd = null;
    ProgressBar pb = null;
    public CheckFile(String fname, StringSimilarity simDetect) {
        sd = simDetect;
        try {
            br = new BufferedReader(new FileReader(fname));
            pb = new ProgressBar(ProgressBar.countLines(new File(fname)));
            check();
        }
        catch (IOException e) 
            { System.out.println("File " + fname + " could not be opened."); }
    }

    private void check()
    {
        String line = null;

        try {

            while((line = br.readLine()) != null)
            {
                String[] words = line.split(" ");
                for(String word : words) {
                    String[] brokenWords = CheckFile.breakWord(word);
                    for (String w: brokenWords) {
                        if (w.isEmpty() || w.matches(".*\\d.*") || w.length() == 1) {
                            continue;
                        }
                        //If the word is not in the word list, find similar words and print out the suggestions
                        if (!sd.checkWord(w)) {
                            String[] sugs = sd.findClosest(w, 3);
                            ProgressBar.clear();
                            System.out.print(w + ":");
                            for (String sug : sugs)
                                System.out.print(" " + sug);
                            System.out.println();
                        }
                    }

                }
                pb.step(1);
            }

        } catch (IOException e) 
            { System.out.println("Failed to read file."); }
    }
    private static String[] breakWord(String word) {
        //First, remove special characters
        word = word.replaceAll("[\\\\!?&_,.:;{}()\\-\\[\\]*/+<>=\"\']", " ");
        //Next, detect camel case
        for (int i=word.length() - 1; i > 0; i--) {
            if (Character.isLowerCase(word.charAt(i - 1)) && Character.isUpperCase(word.charAt(i))) {
                word = word.substring(0,i) + " " + word.substring(i);
            }
        }
        return word.toLowerCase().split(" ");
    }
}

class Hangman {
    public static void playHangman(Scanner sc, StringSimilarity similarityDetector) {
        System.out.println("Welcome to Hangman!");

        //Choose a random word that has a definition
        char[] usedLetters = new char[26];
        String game = Hangman.getRandomAlphabetic(similarityDetector);
        int numGuesses = 8;

        String currentGuess = "";
        for (int i=0; i<game.length(); i++) {
            currentGuess += "_";
        }
        //Hangman menu. Loop until you run out of guesses or solve the word
        while (numGuesses > 0 && !currentGuess.equals(game)) {
            System.out.println("You have " + numGuesses + " guess" + (numGuesses == 1 ? "" : "es") + " remaining.\nUsed letters:");
            for (char c : usedLetters) {if (c != 0) {System.out.print(c);}}
            System.out.println();
            System.out.print(currentGuess + "\n> ");
            String letter = sc.nextLine();
            if (letter.isEmpty()) {continue;}
            char guess = letter.toLowerCase().charAt(0);
            if (Character.isLetter(guess) && usedLetters[guess - 'a'] == 0) {
                usedLetters[guess - 'a'] = guess;
                boolean correctGuess = false;
                for (int i=0; i<game.length(); i++) {
                    if (game.toLowerCase().charAt(i) == guess) {
                        currentGuess = currentGuess.substring(0,i) + guess + currentGuess.substring(i+1);
                        correctGuess = true;
                    }
                }
                if (!correctGuess) {
                    numGuesses--;
                }
            }
        }
        if (currentGuess.equals(game)) {
            System.out.println("Great job! You got the word with " + (numGuesses) + " tr" + (numGuesses == 1 ? "y" : "ies") + " remaining");
        } else {
            System.out.println("Good try! The Word was " + game);
        }
        System.out.println(HelloWorld.defineWord(game));
    }
    private static String getRandomAlphabetic(StringSimilarity ss) {
        String str;

        boolean pass;
        do {
            str = ss.everyWord.get((int) (Math.random()*ss.everyWord.size()));
            char[] arr = str.toCharArray();
            pass = true;
            for (char c : arr) {
                if (!Character.isLetter(c)) {
                    pass = false;
                }
            }
            String def = HelloWorld.defineWord(str);
            if (def == null) {
                //Make sure there's a definition to the word
                pass = false;
            }
        } while (!pass);
        return str;
    }
}

class ProgressBar {
    int max;
    int current;
    public ProgressBar(int max) {
        this.max = max;
        System.out.print("[           ] 0%\r");
    }
    public void step(int amount) {
        current += amount;
        double percentage = (double) current / max * 100;
        String toPrint = "[";
        for (int i=0; i < 10; i++) {
            if (percentage >= (i+1)*10) {
                toPrint += "=";
            } else {
                toPrint += " ";
            }
        }
        System.out.print(toPrint + "] ");
        System.out.printf("%.2f", percentage);
        System.out.print("%\r");
    }
    public static void clear() { System.out.print("\r                     \r"); }
    public void done() {
        System.out.println("Done!                 ");
    }

    //Code from fhucho on stack overflow https://stackoverflow.com/questions/1277880/how-can-i-get-the-count-of-line-in-a-file-in-an-efficient-way
    public static int countLines(File file) throws IOException {
        int lines = 0;

        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[8 * 1024];
        int read;

        while ((read = fis.read(buffer)) != -1) {
            for (int i = 0; i < read; i++) {
                if (buffer[i] == '\n') lines++;
            }
        }

        fis.close();

        return lines;
    }
}
