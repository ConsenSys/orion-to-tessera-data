package net.consensys.tessera.migration.data;

import picocli.CommandLine;

public class TesseraJdbcOptions {

    @CommandLine.Option(names = {"tessera.jdbc.user"},required = true)
    private String username;

    @CommandLine.Option(names = {"tessera.jdbc.password"},required = true)
    private String password;

    @CommandLine.Option(names = "tessera.jdbc.url",required = true)
    private String url;

    @CommandLine.Option(names = "tessera.db.action")
    private String action = "create";

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getUrl() {
        return url;
    }

    public String getAction() {
        return action;
    }
}
