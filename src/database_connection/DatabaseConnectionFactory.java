package database_connection;

import com.mysql.jdbc.Driver;
import java.io.*;
import java.util.Properties;
import java.sql.*;

public class DatabaseConnectionFactory {
    private String connection_url;
    private String username;
    private String password;

    public DatabaseConnectionFactory() {
        File configFile = new File("config.properties");
        try {
            FileReader reader = new FileReader(configFile);
            Properties props = new Properties();
            props.load(reader);
            username = props.getProperty("dbUsername");
            password = props.getProperty("dbPassword");
            String dbName = props.getProperty("dbName");
            connection_url = "jdbc:mysql://localhost:3306/" + dbName;
            reader.close();
        } catch (Exception ex) {
            System.out.println(ex.getClass());
        }
    }

    public Connection getDBConnection() {
        try {
            DriverManager.registerDriver(new Driver());
            return DriverManager.getConnection(connection_url, username, password);
        } catch (Exception e) {
            throw new RuntimeException("Cannot connect to database", e);
        }
    }
}
