package database_manager;
import java.io.File;
import java.io.FileReader;
import java.sql.*;
import java.util.Properties;

public class DatabaseManager {
    private String connection_url;
    private String username;
    private String password;
    private String dbName;

    public DatabaseManager() {
        File configFile = new File("config.properties");
        try {
            FileReader reader = new FileReader(configFile);
            Properties props = new Properties();
            props.load(reader);
            username = props.getProperty("dbUsername");
            password = props.getProperty("dbPassword");
            dbName = props.getProperty("dbName");
            connection_url = "jdbc:mysql://localhost:3306/" + dbName + "?useUnicode=true&characterEncoding=UTF-8&useSSL=false";
            reader.close();
        } catch (Exception ex) {
            System.out.println(ex.getClass());
        }
    }

    public Connection getDBConnection() {
        try {
            return DriverManager.getConnection(connection_url, username, password);
        } catch (Exception e) {
            throw new RuntimeException("Cannot connect to database", e);
        }
    }

    Connection getMySQLDBConnection() {
        try {
            String mysql_db_connection_url = "jdbc:mysql://localhost:3306/mysql?useSSL=false";
            return DriverManager.getConnection(mysql_db_connection_url, username, password);
        } catch (Exception e) {
            throw new RuntimeException("Cannot connect to MySQL database", e);
        }
    }

    public Timestamp getTableUpdateTime(String tableName) {
        try {
            Connection connection = getMySQLDBConnection();

            String query = "SELECT UPDATE_TIME FROM information_schema.tables \n" +
                    "WHERE  TABLE_SCHEMA = ? AND TABLE_NAME = ? ";

            PreparedStatement statement = connection.prepareStatement(query);

            statement.setString(1, dbName);
            statement.setString(2, tableName);

            ResultSet result = statement.executeQuery();

            if (result.next()) {
                return result.getTimestamp(1);
            }

            connection.close();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return null;
    }
}
