package database_manager;

import com.ibatis.common.jdbc.ScriptRunner;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.sql.Connection;

public class CreateDatabase {
    static final String createScriptPath = "create_database.sql";

    public static void main(String[] args) {
        DatabaseManager dbManager = new DatabaseManager();
        Connection con = dbManager.getMySQLDBConnection();

        try {
            ScriptRunner scriptRunner = new ScriptRunner(con, false, false);
            Reader reader = new BufferedReader(new FileReader(createScriptPath));
            scriptRunner.runScript(reader);
            Runtime.getRuntime().exec(new String[] { "sh", "-c", "rm -rf ./html_docs/*.html && rm -rf ./src/crawler/Saved_State/*.txt && rm -rf ./txt_docs/*.txt" });

        } catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }


    }
}
