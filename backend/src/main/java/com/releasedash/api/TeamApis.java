package com.releasedash.api;

/**
 * Team HTTP APIs hosted by this application.
 * Each team is mounted at {@code /{team}/api}. Add a constant and a
 * {@code @RestController} mapped to it to expose another team.
 */
public final class TeamApis {

    public static final String MENTAT = "/mentat/api";
    public static final String FOOBAR = "/foobar/api";

    private TeamApis() {}
}
