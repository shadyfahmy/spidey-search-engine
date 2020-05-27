package ranker;

import database_manager.DatabaseManager;
import org.ejml.simple.SimpleMatrix;

import java.sql.*;
import java.util.HashMap;
import java.util.HashSet;

// Assuming page ids are continuous
public class PageRanker {
    private final DatabaseManager dbManager;
    private Connection connection;
    private int startingID;

    static int pagesCount;

    final static double dampingFactor = 0.85;
    final static double iterativeTolerance = 0.001;
    final static boolean isAlgebraic = false;

    private static class InstanceHolder {
        private static final PageRanker instance = new PageRanker();
    }

    private PageRanker() {
        dbManager = new DatabaseManager();
    }

    public static PageRanker getInstance() {
        return InstanceHolder.instance;
    }

    public static void main(String[] args) {
        PageRanker pageRanker = new PageRanker();
        pageRanker.timedUpdatePageRanks();
    }

    public void timedUpdatePageRanks() {
        long start = System.nanoTime();
        updatePageRanks();
        long end = System.nanoTime();
        System.out.println(
                "PageRank calculation done for " + PageRanker.pagesCount +
                        " pages in " + ((end - start) / 1000000) + " ms, " +
                        "using " + (PageRanker.isAlgebraic ? "algebraic" : "iterative") + " method."
        );
    }

    public void updatePageRanks() {
        try {
            connection = dbManager.getDBConnection();
            updatePagesMetadata();
            HashMap<Integer, HashSet<Integer>> pageConnections = getPageConnections();
            SimpleMatrix adjacencyMatrix = buildAdjacencyMatrix(pageConnections);
            SimpleMatrix pageRanks;
            if (isAlgebraic)
                pageRanks =  calcPageRanksAlgebraic(adjacencyMatrix);
            else
                pageRanks = calcPageRanksIterative(adjacencyMatrix);
            savePageRanks(pageRanks);
            connection.close();
        } catch (Exception ex) {
            System.out.println("Failed to calculate PageRank: Exception occurred.");
            ex.printStackTrace();
        }
    }

    private SimpleMatrix calcPageRanksAlgebraic(SimpleMatrix adjacencyMatrix) {
        SimpleMatrix dampingMatrix = new SimpleMatrix(pagesCount, 1);
        dampingMatrix.fill(1.0 - dampingFactor / pagesCount);
        return (
                SimpleMatrix.identity(pagesCount).minus(adjacencyMatrix.scale(dampingFactor))
        ).invert().mult(dampingMatrix);
    }

    private SimpleMatrix calcPageRanksIterative(SimpleMatrix adjacencyMatrix) {
        SimpleMatrix dampingMatrix = new SimpleMatrix(pagesCount, 1);
        dampingMatrix.fill(1.0 - dampingFactor / pagesCount);
        SimpleMatrix pageRanks = new SimpleMatrix(pagesCount, 1);
        pageRanks.fill(1.0 / pagesCount);
        adjacencyMatrix = adjacencyMatrix.scale(dampingFactor);
        boolean converged = false;
        SimpleMatrix newPageRanks;
        while(!converged) {
            newPageRanks = adjacencyMatrix.mult(pageRanks).plus(dampingMatrix);
            converged = newPageRanks.minus(pageRanks).elementMaxAbs() < iterativeTolerance;
            pageRanks = newPageRanks;
        }
        return pageRanks;
    }

    private void savePageRanks(SimpleMatrix pageRanks) throws SQLException {
        String query = "UPDATE page SET page_rank = CASE id"
                + " WHEN ? THEN ?".repeat(pagesCount)
                + " END";
        PreparedStatement statement = connection.prepareStatement(query);
        for(int i = 0; i < pagesCount; i++) {
            statement.setInt(2*i+1, i + startingID);
            statement.setDouble(2*i+2, pageRanks.get(i, 0));
        }
        statement.execute();
        statement.close();
    }

    private HashMap<Integer, HashSet<Integer>> getPageConnections() throws SQLException {
        String query = "SELECT * FROM page_connections";
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(query);
        HashMap<Integer, HashSet<Integer>> outboundConnections = new HashMap<>(pagesCount);
        for (int pageID = startingID; pageID < pagesCount + startingID; pageID++) {
            outboundConnections.put(pageID, new HashSet<>());
        }
        while (result.next()) {
            int pageID = result.getInt(1), outboundPageID = result.getInt(2);
            outboundConnections.get(pageID).add(outboundPageID);
        }
        result.close();
        statement.close();
        return outboundConnections;
    }

    private SimpleMatrix buildAdjacencyMatrix(HashMap<Integer, HashSet<Integer>> pageConnections) {
        SimpleMatrix adjacencyMatrix = new SimpleMatrix(pagesCount, pagesCount);
        pageConnections.forEach((pageID, outboundConnections) -> {
            int outboundConnectionsCount = outboundConnections.size();
            outboundConnections.forEach(outboundPageID -> {
                if(!pageID.equals(outboundPageID))
                    adjacencyMatrix.set(outboundPageID - startingID,
                                        pageID - startingID,
                                       1.0/outboundConnectionsCount);
            }
            );
        });
        return adjacencyMatrix;
    }

    private void updatePagesMetadata() throws SQLException {
        String query = "SELECT COUNT(*), MIN(id) FROM page";
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(query);
        result.next();
        this.pagesCount = result.getInt(1);
        this.startingID = result.getInt(2);
        result.close();
        statement.close();
    }
}
