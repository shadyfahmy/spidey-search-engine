package ranker;

import database_manager.DatabaseManager;

import org.tartarus.snowball.ext.EnglishStemmer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class QueryResultsFetcher {
    private final DatabaseManager dbManager;
    final String text_docs_dir = "./txt_docs/";
    final int maxSnippets = 5;

    public static class Page {
        int id;
        String url;
        String title;
        String description;

        public Page(int id, String url, String title, String description) {
            this.id = id;
            this.url = url;
            this.title = title;
            this.description = description;
        }

        void print() {
            System.out.println(id);
            System.out.println(url);
            System.out.println(title);
            System.out.println(description);
        }
    }

    public static void main(String[] args)  {
        QueryResultsFetcher queryResultsFetcher = new QueryResultsFetcher();
        PageRanker pageRanker = PageRanker.getInstance();
        pageRanker.timedUpdatePageRanks();


        ArrayList<String> words = new ArrayList<>();

//        words.add("blog");
//        words.add("home");
//
//        List<String> urls = queryResultsFetcher.getImageSearchResults(words, 2, 1);
//
//        urls.forEach(System.out::println);

        EnglishStemmer stemmer = new EnglishStemmer();

        stemmer.setCurrent("about");
        stemmer.stem();
        words.add(stemmer.getCurrent());

        stemmer.setCurrent("your");
        stemmer.stem();
        words.add(stemmer.getCurrent());

        ArrayList<List<String>> phrases = new ArrayList<>();

        List<String> phrase = new ArrayList<>();

        stemmer.setCurrent("more");
        stemmer.stem();
        phrase.add(stemmer.getCurrent());

        stemmer.setCurrent("featured");
        stemmer.stem();
        phrase.add(stemmer.getCurrent());

        stemmer.setCurrent("articles");
        stemmer.stem();
        phrase.add(stemmer.getCurrent());

        phrases.add(phrase);


        List<Page> queryResults = queryResultsFetcher.getQueryResults(3, words, phrases, 0, 5);
        queryResults.forEach(Page::print);
    }

    public QueryResultsFetcher() {
        dbManager = new DatabaseManager();
    }

    public List<Page> getQueryResults(int userID, List<String> words, List<List<String>> phrases, int offset, int limit) {
        try {
            long start = System.currentTimeMillis();

            Connection connection = dbManager.getDBConnection();
            PreparedStatement statement = getQueryStatement(connection, userID, words, phrases, offset, limit);
            ResultSet result = statement.executeQuery();

            List<Page> queryResults = new ArrayList<>();
            while (result.next()) {
                int pageID = result.getInt(1);
                String  url = result.getString(2),
                        title = result.getString(3),
                        description = result.getString(4),
                        indices = result.getString(5);
                if(description == null) {
                    description = getPageSnippets(pageID, getNumericIndices(indices, maxSnippets));
                }
                queryResults.add(new Page(pageID, url, title, description));
            }

            statement.close();
            connection.close();

            long end = System.currentTimeMillis();
            System.out.println("Query results fetched in " + (end - start) + " ms");

            return queryResults;
        }
        catch (Exception ex) {
            System.out.println("Failed to fetch query results: Exception occurred.");
            ex.printStackTrace();
            return null;
        }
    }

    private List<Integer> getNumericIndices(String indicesString, int limit) {
        return Arrays.stream(indicesString.split(",")).limit(limit).map(Integer::parseInt).collect(Collectors.toList());
    }

    private String getPageSnippets(int pageID, List<Integer> indices) {
        try {
            String content = Files.readString(Path.of(text_docs_dir + pageID + ".txt"));
            List<String> words = Arrays.asList(content.split("\\s"));
            StringBuilder snippets = new StringBuilder();
            for (Integer index : indices) {
                snippets.append(
                        String.join(" ", words.subList(
                                Math.max(0, index - 4), Math.min(words.size(), index + 4)
                        ))
                );
                snippets.append("... ");
            }
            return snippets.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    private PreparedStatement getQueryStatement(Connection connection, int userID, List<String> words,
                                                List<List<String>> phrases, int offset, int limit) throws SQLException {
        // Build query string
        StringBuilder query = new StringBuilder(
                "select p.id, p.url, p.title, p.description, indices, total_relevance * page_rank as score, is_phrase, user from\n" +
                "    ( \n" +
                "    select page_id, sum(relevance) as total_relevance, group_concat(indices) as indices, sum(is_phrase) as is_phrase, user from\n" +
                "    ( \n"
        );

        boolean queryAdded = false;

        if (!words.isEmpty()) {
            query.append(generateWordsRelevanceQuery(words));
            queryAdded = true;
        }

        for (List<String> phrase : phrases) {
            if (queryAdded)
                query.append("union \n");
            query.append(generatePhraseRelevanceQuery(phrase));
            queryAdded = true;
        }

        query.append(
                ") t\n" +
                "    left join history h on t.page_id = h.page and user = ? \n" +
                "    group by page_id, user\n" +
                "    order by user desc, is_phrase desc, total_relevance desc\n" +
                "    limit ? , ?" +
                "    ) r\n" +
                "join page p on p.id = r.page_id\n" +
                "order by user desc, is_phrase desc, score desc"
        );

        PreparedStatement statement = connection.prepareStatement(query.toString());

        // Set query parameters
        int paramIdx = 1;

        if(!words.isEmpty())
            paramIdx = setWordsRelevanceQueryParameters(words, statement, paramIdx);

        for (List<String> phrase : phrases)
            paramIdx = setPhraseRelevanceQueryParameters(phrase, statement, paramIdx);

        statement.setInt(paramIdx++, userID);
        statement.setInt(paramIdx++, offset);
        statement.setInt(paramIdx, limit);

        return statement;
    }

    private String generateWordsRelevanceQuery(List<String> words)  {
        int wordsCount = words.size();

        String query;
        query =
                "select wi.page_id,SUM((1 + log(count)) * idf + important) as relevance, group_concat(position) as indices, 0 as is_phrase\n" +
                "from page p\n" +
                "join word_index wi on wi.page_id = p.id\n" +
                "join word w on wi.word_id = w.id\n" +
                "join word_positions wp on wp.word_id = wi.word_id and wp.page_id = wi.page_id \n" +
                "join (\n" +
                "    select word_id, log(1 + (? / y)) as idf from \n" +
                "    (select id as word_id, pages_count as y from word where word = ?" +
                        " or word = ? ".repeat(wordsCount - 1) + ") as t2\n" +
                ") t on t.word_id = w.id\n" +
                "group by page_id\n";

        return query;
    }

    private int setWordsRelevanceQueryParameters(List<String> words,
                                                 PreparedStatement statement,
                                                 int paramIdx) throws SQLException {
        statement.setInt(paramIdx++, PageRanker.pagesCount);
        for (String word : words)
            statement.setString(paramIdx++, word);

        return paramIdx;
    }

    private String generatePhraseRelevanceQuery(List<String> phraseWords)  {
        int phraseWordsCount = phraseWords.size();

        String coreTableQuery =
                "select wi.page_id, word, wp.position from word_index wi \n" +
                " join word w on w.id = wi.word_id\n" +
                " join word_positions wp \n" +
                " on wi.word_id = wp.word_id and wi.page_id = wp.page_id \n";

        String coreTableJoin = "join ( " + coreTableQuery + ") t? on t1.page_id = t?.page_id \n";

        String termFrequencyTableQuery =
                "select t1.page_id, 1 + log(count(*)) as tf, group_concat(t1.position) as indices\n" +
                "from ( " + coreTableQuery + " ) t1\n" + coreTableJoin.repeat(phraseWordsCount - 1) +
                "where (t1.word = ? " + " and t?.word = ? ".repeat(phraseWordsCount - 1) +
                "and t?.position - t?.position = 1 ".repeat(phraseWordsCount - 1) + ")\n" +
                "group by t1.page_id \n";

        String query;
        query =
             "select page_id, log(1 + (?/phrase_count)) * tf as relevance, indices, 1 as is_phrase from\n" +
             "(select count(*) as phrase_count from (\n" + termFrequencyTableQuery + ") t \n" +
             ") t1,\n" +
             "(\n" + termFrequencyTableQuery +
             ") t2,\n" +
             "(select count(*) as pages_count from page) t3 \n";

        return query;
    }

    private int setPhraseRelevanceQueryParameters(List<String> phraseWords,
                                                      PreparedStatement statement,
                                                      int paramIdx) throws SQLException {
        int phraseWordsCount = phraseWords.size();

        statement.setInt(paramIdx++, PageRanker.pagesCount);
        // The same parameters are repeated twice
        for(int count = 1; count <= 2; count++) {
            // Set join mainTableJoin parameters
            for (int idx = 2; idx <= phraseWordsCount ; idx++) {
                statement.setInt(paramIdx++, idx);
                statement.setInt(paramIdx++, idx);
            }

            // Set WHERE phrase words parameters
            statement.setString(paramIdx++, phraseWords.get(0));
            for (int idx = 1; idx < phraseWordsCount; idx++) {
                statement.setInt(paramIdx++, idx + 1);
                statement.setString(paramIdx++, phraseWords.get(idx));
            }

            // Set WHERE phrase positions parameters
            for (int idx = 2; idx <= phraseWordsCount ; idx++) {
                statement.setInt(paramIdx++, idx);
                statement.setInt(paramIdx++, idx - 1);
            }
        }

        return paramIdx;
    }

    public List<String> getImageSearchResults(List<String> words, int offset, int limit) {
        try {
            long start = System.currentTimeMillis();
            int wordsCount = words.size();

            String query;
            query =
                    "select image_id, url, count(*) as score from word_index_image wi\n" +
                            "join word_image w on w.id = wi.word_id\n" +
                            "join image i on i.id = wi.image_id\n" +
                            "where word = ? " + " or word = ? ".repeat(wordsCount - 1) +
                            "group by image_id, url\n" +
                            "order by score desc \n" +
                            "limit ? , ?";

            Connection connection = dbManager.getDBConnection();
            PreparedStatement statement = connection.prepareStatement(query);

            int paramIdx = 1;
            for (String word : words)
                statement.setString(paramIdx++, word);

            statement.setInt(paramIdx++, offset);
            statement.setInt(paramIdx, limit);

            ResultSet result = statement.executeQuery();

            List<String> imageURLs = new ArrayList<>();
            while (result.next()) {
                String url = result.getString(2);
                imageURLs.add(url);
            }

            statement.close();
            connection.close();

            long end = System.currentTimeMillis();
            System.out.println("Image results fetched in " + (end - start) + " ms");

            return imageURLs;
        } catch (Exception ex) {
            System.out.println("Failed to fetch image query results: Exception occurred.");
            ex.printStackTrace();
            return null;
        }
    }
}
