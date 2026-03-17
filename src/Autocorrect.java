import java.io.*;
import java.util.*;

/**
 * Autocorrect
 * <p>
 * A command-line tool to suggest similar words when given one not in the dictionary.
 * </p>
 * @author Zach Blick
 * @author Lily Kassaei
 */

public class Autocorrect {
    public static String[] words;
    public static int threshold;

    public static void main(String[] args) {
        run();
    }

    // Sets up scanner to get user input on the file name of the dictionary, threshold and given word to check
    public static void run() {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter dictionary name: ");
        String dictionaryName = scanner.nextLine();

        System.out.print("Enter threshold: ");
        int threshold = Integer.parseInt(scanner.nextLine());

        Autocorrect ac = new Autocorrect(dictionaryName, threshold);

        System.out.print("Enter word to check: ");
        String word = scanner.nextLine();

        // Get the results
        String[] suggestions = ac.runTest(word);

        // Final output
        if (suggestions.length == 0) {
            System.out.println("No suggestions found.");
        }
        else {
            System.out.println("Suggestions: " + Arrays.toString(suggestions));
        }

        scanner.close();
    }

    public Autocorrect(String dictionaryName, int threshold) {
        this.words = loadDictionary(dictionaryName); // Use when you want terminal
        //this.words = dictionary; // Use when you want testers 
        this.threshold = threshold;
    }

    /**
     * Runs a test from the tester file, AutocorrectTester.
     * @param typed The (potentially) misspelled word, provided by the user.
     * @return An array of all dictionary words with an edit distance less than or equal
     * to threshold, sorted by edit distnace, then sorted alphabetically.
     */

    public String[] runTest(String typed) {
        // Put the pairs into 2D array
        String filename = "pairs.csv";
        int[][] pairs = loadPairs(filename);

        // Collect candidate words that share at least one bigram with the typed word
        // This is necessary to avoid duplicates if a word shares multiple bigrams with the typed word
        boolean[] isCandidate = new boolean[words.length];

        // Get all of the bigrams
        findBigramCandidates(typed, pairs, isCandidate);

        // After the bigram candidate loop, also add any word where the length difference alone doesn't rule it out
        findOtherCandidates(typed, isCandidate);

        // Actually get the indices of the candidates in the dictionary
        ArrayList<Integer> candidateIndices = new ArrayList<>();
        for (int i = 0; i < words.length; i++) {
            // Again making sure no duplicates
            if (isCandidate[i]) {
                candidateIndices.add(i);
            }
        }

        // Array for the edit distance
        int[] ed = new int[words.length];

        // default everyone to out
        Arrays.fill(ed, threshold + 1);

        // Levenshtein on all of the candidates with the results stored in ed
        performLevenshtein(typed, candidateIndices, ed);

        // Sort the results
        ArrayList<String> ans = sort(candidateIndices, ed);

        // Transfer to string array and return
        String[] fin = new String[ans.size()];
        for (int i = 0; i < ans.size(); i++) {
            fin[i] = ans.get(i);
        }

        return fin;
    }

    // Find all the words that are candidates based on bigrams in the typed word
    public void findBigramCandidates(String typed, int[][] pairs, boolean[] isCandidate) {
        for (int i = 0; i < typed.length() - 1; i++) {
            // Char one of the pair/bigram
            char c1 = typed.charAt(i);
            // Char two of the pair/bigram
            char c2 = typed.charAt(i + 1);
            if (c1 >= 'a' && c1 <= 'z' && c2 >= 'a' && c2 <= 'z') {
                // Bring the bigram back to an index 0-675 so we can map to the right row in pairs
                // Ex. AA = 0
                int index = (c1 - 'a') * 26 + (c2 - 'a');
                // Go through the row in pairs at index and mark the index of the work in the dict as a candidate
                for (int idx : pairs[index]) {
                    isCandidate[idx] = true;
                }
            }
        }
    }

    // Bigram doesn't work for smaller words so also get words that could work based on length
    public void findOtherCandidates(String typed, boolean[] isCandidate) {
        for (int i = 0; i < words.length; i++) {
            if (Math.abs(words[i].length() - typed.length()) <= threshold) {
                isCandidate[i] = true;
            }
        }
    }

