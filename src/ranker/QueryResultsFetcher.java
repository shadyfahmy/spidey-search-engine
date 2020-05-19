package ranker;

import database_manager.DatabaseManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class QueryResultsFetcher {
    private final DatabaseManager dbManager;
    final String text_docs_dir = "./txt_docs/";
    final int maxSnippets = 5;

    public static class Page {
        String url;
        String title;
        String description;

        public Page(String url, String title, String description) {
            this.url = url;
            this.title = title;
            this.description = description;
        }

        void print() {
            System.out.println(url);
            System.out.println(title);
            System.out.println(description);
        }
    }

    public static void main(String[] args) {
        DatabaseManager dbManager = new DatabaseManager();
        QueryResultsFetcher queryResultsFetcher = new QueryResultsFetcher(dbManager);
        ArrayList<String> queryWords = new ArrayList<>();
        queryWords.add("about");
        queryWords.add("your");
        List<Page> queryResults = queryResultsFetcher.getQueryResults(queryWords);
        queryResults.forEach(Page::print);
    }

    public QueryResultsFetcher(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public List<Page> getQueryResults(List<String> queryWords) {
        try {
            int queryWordsCount = queryWords.size();

            String query = "select p.id, p.url, p.title, p.description, indices, relevance * page_rank as score from\n" +
                    "    (select page_id, group_concat(indices) as indices, SUM((1 + log(count)) * idf + important) as relevance\n" +
                    "    from page p\n" +
                    "    join word_index wi on wi.page_id = p.id\n" +
                    "    join word w on wi.word_id = w.id\n" +
                    "    join (\n" +
                    "        select word_id, log(1 + (x / y)) as idf from \n" +
                    "        (select count(id) as x from page) as t1, \n" +
                    "        (select id as word_id, pages_count as y from word where word = ? " +
                    "           or word = ? ".repeat(queryWordsCount - 1) + ") as t2\n" +
                    "    ) t on t.word_id = w.id\n" +
                    "    where word = ? " + " or word = ? ".repeat(queryWordsCount - 1) +
                    "    group by page_id\n" +
                    "    order by relevance desc\n" +
                    "    limit 100) r\n" +
                    "join page p on p.id = r.page_id\n" +
                    "order by score desc;";

            PreparedStatement statement = dbManager.getDBConnection().prepareStatement(query);

            for(int i = 0; i < queryWordsCount; i++) {
                statement.setString(i + 1, queryWords.get(i));
                statement.setString(queryWordsCount + i + 1, queryWords.get(i));
            }

            ResultSet result = statement.executeQuery();

            List<Page> queryResults = new ArrayList<>();
            while (result.next()) {
                int pageID = result.getInt(1);
                String  url = result.getString(2),
                        title = result.getString(3),
                        description = result.getString(4),
                        indices = result.getString(5);
                if(description == null) {
                    description = getPageSnippets(pageID, getNumericIndices(indices));
                }
                queryResults.add(new Page(url, title, description));
            }
            return queryResults;
        }
        catch (Exception ex) {
            System.out.println("Failed to fetch query results: Exception occurred.");
            ex.printStackTrace();
            return null;
        }
    }

    private List<Integer> getNumericIndices(String indicesString) {
        return Arrays.stream(indicesString.split(",")).map(Integer::parseInt).collect(Collectors.toList());
    }

    private String getPageSnippets(int pageID, List<Integer> indices) {
        try {
            String content = Files.readString(Path.of(text_docs_dir + pageID + ".txt"));
            List<String> words = Arrays.asList(content.split("\\s"));
            StringBuilder snippets = new StringBuilder();
            for (Integer index : indices.subList(0, Math.min(maxSnippets, indices.size()))) {
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
}

