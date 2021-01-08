package net.consensys.tessera.migration.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lmax.disruptor.dsl.Disruptor;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Objects;

public class JdbcOrionDataAdapter implements OrionDataAdapter {

    private final MigrateDataCommand.InboundJdbcArgs jdbcConfig;

    private final ObjectMapper cborObjectMapper;

    private final OrionKeyHelper orionKeyHelper;

    private Disruptor<OrionRecordEvent> disruptor;

    public JdbcOrionDataAdapter(MigrateDataCommand.InboundJdbcArgs jdbcConfig,
                                ObjectMapper cborObjectMapper,
                                OrionKeyHelper orionKeyHelper) {

        this.jdbcConfig = Objects.requireNonNull(jdbcConfig);
        this.cborObjectMapper = Objects.requireNonNull(cborObjectMapper);
        this.orionKeyHelper = Objects.requireNonNull(orionKeyHelper);
    }

    @Override
    public void start() throws Exception {

        Connection connection = DriverManager.getConnection(jdbcConfig.getUrl(),jdbcConfig.getUsername(), jdbcConfig.getPassword());
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("");
        try(connection;statement) {

        }
    }

}