    // Levenshtein on all of the candidates with the results stored in ed
    public void performLevenshtein(String typed, ArrayList<Integer> candidateIndices, int[] ed) {
        // Go through all the candidates
        for (int i : candidateIndices) {
            // Array for tabulation
            int[][] tab = new int[typed.length() + 1][words[i].length() + 1];
            // Boolean so that if a word passes threshold we can immediately move on to the next
            boolean isInvalid = false;

            // Compute Levenshtein distance
            for (int j = 0; j < tab.length; j++) {
                for (int k = 0; k < tab[0].length; k++) {
                    // If one word is shorter than the other than distance is just length of other word
                    if (j == 0 && k != 0) {
                        tab[j][k] = k;
                    }
                    else if (k == 0 && j != 0) {
                        tab[j][k] = j;
                    }
                    // The spot at (0,0)
                    else if (j == 0) {
                        tab[j][k] = 0;
                    }
                    // If the chars match, just take the old value of the distance
                    else if (typed.charAt(j - 1) == words[i].charAt(k - 1)) {
                        tab[j][k] = tab[j - 1][k - 1];
                    }
                    // Take the minimum of substitution, deletion and insertion
                    else {
                        int first = Math.min(tab[j - 1][k - 1], tab[j - 1][k]);
                        tab[j][k] = 1 + Math.min(first, tab[j][k - 1]);
                    }
                }

                // After filling each row, find the minimum value in it to see if we can break out
                int rowMin = Integer.MAX_VALUE;
                for (int k = 0; k < tab[0].length; k++) {
                    rowMin = Math.min(rowMin, tab[j][k]);
                }

                // If even the best cell in this row exceeds threshold the word will definitely exceed
                if (rowMin > threshold) {
                    isInvalid = true;
                    break;
                }
            }

            // If we broke out, then the last square is null so we just set to threshold + 1 to eliminate
            if (isInvalid) {
                ed[i] = threshold + 1;
            }
            // Else just set the last square to be the edit distance
            else {
                ed[i] = tab[tab.length - 1][tab[0].length - 1];
            }
        }
    }

    // Sort the results of Levenshtein based alphabetical value and edit distance
    public ArrayList<String> sort(ArrayList<Integer> candidateIndices, int[] ed) {
        // Get original indices
        Integer[] indices = new Integer[words.length];
        for (int i = 0; i < words.length; i++) {
            indices[i] = i;
        }

        // Sorts by edit distance
        // Arrays.sort with a comparator:
        // Compares two indices a and b:
        // If their edit distances differ, the one with the smaller distance goes first
        // If their edit distances are equal, sort alphabetically by word
        // Sort just the candidates by edit distance, then alphabetically
        candidateIndices.sort((a, b) -> {
            if (ed[a] != ed[b]) return ed[a] - ed[b];
            return words[a].compareTo(words[b]);
        });

        // Only keep those at or below threshold
        ArrayList<String> ans = new ArrayList<>();
        for (int i : candidateIndices) {
            if (ed[i] <= threshold) {
                ans.add(words[i]);
            }
        }
        return ans;
    }

    // Load the information from the existing file pairs.csv
    // Code here based off of Mr. Blick's loadDictionary()
    public static int[][] loadPairs(String filename) {
        // Will have exactly 676 pairs (26 * 26 is all possible alphabet pairs)
        int[][] pairs = new int[676][];

        try {
            String line;
            BufferedReader reader = new BufferedReader(new FileReader(filename));

            while ((line = reader.readLine()) != null) {
                // Each line is like AB, 1, 2, etc. (representing the index of the words in dict. with AB)
                String[] parts = line.split(",");

                // Assign the label of the row (ex. AB, AC, etc.) to an index between 0-675
                char c1 = parts[0].charAt(0);
                char c2 = parts[0].charAt(1);
                int index = (c1 - 'A') * 26 + (c2 - 'A');

                // Get all of the indices in each row which excludes the label part (ex. AB, AC, etc.)
                int[] indices = new int[parts.length - 1];
                for (int i = 1; i < parts.length; i++) {
                    indices[i - 1] = Integer.parseInt(parts[i]); // Need to parse since everything in the file is a String
                }
                pairs[index] = indices;
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Fill any missing rows with empty arrays so we don't get null errors
        for (int i = 0; i < 676; i++) {
            if (pairs[i] == null) {
                pairs[i] = new int[0];
            }
        }
        
        return pairs;
    }

    private static String[] loadDictionary(String dictionary)  {
        try {
            String line;
            BufferedReader dictReader = new BufferedReader(new FileReader("dictionaries/" + dictionary + ".txt"));
            line = dictReader.readLine();

            // Update instance variables with test data
            int n = Integer.parseInt(line);
            String[] words = new String[n];

            for (int i = 0; i < n; i++) {
                line = dictReader.readLine();
                words[i] = line;
            }
            return words;
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}