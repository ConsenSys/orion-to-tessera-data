package net.consensys.tessera.migration.data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class JdbcRecordCounter implements RecordCounter {

    private final MigrateDataCommand.InboundJdbcArgs jdbcConfig;

    public JdbcRecordCounter(MigrateDataCommand.InboundJdbcArgs jdbcConfig) {
        this.jdbcConfig = jdbcConfig;
    }

    @Override
    public long count() throws Exception {

        Connection connection = DriverManager.getConnection(jdbcConfig.getUrl(),jdbcConfig.getUsername(),jdbcConfig.getPassword());
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM STORE");

        try(connection;statement;resultSet) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}
