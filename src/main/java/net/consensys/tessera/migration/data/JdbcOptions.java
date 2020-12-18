package net.consensys.tessera.migration.data;

import picocli.CommandLine;

public class JdbcOptions {

    @CommandLine.Option(names = {"jdbc.user"},required = true)
    private String username;

    @CommandLine.Option(names = {"jdbc.password"},required = true)
    private String password;

    @CommandLine.Option(names = "jdbc.url",required = true)
    private String url;

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getUrl() {
        return url;
    }
}
