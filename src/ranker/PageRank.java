package ranker;

import com.mysql.jdbc.exceptions.NotYetImplementedException;
import database_connection.DatabaseConnectionFactory;
import org.ejml.simple.SimpleMatrix;
import java.sql.*;
import java.util.HashSet;
import java.util.HashMap;

public class PageRank {
    private DatabaseConnectionFactory dbConnectionFactory;

    // TODO: Replace with dynamic count from database
    final static int pagesCount = 4;
    final static double dampingFactor = 0.85;
    final static double iterativeTolerance = 0.001;

    public static void main(String[] args) {
        DatabaseConnectionFactory dbConnectionFactory = new DatabaseConnectionFactory();
        PageRank pageRankCalculator = new PageRank(dbConnectionFactory);
        pageRankCalculator.compareCalculationMethods();
    }

    public PageRank(DatabaseConnectionFactory dbConnectionFactory) {
        this.dbConnectionFactory = dbConnectionFactory;
    }

    public void compareCalculationMethods() {
        try {
            long start = System.nanoTime();
            calcPageRanks(false);
            long checkpoint = System.nanoTime();
            calcPageRanks(true);
            long end = System.nanoTime();
            System.out.println("Algebraic execution time: " + ((checkpoint - start) / 1000000) + " ms");
            System.out.println("Iterative execution time: " + ((end - checkpoint) / 1000000) + " ms");
        } catch (SQLException e) {
            System.out.println("SQL Exception");
            e.getSQLState();
        }
    }

    public SimpleMatrix calcPageRanks(boolean isAlgebraic) throws SQLException {
        HashMap<Integer, HashSet<Integer>> pageConnections = getPageConnections();
        SimpleMatrix adjacencyMatrix = buildAdjacencyMatrix(pageConnections);
        if(isAlgebraic)
            return calcPageRanksAlgebraic(adjacencyMatrix);
        else
            return calcPageRanksIterative(adjacencyMatrix);
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
        throw new NotYetImplementedException();
    }

    private HashMap<Integer, HashSet<Integer>> getPageConnections() throws SQLException {
        String query = "SELECT * FROM page_connections";
        Statement statement = dbConnectionFactory.getDBConnection().createStatement();
        ResultSet result = statement.executeQuery(query);
        HashMap<Integer, HashSet<Integer>> outboundConnections = new HashMap<>(pagesCount);
        for(int pageID = 1; pageID <= pagesCount; pageID++) {
            outboundConnections.put(pageID, new HashSet<>());
        }
        while(result.next()) {
            int pageID = result.getInt(1), outboundPageID = result.getInt(2);
            outboundConnections.get(pageID).add(outboundPageID);
        }
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
}
