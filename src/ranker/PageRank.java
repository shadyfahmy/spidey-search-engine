package ranker;

import database_connection.DatabaseManager;
import org.ejml.simple.SimpleMatrix;
import java.sql.*;
import java.util.HashSet;
import java.util.HashMap;

// TODO: Try to optimize writing to database
// Assuming page ids are continuous, starting at 1
public class PageRank {
    private final DatabaseManager dbManager;
    private Connection connection;

    private int pagesCount;
    final static double dampingFactor = 0.85;
    final static double iterativeTolerance = 0.001;

    public static void main(String[] args) {
        DatabaseManager dbManager = new DatabaseManager();
        PageRank pageRankCalculator = new PageRank(dbManager);
        pageRankCalculator.compareCalculationMethods();
    }

    public PageRank(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void compareCalculationMethods() {
        long start = System.nanoTime();
        SimpleMatrix iterativePageRanks = calcPageRanks(false);
        long checkpoint = System.nanoTime();
        SimpleMatrix algebraicPageRanks = calcPageRanks(true);
        long end = System.nanoTime();
        System.out.println("Iterative execution time: " + ((checkpoint - start) / 1000000) + " ms");
        iterativePageRanks.print();
        System.out.println("Algebraic execution time: " + ((end - checkpoint) / 1000000) + " ms");
        algebraicPageRanks.print();
    }

    public SimpleMatrix calcPageRanks(boolean isAlgebraic) {
        try {
            connection = dbManager.getDBConnection();
            pagesCount = getPagesCount();
            HashMap<Integer, HashSet<Integer>> pageConnections = getPageConnections();
            SimpleMatrix adjacencyMatrix = buildAdjacencyMatrix(pageConnections);
            SimpleMatrix pageRanks;
            if (isAlgebraic)
                pageRanks =  calcPageRanksAlgebraic(adjacencyMatrix);
            else
                pageRanks = calcPageRanksIterative(adjacencyMatrix);
            savePageRanks(pageRanks);
            connection.close();
            return pageRanks;
        } catch (Exception ex) {
            System.out.println("Failed to calculate PageRank: Exception occurred.");
            ex.printStackTrace();
            return null;
        }
    }

    private SimpleMatrix calcPageRanksAlgebraic(SimpleMatrix adjacencyMatrix) {
        SimpleMatrix dampingScale = new SimpleMatrix(pagesCount, 1);
        dampingScale.fill(1.0 - dampingFactor / pagesCount);
        return (
                SimpleMatrix.identity(pagesCount).minus(adjacencyMatrix.scale(dampingFactor))
        ).invert().mult(dampingScale);
    }

    private SimpleMatrix calcPageRanksIterative(SimpleMatrix adjacencyMatrix) {
        SimpleMatrix dampingScale = new SimpleMatrix(pagesCount, 1);
        dampingScale.fill(1.0 - dampingFactor / pagesCount);
        SimpleMatrix pageRanks = new SimpleMatrix(pagesCount, 1);
        pageRanks.fill(1.0 / pagesCount);
        adjacencyMatrix = adjacencyMatrix.scale(dampingFactor);
        boolean converged = false;
        SimpleMatrix newPageRanks;
        while(!converged) {
            newPageRanks = adjacencyMatrix.mult(pageRanks).plus(dampingScale);
            converged = newPageRanks.minus(pageRanks).elementMaxAbs() < iterativeTolerance;
            pageRanks = newPageRanks;
        }
        return pageRanks;
    }

    private void savePageRanks(SimpleMatrix pageRanks) throws SQLException {
        StringBuilder query = new StringBuilder("UPDATE page SET page_rank = CASE id");
        for(int i = 0; i < pagesCount; i++) {
            query.append(" WHEN ? THEN ?");
        }
        query.append(" END");
        PreparedStatement statement = connection.prepareStatement(query.toString());
        for(int i = 0; i < pagesCount; i++) {
            statement.setInt(2*i+1, i+1);
            statement.setDouble(2*i+2, pageRanks.get(i, 0));
        }
        statement.execute();
//        **********************************************************************
//        Statement statement = connection.createStatement();
//        statement.execute("DROP TEMPORARY TABLE IF EXISTS page_ranks;");
//        statement.execute("CREATE TEMPORARY TABLE page_ranks(id serial, page_rank double)");
//
//        StringBuilder query = new StringBuilder("INSERT INTO page_ranks(page_rank) values");
//        for(int i = 0; i < pagesCount; i++) {
//            query.append(" (?)");
//            if (i < pagesCount - 1)
//                query.append(",");
//        }
//        PreparedStatement preparedStatement = connection.prepareStatement(query.toString());
//        for(int i = 0; i < pagesCount; i++) {
//            preparedStatement.setDouble(i+1, pageRanks.get(i, 0));
//        }
//        preparedStatement.execute();
//        statement.execute("UPDATE page SET page_rank = (SELECT page_rank FROM page_ranks WHERE page_ranks.id = page.id)");
//        **********************************************************************
//        PreparedStatement statement = connection.prepareStatement(
//                "UPDATE page SET page_rank = ? WHERE id = ?"
//        );
//        for(int i = 0; i < pagesCount; i++) {
//            statement.setDouble(1, pageRanks.get(i, 0));
//            statement.setInt(2, i+1);
//            statement.addBatch();
//        }
//        statement.executeBatch();
        statement.close();
    }

    private HashMap<Integer, HashSet<Integer>> getPageConnections() throws SQLException {
        String query = "SELECT * FROM page_connections";
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(query);
        HashMap<Integer, HashSet<Integer>> outboundConnections = new HashMap<>(pagesCount);
        for (int pageID = 1; pageID <= pagesCount; pageID++) {
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
            outboundConnections.forEach(outboundPageID ->
                    adjacencyMatrix.set(outboundPageID - 1, pageID - 1, 1.0/outboundConnectionsCount)
            );
        });
        return adjacencyMatrix;
    }

    private int getPagesCount() throws SQLException {
        String query = "SELECT COUNT(*) FROM page";
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(query);
        result.next();
        int pagesCount = result.getInt(1);
        result.close();
        statement.close();
        return pagesCount;
    }
}
